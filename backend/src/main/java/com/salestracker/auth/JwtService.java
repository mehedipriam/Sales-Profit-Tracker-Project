package com.salestracker.auth;

import com.salestracker.user.Role;
import com.salestracker.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration ttl;
    private final Duration rememberTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes,
                      @Value("${app.jwt.remember-me-days}") long rememberMeDays) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(expirationMinutes);
        this.rememberTtl = Duration.ofDays(rememberMeDays);
    }

    /** A remembered token lives for days instead of hours, and says so, so a token reissued from it stays long. */
    public String generate(User user, boolean remembered) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("tenantId", user.getTenantId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("rem", remembered)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(remembered ? rememberTtl : ttl)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(
                    Long.valueOf(c.getSubject()),
                    c.get("tenantId", Long.class),
                    c.get("email", String.class),
                    Role.valueOf(c.get("role", String.class)),
                    Boolean.TRUE.equals(c.get("rem", Boolean.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
