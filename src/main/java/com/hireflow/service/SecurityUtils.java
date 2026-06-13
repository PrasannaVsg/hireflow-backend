package com.hireflow.service;

import com.hireflow.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() { }

    public static Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("No authenticated principal");
        }
        return auth;
    }

    /**
     * Returns the current user ID from the security context.
     * When authentication is a CustomUserDetails principal, returns its userId.
     * Falls back to a fixed dev UUID when security is disabled (permitAll mode).
     */
    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.hireflow.security.CustomUserDetails cud) {
            return cud.getUserId();
        }
        // Dev fallback: fixed UUID when auth is not wired
        return UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    }

    public static UUID currentOrgId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.hireflow.security.CustomUserDetails cud) {
            return cud.getOrganisationId();
        }
        // Dev fallback
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
