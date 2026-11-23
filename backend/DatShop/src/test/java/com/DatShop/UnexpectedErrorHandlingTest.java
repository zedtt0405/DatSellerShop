package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.DatShop.exception.ApiError;
import com.DatShop.exception.GlobalExceptionHandler;
import com.DatShop.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UnexpectedErrorHandlingTest.ProbeController.class)
class UnexpectedErrorHandlingTest {

    private static final String RAW_PASSWORD = "S3cure-Passw0rd";
    private static final String LEAK = "internal-secret-should-not-leak";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    private final List<String> createdUsernames = new ArrayList<>();

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
    void unexpectedExceptionReturnsGenericApiErrorWithoutInternalDetail() throws Exception {
        String token = login(registerCustomer());

        mockMvc.perform(get("/__test__/unexpected-error").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/__test__/unexpected-error"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString(LEAK))))
                .andExpect(content().string(not(containsString("IllegalArgumentException"))))
                .andExpect(content().string(not(containsString("at com.DatShop"))));
    }

    @Test
    void handleUnexpectedDoesNotCopyExceptionMessageIntoApiError() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/__test__/unexpected-error");

        ResponseEntity<ApiError> response = exceptionHandler.handleUnexpected(
                new IllegalArgumentException(LEAK), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().path()).isEqualTo("/__test__/unexpected-error");
        assertThat(response.getBody().message()).doesNotContain(LEAK);
    }

    @Test
    void existingClientErrorStatusesStayUnchanged() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("ab", "not-an-email", "short", "", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String customerToken = login(registerCustomer());
        mockMvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));

        mockMvc.perform(get("/api/products/no-such-product-d1-" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        String username = uniqueUsername();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@example.com", RAW_PASSWORD, "Test Customer", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + ".other@example.com", RAW_PASSWORD, "Other", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    private String registerCustomer() throws Exception {
        String username = uniqueUsername();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@example.com", RAW_PASSWORD, "Test Customer", null)))
                .andExpect(status().isCreated());
        return username;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private String uniqueUsername() {
        String username = "err_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createdUsernames.add(username);
        return username;
    }

    private String registerJson(
            String username,
            String email,
            String password,
            String fullName,
            String phoneNumber) {
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

    @RestController
    static class ProbeController {

        @GetMapping("/__test__/unexpected-error")
        public void fail() {
            throw new IllegalArgumentException(LEAK);
        }
    }
}
