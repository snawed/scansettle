package com.scansettle.api.security;

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

/**
 * Issues and validates the JWTs used by the Phase 2 authentication foundation.
 *
 * <p>This is deliberately self-issued for MVP: {@code app.security.jwt.secret} is a
 * local placeholder, not an external identity provider. The validating side
 * ({@link JwtAuthenticationFilter}) only depends on {@link AuthenticatedPrincipal},
 * so swapping to an OIDC/OAuth2 resource-server validating tokens from
 * {@code {{OPEN_BANKING_PROVIDER}}}-independent IdP later is a configuration change,
 * not a rewrite of the authorization code.
 */
@Service
public class JwtService {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_MFA_CHALLENGE = "mfa_challenge";

    private final SecretKey key;
    private final Duration tokenTtl;
    private final Duration mfaChallengeTtl;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.ttl-minutes:60}") long ttlMinutes,
            @Value("${app.security.jwt.mfa-ttl-minutes:5}") long mfaTtlMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes — set APP_JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenTtl = Duration.ofMinutes(ttlMinutes);
        this.mfaChallengeTtl = Duration.ofMinutes(mfaTtlMinutes);
    }

    public String issue(AuthenticatedPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.subject())
                .claim("type", TYPE_ACCESS)
                .claim("role", principal.role().name())
                .claim("merchantId", principal.merchantId())
                .claim("userId", principal.userId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthenticatedPrincipal> validate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!TYPE_ACCESS.equals(claims.get("type", String.class))) {
                return Optional.empty(); // e.g. an MFA-challenge token used as a bearer token
            }
            Role role = Role.valueOf(claims.get("role", String.class));
            String merchantId = claims.get("merchantId", String.class);
            String userId = claims.get("userId", String.class);
            return Optional.of(new AuthenticatedPrincipal(claims.getSubject(), role, merchantId, userId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * A short-lived, deliberately weaker token issued after password check but
     * before MFA verification — proves nothing beyond "this caller knows the
     * password for this merchantUserId" and cannot be used as a bearer token
     * ({@link #validate} rejects it).
     */
    public String issueMfaChallenge(String merchantUserId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(merchantUserId)
                .claim("type", TYPE_MFA_CHALLENGE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(mfaChallengeTtl)))
                .signWith(key)
                .compact();
    }

    public Optional<String> validateMfaChallenge(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!TYPE_MFA_CHALLENGE.equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
