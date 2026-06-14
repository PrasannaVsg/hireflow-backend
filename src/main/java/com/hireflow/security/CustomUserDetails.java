package com.hireflow.security;

import com.hireflow.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final UUID organisationId;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;
    private final int tokenVersion;
    private final boolean mustChangePassword;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.organisationId = user.getOrganisation().getId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole().name();
        this.enabled = user.isEnabled();
        this.tokenVersion = user.getTokenVersion();
        this.mustChangePassword = user.isMustChangePassword();
    }

    public UUID getUserId() { return userId; }
    public UUID getOrganisationId() { return organisationId; }
    public String getFullName() { return fullName; }
    public int getTokenVersion() { return tokenVersion; }
    public String getRole() { return role; }
    public boolean isMustChangePassword() { return mustChangePassword; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
