package com.droneanalytics.auth;

import com.droneanalytics.api.dto.AuthDto;
import com.droneanalytics.entity.User;
import com.droneanalytics.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Authentication endpoints (FR-1).
 *
 * POST /api/auth/register  — create account, receive JWT
 * POST /api/auth/login     — verify credentials, receive JWT
 * GET  /api/auth/me        — return profile of the currently authenticated user
 *
 * Both endpoints are permit-all in SecurityConfig; no token is required.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtils              jwtUtils;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authManager,
                          JwtUtils jwtUtils) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authManager     = authManager;
        this.jwtUtils        = jwtUtils;
    }

    // ── Register ─────────────────────────────────────────────────────────────

    /**
     * Creates a new user account and returns a JWT immediately (no separate login step).
     *
     * Validation:
     *  - email must be non-blank and unique
     *  - password must be at least 6 characters
     *  - displayName must be non-blank
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDto.RegisterRequest req) {

        if (req.email == null || req.email.isBlank())
            return error(HttpStatus.BAD_REQUEST, "Email is required");
        if (req.password == null || req.password.length() < 6)
            return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        if (req.displayName == null || req.displayName.isBlank())
            return error(HttpStatus.BAD_REQUEST, "Display name is required");

        String email = req.email.trim().toLowerCase();

        if (userRepository.existsByEmail(email))
            return error(HttpStatus.CONFLICT, "An account with that email already exists");

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password));
        user.setDisplayName(req.displayName.trim());
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

        String token = jwtUtils.generateToken(email);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new AuthDto.AuthResponse(token, email,
                user.getDisplayName(), jwtUtils.getExpirationSeconds()));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates with email + password and returns a fresh JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDto.LoginRequest req) {

        if (req.email == null || req.password == null)
            return error(HttpStatus.BAD_REQUEST, "Email and password are required");

        String email = req.email.trim().toLowerCase();

        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.password));
        } catch (BadCredentialsException | DisabledException e) {
            return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User disappeared after auth"));

        String token = jwtUtils.generateToken(email);
        return ResponseEntity.ok(
            new AuthDto.AuthResponse(token, email,
                user.getDisplayName(), jwtUtils.getExpirationSeconds()));
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    /**
     * Returns basic profile info for the authenticated user.
     * The JWT filter must have already set the SecurityContext.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {

        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return ResponseEntity.ok(new AuthDto.AuthResponse(
            "", user.getEmail(), user.getDisplayName(), 0));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<AuthDto.ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new AuthDto.ErrorResponse(message));
    }
}
