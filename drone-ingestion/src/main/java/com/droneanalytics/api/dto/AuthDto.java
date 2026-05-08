package com.droneanalytics.api.dto;

/**
 * Request/response DTOs for the authentication endpoints.
 */
public class AuthDto {

    // ── Requests ─────────────────────────────────────────────────────────────

    public static class RegisterRequest {
        public String email;
        public String password;
        public String displayName;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    // ── Responses ────────────────────────────────────────────────────────────

    public static class AuthResponse {
        public String token;
        public String email;
        public String displayName;
        public long   expiresInSeconds;

        public AuthResponse(String token, String email, String displayName, long expiresInSeconds) {
            this.token           = token;
            this.email           = email;
            this.displayName     = displayName;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    public static class ErrorResponse {
        public String message;

        public ErrorResponse(String message) { this.message = message; }
    }
}
