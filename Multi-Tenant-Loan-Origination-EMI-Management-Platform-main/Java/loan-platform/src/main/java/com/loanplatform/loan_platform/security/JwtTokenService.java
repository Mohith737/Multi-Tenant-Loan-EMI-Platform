package com.loanplatform.loan_platform.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenService {

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.expiry-seconds}")
    private long expirySeconds;

    public TokenResult generateToken(String username, List<String> roles, UUID tenantId) {
        return generateToken(username, roles, tenantId, Map.of());
    }

    public TokenResult generateToken(String username, List<String> roles, UUID tenantId, Map<String, Object> extraClaims) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirySeconds);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject(username)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("roles", roles);

        if (roles != null && !roles.isEmpty()) {
            claimsBuilder.claim("role", roles.getFirst());
        }

        if (tenantId != null) {
            claimsBuilder.claim("tenantId", tenantId.toString());
        }

        for (Map.Entry<String, Object> entry : extraClaims.entrySet()) {
            claimsBuilder.claim(entry.getKey(), entry.getValue());
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .type(JOSEObjectType.JWT)
                        .build(),
                claimsBuilder.build()
        );

        try {
            signedJWT.sign(new MACSigner(jwtSecret.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to generate JWT token", ex);
        }

        return new TokenResult(signedJWT.serialize(), expiresAt, expirySeconds);
    }

    public record TokenResult(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
