package com.droneanalytics.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController — register, login, protected endpoints.
 *
 * Uses a unique email per test run (System.currentTimeMillis) so tests are
 * idempotent against the shared in-memory H2 DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    // Shared across ordered tests in this class
    private static String testEmail;
    private static String jwtToken;

    @BeforeAll
    static void init() {
        testEmail = "testuser_" + System.currentTimeMillis() + "@drone.test";
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Test @Order(1)
    void registerCreatesAccountAndReturnsJwt() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "email",       testEmail,
            "password",    "password123",
            "displayName", "Test Pilot"
        ));

        MvcResult result = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.email").value(testEmail))
            .andExpect(jsonPath("$.displayName").value("Test Pilot"))
            .andReturn();

        jwtToken = mapper.readTree(result.getResponse().getContentAsString())
            .get("token").asText();
        assertThat(jwtToken).isNotBlank();
    }

    @Test @Order(2)
    void registerWithDuplicateEmailReturns409() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "email", testEmail, "password", "other123", "displayName", "Dup"));

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test @Order(3)
    void registerWithShortPasswordReturns400() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "email", "short@drone.test", "password", "abc", "displayName", "X"));

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test @Order(4)
    void loginWithCorrectCredentialsReturnsJwt() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "email", testEmail, "password", "password123"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.email").value(testEmail));
    }

    @Test @Order(5)
    void loginWithWrongPasswordReturns401() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "email", testEmail, "password", "wrongpassword"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test @Order(6)
    void loginWithUnknownEmailReturns401() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "email", "nobody@drone.test", "password", "whatever"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    // ── /api/auth/me ──────────────────────────────────────────────────────────

    @Test @Order(7)
    void meEndpointReturnsProfileWithValidToken() throws Exception {
        assertThat(jwtToken).as("JWT must have been set in test 1").isNotNull();

        mvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(testEmail))
            .andExpect(jsonPath("$.displayName").value("Test Pilot"));
    }

    @Test @Order(8)
    void meEndpointReturns401WithoutToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    // ── JWT on protected flight endpoint ─────────────────────────────────────

    @Test @Order(9)
    void flightListRequiresAuth() throws Exception {
        // No token — should be 401
        mvc.perform(get("/api/flights"))
            .andExpect(status().isUnauthorized());
    }

    @Test @Order(10)
    void flightListWithValidTokenReturnsEmptyList() throws Exception {
        assertThat(jwtToken).as("JWT must have been set in test 1").isNotNull();

        mvc.perform(get("/api/flights")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions").isArray())
            .andExpect(jsonPath("$.totalCount").isNumber());
    }
}
