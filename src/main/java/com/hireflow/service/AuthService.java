package com.hireflow.service;

import com.hireflow.domain.User;
import com.hireflow.exception.UnauthorizedException;
import com.hireflow.repository.UserRepository;
import com.hireflow.security.CustomUserDetails;
import com.hireflow.security.JwtUtil;
import com.hireflow.web.controller.AuthController.TokenResponse;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public TokenResponse login(String email, String password) {
        try {
            var authToken = new UsernamePasswordAuthenticationToken(email, password);
            var auth = authenticationManager.authenticate(authToken);
            CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();

            userRepository.findById(principal.getUserId())
                    .ifPresent(u -> u.setLastLoginAt(Instant.now()));

            return issueTokens(principal);
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid email or password");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims = jwtUtil.parse(refreshToken);
        if (!jwtUtil.isRefreshToken(claims)) {
            throw new UnauthorizedException("Not a refresh token");
        }
        UUID userId = jwtUtil.extractUserId(claims);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        if (!user.isEnabled() || jwtUtil.extractTokenVersion(claims) != user.getTokenVersion()) {
            throw new UnauthorizedException("Refresh token revoked");
        }
        return issueTokens(new CustomUserDetails(user));
    }

    @Transactional
    public void logout() {
        UUID userId = SecurityUtils.currentUserId();
        userRepository.findById(userId).ifPresent(u -> u.setTokenVersion(u.getTokenVersion() + 1));
        SecurityContextHolder.clearContext();
    }

    private TokenResponse issueTokens(CustomUserDetails principal) {
        String access = jwtUtil.generateAccessToken(principal);
        String refresh = jwtUtil.generateRefreshToken(principal);
        return new TokenResponse(access, refresh, "Bearer", jwtUtil.getAccessTtlSeconds());
    }
}
