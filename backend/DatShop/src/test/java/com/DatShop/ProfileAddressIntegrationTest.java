package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.account.AddressUpdateRequest;
import com.DatShop.dto.account.ChangePasswordRequest;
import com.DatShop.dto.account.ProfileUpdateRequest;
import com.DatShop.model.User;
import com.DatShop.repository.AddressRepository;
import com.DatShop.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileAddressIntegrationTest {

    private static final String PASSWORD = "S3cure-Passw0rd";
    private static final String NEW_PASSWORD = "N3w-S3cure-Pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanup() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        for (String username : createdUsernames) {
            template.executeWithoutResult(status -> userRepository.findWithRolesByUsername(username).ifPresent(user -> {
                addressRepository.findByUser_Id(user.getId()).forEach(addressRepository::delete);
                user.getRoles().clear();
                userRepository.save(user);
                userRepository.delete(user);
            }));
        }
    }

    @Test
    void profileRequiresAuthAndHidesPassword() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());

        String username = uniqueUsername();
        register(username);
        String token = login(username, PASSWORD);

        mockMvc.perform(get("/api/profile").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    void updateProfileAllowsNameAndPhoneAndRejectsUnknownFields() throws Exception {
        String username = uniqueUsername();
        register(username);
        String token = login(username, PASSWORD);

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProfileUpdateRequest("New Name", "0901111222"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.phoneNumber").value("0901111222"))
                .andExpect(jsonPath("$.username").value(username));

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hacked\",\"phoneNumber\":\"0901111222\",\"username\":\"other\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: username"));
        mockMvc.perform(put("/api/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hacked\",\"phoneNumber\":\"0901111222\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: roles"));
        mockMvc.perform(put("/api/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hacked\",\"phoneNumber\":\"0901111222\",\"status\":\"BLOCKED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field: status"));

        mockMvc.perform(get("/api/profile").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void changePasswordUsesBcryptAndKeepsTokenUntilExpiry() throws Exception {
        String username = uniqueUsername();
        register(username);
        String token = login(username, PASSWORD);

        mockMvc.perform(put("/api/profile/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("wrong-password", NEW_PASSWORD, NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        mockMvc.perform(put("/api/profile/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(PASSWORD, NEW_PASSWORD, "mismatch-pass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("confirmation")));
        mockMvc.perform(put("/api/profile/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(PASSWORD, PASSWORD, PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("different")));

        mockMvc.perform(put("/api/profile/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(PASSWORD, NEW_PASSWORD, NEW_PASSWORD))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(not(containsString(NEW_PASSWORD))))
                .andExpect(content().string(not(containsString("passwordHash"))));

        User stored = userRepository.findByUsername(username).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(NEW_PASSWORD);
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isFalse();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, NEW_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/profile").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void addressesAreOwnerScopedAndKeepOneDefault() throws Exception {
        mockMvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());

        String owner = uniqueUsername();
        String other = uniqueUsername();
        register(owner);
        register(other);
        String ownerToken = login(owner, PASSWORD);
        String otherToken = login(other, PASSWORD);

        MvcResult first = postAddress(ownerToken, address("Home", "0901000001", false));
        long firstId = idOf(first);
        mockMvc.perform(get("/api/addresses").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[0].isDefault").value(true));

        MvcResult second = postAddress(ownerToken, address("Office", "0901000002", false));
        long secondId = idOf(second);
        assertSingleDefault(ownerToken, firstId);

        MvcResult third = postAddress(ownerToken, address("Parents", "0901000003", true));
        long thirdId = idOf(third);
        assertSingleDefault(ownerToken, thirdId);

        mockMvc.perform(patch("/api/addresses/" + secondId + "/default")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secondId))
                .andExpect(jsonPath("$.isDefault").value(true));
        assertSingleDefault(ownerToken, secondId);

        mockMvc.perform(put("/api/addresses/" + secondId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddressUpdateRequest("Office", "0901000002", "Hanoi", "Ba Dinh",
                                        "Cong Vi", "9 Test", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false));
        assertSingleDefault(ownerToken, firstId);

        mockMvc.perform(patch("/api/addresses/" + secondId + "/default")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        assertSingleDefault(ownerToken, secondId);

        mockMvc.perform(put("/api/addresses/" + secondId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddressUpdateRequest("Office 2", "0901000002", "Hanoi", "Ba Dinh",
                                        "Cong Vi", "9 Updated", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Office 2"))
                .andExpect(jsonPath("$.addressLine").value("9 Updated"));
        assertSingleDefault(ownerToken, secondId);

        mockMvc.perform(put("/api/addresses/" + firstId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddressUpdateRequest("Stolen", "0901999999", "Hanoi", "Dong Da",
                                        "Lang", "Hack", true))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/addresses/" + firstId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/addresses/" + firstId + "/default")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/addresses").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) firstId))));

        mockMvc.perform(delete("/api/addresses/" + firstId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        assertSingleDefault(ownerToken, secondId);

        mockMvc.perform(delete("/api/addresses/" + secondId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/addresses").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(thirdId))
                .andExpect(jsonPath("$[0].isDefault").value(true));

        mockMvc.perform(put("/api/addresses/" + thirdId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddressUpdateRequest("Parents", "0901000003", "Hanoi", "Cau Giay",
                                        "Dich Vong", "Last", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc.perform(delete("/api/addresses/" + thirdId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/addresses").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void assertSingleDefault(String token, long expectedDefaultId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/addresses").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        int defaults = 0;
        for (JsonNode node : list) {
            if (node.get("isDefault").asBoolean()) {
                defaults++;
                assertThat(node.get("id").asLong()).isEqualTo(expectedDefaultId);
            }
        }
        assertThat(defaults).isEqualTo(1);
    }

    private MvcResult postAddress(String token, AddressCreateRequest request) throws Exception {
        return mockMvc.perform(post("/api/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private AddressCreateRequest address(String name, String phone, boolean isDefault) {
        return new AddressCreateRequest(name, phone, "Ho Chi Minh", "District 1", "Ben Nghe", "123 Test", isDefault);
    }

    private void register(String username) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("email", username + "@example.com");
        body.put("password", PASSWORD);
        body.put("fullName", "Test Customer");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String uniqueUsername() {
        String username = "prf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createdUsernames.add(username);
        return username;
    }

    private long idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
