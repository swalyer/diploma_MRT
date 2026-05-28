package com.diploma.mrt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.WeakKeyException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.diploma.mrt.entity.Role;
import com.diploma.mrt.util.EmailNormalizer;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private static final String INSECURE_DEV_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String INSECURE_COMPOSE_SECRET = "replace-with-long-random-secret-at-least-32-bytes";
    private static final String INSECURE_ENV_EXAMPLE_SECRET = "generate-a-real-random-secret-before-deploy";

    @Value("${app.jwt-secret}")
    private String secret;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("APP_JWT_SECRET must be configured");
        }
        String normalized = secret.trim().toLowerCase();
        if (INSECURE_DEV_SECRET.equals(secret)
                || INSECURE_COMPOSE_SECRET.equals(secret)
                || INSECURE_ENV_EXAMPLE_SECRET.equals(secret)
                || normalized.contains("change-me")
                || normalized.contains("replace-with")
                || normalized.contains("generate-a-real-random-secret")) {
            throw new IllegalStateException("APP_JWT_SECRET must not use insecure default values");
        }
        signingKey();
    }

    public String generateToken(String subject, Role role) {
        SecretKey key = signingKey();
        Date now = new Date();
        Date exp = new Date(now.getTime() + 86400000L);
        return Jwts.builder()
                .subject(EmailNormalizer.normalize(subject))
                .claims(Map.of("role", role.name()))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    private SecretKey signingKey() {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        try {
            return Keys.hmacShaKeyFor(raw);
        } catch (WeakKeyException weakKeyException) {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        }
    }
}
