package com.example.academicprofile.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(CurrentUser currentUser) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getJwtExpirationMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(currentUser.userId()))
                .claim("teacherId", currentUser.teacherId())
                .claim("username", currentUser.username())
                .claim("email", currentUser.email())
                .claim("role", currentUser.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
    }

    public CurrentUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            Long teacherId = claims.get("teacherId", Number.class).longValue();
            String username = claims.get("username", String.class);
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            return new CurrentUser(userId, teacherId, username, email, role);
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private SecretKey signingKey() {
        String secret = jwtProperties.getJwtSecret();
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "JWT secret must be configured with at least 32 characters");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
