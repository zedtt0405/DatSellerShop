package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String RAW_PASSWORD = "S3cure-Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> createdUsernames = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        for (String username : createdUsernames) {
            template.executeWithoutResult(status -> userRepository.findWithRolesByUsername(username).ifPresent(user -> {
                user.getRoles().clear();
                userRepository.save(user);
                userRepository.delete(user);
            }));
        }
    }

    @Test
    void registerSuccessStoresBcryptCustomerAndDoesNotExposePassword() throws Exception {
        String username = uniqueUsername();
        String email = username + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email, RAW_PASSWORD, "Test Customer", "0901234567")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Test Customer"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(RAW_PASSWORD))))
                .andExpect(content().string(not(containsString("passwordHash"))));

        User stored = userRepository.findWithRolesByUsername(username).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(stored.getRoles()).extracting(role -> role.getName()).containsExactly(RoleName.CUSTOMER);

        assertThat(userDetailsService.loadUserByUsername(username).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void registerRejectsUnknownRoleFieldAndDoesNotCreateUser() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@example.com", RAW_PASSWORD, "Test Customer", null)
                                .replaceFirst("\\}$", ",\"role\":\"ADMIN\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unknown field: role"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("passwordHash"))));

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void duplicateUsernameReturnsConflict() throws Exception {
        String username = uniqueUsername();
        String firstEmail = username + "@example.com";
        String secondEmail = username + ".other@example.com";
        register(username, firstEmail);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, secondEmail, RAW_PASSWORD, "Other Name", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String firstUsername = uniqueUsername();
        String secondUsername = uniqueUsername();
        String email = firstUsername + "@example.com";
        register(firstUsername, email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(secondUsername, email, RAW_PASSWORD, "Other Name", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    void loginSuccessReturnsJwt() throws Exception {
        String username = uniqueUsername();
        register(username, username + "@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(RAW_PASSWORD))));
    }

    @Test
    void loginWrongUsernameReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("missing_" + UUID.randomUUID().toString().substring(0, 8), RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginWrongPasswordReturnsUnauthorized() throws Exception {
        String username = uniqueUsername();
        register(username, username + "@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void currentUserRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void currentUserReturnsProfileForValidJwt() throws Exception {
        String username = uniqueUsername();
        register(username, username + "@example.com");
        String token = login(username);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void jwtWithInvalidSignatureIsRejected() throws Exception {
        String username = uniqueUsername();
        register(username, username + "@example.com");

        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        SecretKey key = Keys.hmacShaKeyFor(otherKey);
        String forged = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockedUserCannotLoginOrReuseToken() throws Exception {
        String username = uniqueUsername();
        register(username, username + "@example.com");
        String token = login(username);

        setStatus(username, UserStatus.BLOCKED);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveUserLoginReturnsGenericUnauthorized() throws Exception {
        String username = uniqueUsername();
        register(username, username + "@example.com");
        setStatus(username, UserStatus.INACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email, RAW_PASSWORD, "Test Customer", null)))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private String uniqueUsername() {
        String username = "auth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createdUsernames.add(username);
        return username;
    }

    private void setStatus(String username, UserStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            User user = userRepository.findWithRolesByUsername(username).orElseThrow();
            user.setStatus(status);
            userRepository.save(user);
        });
    }

    private String registerJson(
            String username,
            String email,
            String password,
            String fullName,
            String phoneNumber) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        body.put("fullName", fullName);
        if (phoneNumber != null) {
            body.put("phoneNumber", phoneNumber);
        }
        return objectMapper.writeValueAsString(body);
    }

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }
}
