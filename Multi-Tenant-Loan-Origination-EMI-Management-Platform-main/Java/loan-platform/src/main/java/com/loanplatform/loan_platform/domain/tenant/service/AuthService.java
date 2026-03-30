package com.loanplatform.loan_platform.domain.tenant.service;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.dto.request.AuthLoginRequest;
import com.loanplatform.loan_platform.dto.response.AuthLoginResponse;
import com.loanplatform.loan_platform.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final BorrowerRepository borrowerRepository;

    public AuthLoginResponse login(AuthLoginRequest request) {
        return authenticateAndIssueToken(request, false);
    }

    public AuthLoginResponse adminLogin(AuthLoginRequest request) {
        return authenticateAndIssueToken(request, true);
    }

    private AuthLoginResponse authenticateAndIssueToken(AuthLoginRequest request, boolean adminOnly) {
        Optional<UserDetails> internalUser = loadUserOptional(request.getUsername());
        if (internalUser.isPresent()) {
            return authenticateInternalUser(request, adminOnly, internalUser.get());
        }

        if (adminOnly) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return authenticateBorrower(request);
    }

    private AuthLoginResponse authenticateInternalUser(AuthLoginRequest request, boolean adminOnly, UserDetails user) {
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::normalizeRole)
                .toList();

        if (roles.isEmpty()) {
            throw new AccessDeniedException("User has no roles configured");
        }
        if (adminOnly && !roles.contains(PLATFORM_ADMIN)) {
            throw new AccessDeniedException("Only PLATFORM_ADMIN can use admin login");
        }

        UUID tenantId = resolveTenantIdForToken(roles, request.getTenantId());
        var token = jwtTokenService.generateToken(user.getUsername(), roles, tenantId);

        return AuthLoginResponse.builder()
                .tokenType("Bearer")
                .accessToken(token.token())
                .expiresInSeconds(token.expiresInSeconds())
                .expiresAt(token.expiresAt())
                .username(user.getUsername())
                .roles(roles)
                .tenantId(tenantId)
                .build();
    }

    private AuthLoginResponse authenticateBorrower(AuthLoginRequest request) {
        Borrower borrower = resolveBorrower(request);

        if (!passwordEncoder.matches(request.getPassword(), borrower.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return issueBorrowerToken(borrower);
    }

    private Borrower resolveBorrower(AuthLoginRequest request) {
        if (request.getTenantId() != null) {
            return borrowerRepository.findByTenantIdAndEmailIgnoreCase(request.getTenantId(), request.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        }

        List<Borrower> candidates = borrowerRepository.findAllByEmailIgnoreCase(request.getUsername());
        if (candidates.isEmpty()) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // If an email exists under multiple tenants, use password to resolve the exact borrower account.
        List<Borrower> matchingPassword = candidates.stream()
                .filter(borrower -> passwordEncoder.matches(request.getPassword(), borrower.getPasswordHash()))
                .toList();

        if (matchingPassword.size() != 1) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return matchingPassword.getFirst();
    }

    private AuthLoginResponse issueBorrowerToken(Borrower borrower) {
        List<String> roles = List.of("BORROWER");
        var token = jwtTokenService.generateToken(
                borrower.getEmail(),
                roles,
                borrower.getTenantId(),
                Map.of("borrowerId", borrower.getId().toString())
        );

        return AuthLoginResponse.builder()
                .tokenType("Bearer")
                .accessToken(token.token())
                .expiresInSeconds(token.expiresInSeconds())
                .expiresAt(token.expiresAt())
                .username(borrower.getEmail())
                .roles(roles)
                .tenantId(borrower.getTenantId())
                .build();
    }

    private Optional<UserDetails> loadUserOptional(String username) {
        try {
            return Optional.of(userDetailsService.loadUserByUsername(username));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String normalizeRole(String authority) {
        if (authority == null) {
            return "";
        }
        if (authority.startsWith("ROLE_")) {
            return authority.substring("ROLE_".length()).toUpperCase(Locale.ROOT);
        }
        return authority.toUpperCase(Locale.ROOT);
    }

    private UUID resolveTenantIdForToken(List<String> roles, UUID requestedTenantId) {
        if (roles.contains(PLATFORM_ADMIN)) {
            return requestedTenantId;
        }
        if (requestedTenantId == null) {
            throw new IllegalArgumentException("tenantId is required for tenant-scoped login");
        }
        return requestedTenantId;
    }
}
