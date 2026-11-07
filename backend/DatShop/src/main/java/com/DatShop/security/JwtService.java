package com.DatShop.security;

import com.DatShop.config.JwtProperties;
import com.DatShop.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set JWT_SECRET or app.jwt.secret in application-local.properties.");
        }
        if (properties.getExpirationMs() < 1_000L) {
            throw new IllegalStateException("JWT expiration must be at least 1000 milliseconds.");
        }
        this.signingKey = buildSigningKey(properties.getSecret().trim());
        this.expirationMs = properties.getExpirationMs();
    }

    public String generateToken(User user) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expirationMs);
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String subject = extractUsername(token);
        return subject != null && subject.equals(userDetails.getUsername());
    }

    public long getExpiresInSeconds() {
        return expirationMs / 1_000L;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey buildSigningKey(String base64Secret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT secret must be Base64-encoded.", ex);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes (256 bits).");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
