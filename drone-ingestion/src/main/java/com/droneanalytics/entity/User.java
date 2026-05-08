package com.droneanalytics.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * JPA entity representing a registered user.
 *
 * Implements {@link UserDetails} so it can be returned directly by
 * {@link com.droneanalytics.auth.UserDetailsServiceImpl} — no adapter needed.
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login identity — must be unique across the system. */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt-hashed password (never store plaintext). */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /** Display name shown in the dashboard. */
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ── UserDetails ──────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** Returns the BCrypt hash — Spring Security compares this to the submitted password. */
    @Override
    public String getPassword() { return passwordHash; }

    /** Username in Spring Security terms is the email address. */
    @Override
    public String getUsername() { return email; }

    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()              { return true; }

    // ── Getters / setters ────────────────────────────────────────────────────

    public Long    getId()           { return id; }
    public void    setId(Long id)    { this.id = id; }

    public String  getEmail()                     { return email; }
    public void    setEmail(String email)         { this.email = email; }

    public String  getPasswordHash()              { return passwordHash; }
    public void    setPasswordHash(String hash)   { this.passwordHash = hash; }

    public String  getDisplayName()               { return displayName; }
    public void    setDisplayName(String name)    { this.displayName = name; }

    public Instant getCreatedAt()                 { return createdAt; }
    public void    setCreatedAt(Instant t)        { this.createdAt = t; }
}
