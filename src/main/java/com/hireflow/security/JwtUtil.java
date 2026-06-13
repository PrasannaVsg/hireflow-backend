package com.hireflow.security;

import com.hireflow.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    private SecretKey key;

    @Value("${hireflow.jwt.secret}")
    private String secret;

    @Value("${hireflow.jwt.access-ttl-seconds:900}")
    private long accessTtlSeconds;

    @Value("${hireflow.jwt.refresh-ttl-seconds:604800}")
    private long refreshTtlSeconds;

    @Value("${hireflow.jwt.issuer:hireflow}")
    private String issuer;

    @PostConstruct
    void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("hireflow.jwt.secret must be >= 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }

    public String generateAccessToken(CustomUserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getUserId().toString())
                .claims(Map.of(
                        "email", user.getUsername(),
                        "org", user.getOrganisationId().toString(),
                        "role", user.getRole(),
                        "typ", "access"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(CustomUserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getUserId().toString())
                .claims(Map.of(
                        "tv", user.getTokenVersion(),
                        "typ", "refresh"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTtlSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid token: " + e.getMessage());
        }
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("typ", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("typ", String.class));
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public int extractTokenVersion(Claims claims) {
        Integer tv = claims.get("tv", Integer.class);
        return tv == null ? -1 : tv;
    }
}
