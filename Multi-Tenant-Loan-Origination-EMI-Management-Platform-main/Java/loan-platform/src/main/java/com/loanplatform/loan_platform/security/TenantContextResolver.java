package com.loanplatform.loan_platform.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class TenantContextResolver {

    public UUID currentTenantId() {
        JwtAuthenticationToken jwtAuth = currentJwt();

        String tenantId = jwtAuth.getToken().getClaimAsString("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = jwtAuth.getToken().getClaimAsString("tenant_id");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("JWT claim tenantId is required");
        }

        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT claim tenantId must be a valid UUID", ex);
        }
    }

    public UUID currentBorrowerId() {
        JwtAuthenticationToken jwtAuth = currentJwt();
        String borrowerId = jwtAuth.getToken().getClaimAsString("borrowerId");
        if (borrowerId == null || borrowerId.isBlank()) {
            throw new IllegalArgumentException("JWT claim borrowerId is required");
        }
        try {
            return UUID.fromString(borrowerId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT claim borrowerId must be a valid UUID", ex);
        }
    }

    public UUID currentActorId() {
        JwtAuthenticationToken jwtAuth = currentJwt();

        String actorId = jwtAuth.getToken().getClaimAsString("userId");
        if (actorId == null || actorId.isBlank()) {
            actorId = jwtAuth.getToken().getClaimAsString("underwriterId");
        }
        if (actorId == null || actorId.isBlank()) {
            actorId = jwtAuth.getToken().getClaimAsString("borrowerId");
        }
        if (actorId == null || actorId.isBlank()) {
            actorId = jwtAuth.getToken().getSubject();
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Unable to resolve actor id from JWT");
        }

        try {
            return UUID.fromString(actorId);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(actorId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private JwtAuthenticationToken currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new AccessDeniedException("Tenant-scoped operation requires JWT authentication");
        }
        return jwtAuth;
    }
}
