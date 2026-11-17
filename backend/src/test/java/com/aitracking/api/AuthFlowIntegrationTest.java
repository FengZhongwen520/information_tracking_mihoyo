package com.aitracking.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.aitracking.support.PasswordTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "app.hoyoverse.enabled=false",
        "app.backup.enabled=false",
        "app.auth.secret=integration-test-secret-0123456789abcdef",
        "app.auth.rsa-key-path=target/test-auth-rsa.key",
        "app.auth.allowed-origins=https://test.local"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginChangePasswordResetAndDeleteAccount() throws Exception {
        String publicKey = publicKey();
        String username = "flow_user";
        String password = "Flow-Pass-2026!";
        String nextPassword = "Flow-Pass-2026-Next!";
        String resetPassword = "Flow-Pass-2026-Reset!";

        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(username,
                                PasswordTestClient.encrypt(password, publicKey)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.recovery_code").isNotEmpty())
                .andReturn();
        JsonNode registeredBody = body(registered);
        String firstToken = registeredBody.get("token").asText();
        String recoveryCode = registeredBody.get("recovery_code").asText();
        assertNotNull(recoveryCode);
        assertFalse(recoveryCode.isBlank());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        MvcResult changed = mockMvc.perform(put("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordPayload(
                                PasswordTestClient.encrypt(password, publicKey),
                                PasswordTestClient.encrypt(nextPassword, publicKey)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String secondToken = body(changed).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(username,
                                PasswordTestClient.encrypt(password, publicKey)))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(username,
                                PasswordTestClient.encrypt(nextPassword, publicKey)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPayload(username, recoveryCode,
                                PasswordTestClient.encrypt(resetPassword, publicKey)))))
                .andExpect(status().isNoContent());

        MvcResult resetLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(username,
                                PasswordTestClient.encrypt(resetPassword, publicKey)))))
                .andExpect(status().isOk())
                .andReturn();
        String resetToken = body(resetLogin).get("token").asText();

        mockMvc.perform(delete("/api/auth/account")
                        .header(HttpHeaders.AUTHORIZATION, bearer(resetToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePayload(
                                PasswordTestClient.encrypt(resetPassword, publicKey)))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(username,
                                PasswordTestClient.encrypt(resetPassword, publicKey)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndCorsAreConfigured() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("up"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://test.local")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://test.local"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://not-allowed.local")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    private String publicKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/public-key"))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("public_key").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegisterPayload(String username, String passwordCiphertext) {
    }

    private record LoginPayload(String username, String passwordCiphertext) {
    }

    private record ChangePasswordPayload(String currentPasswordCiphertext, String newPasswordCiphertext) {
    }

    private record ResetPayload(String username, String recoveryCode, String newPasswordCiphertext) {
    }

    private record DeletePayload(String passwordCiphertext) {
    }
}
