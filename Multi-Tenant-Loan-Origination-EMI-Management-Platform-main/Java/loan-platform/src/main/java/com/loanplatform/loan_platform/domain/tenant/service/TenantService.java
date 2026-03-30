package com.loanplatform.loan_platform.domain.tenant.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchResponse;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.dto.request.ActivateTenantRequest;
import com.loanplatform.loan_platform.dto.request.SuspendTenantRequest;
import com.loanplatform.loan_platform.dto.request.TenantRegistrationRequest;
import com.loanplatform.loan_platform.dto.response.PublicTenantBrowseResponse;
import com.loanplatform.loan_platform.dto.response.PublicTenantProductResponse;
import com.loanplatform.loan_platform.dto.response.TenantActivationResponse;
import com.loanplatform.loan_platform.dto.response.TenantRegistrationResponse;
import com.loanplatform.loan_platform.dto.response.TenantResponse;
import com.loanplatform.loan_platform.dto.response.TenantSuspensionResponse;
import com.loanplatform.loan_platform.exception.MambuIntegrationException;
import com.loanplatform.loan_platform.exception.TenantConflictException;
import com.loanplatform.loan_platform.exception.TenantNotFoundException;
import com.loanplatform.loan_platform.mapper.TenantMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.inbound.TenantUseCase;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService implements TenantUseCase {

    private final TenantRepository tenantRepository;
    private final LoanProductRepository loanProductRepository;
    private final MambuPort mambuPort;
    private final TenantMapper tenantMapper;

    @Value("${mambu.base-url}")
    private String mambuBaseUrl;

    @Override
    @TenantAware
    @Transactional(noRollbackFor = MambuIntegrationException.class)
    public TenantRegistrationResponse registerTenant(TenantRegistrationRequest request) {
        if (tenantRepository.existsByDomain(request.getDomain())) {
            throw new TenantConflictException("Tenant domain already exists: " + request.getDomain());
        }

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .domain(request.getDomain())
                .planTier(request.getPlanTier())
                .status(TenantStatus.PENDING)
                .build();

        try {
            tenant = tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException ex) {
            throw new TenantConflictException("Tenant domain already exists: " + request.getDomain());
        }

        try {
            MambuBranchResponse branchResponse = mambuPort.createBranch(
                    tenant.getId().toString(),
                    request.getName(),
                    request.getAdminEmail(),
                    request.getAdminPhone()
            );
            tenant.setMambuBranchId(branchResponse.getId());
            tenant.setMambuBranchEncodedKey(branchResponse.getEncodedKey());
            tenant.setStatus(TenantStatus.ACTIVE);
            Tenant updated = tenantRepository.save(tenant);
            return tenantMapper.toTenantRegistrationResponse(updated);
        } catch (Exception ex) {
            log.error("Tenant onboarding failed for tenantId={} domain={}", tenant.getId(), request.getDomain(), ex);
            throw new MambuIntegrationException(buildMambuFailureMessage(tenant.getId(), ex), ex);
        }
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        return tenantMapper.toTenantResponse(tenant);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        return tenantRepository.findAll().stream()
                .map(tenantMapper::toTenantResponse)
                .toList();
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true)
    public List<PublicTenantBrowseResponse> listActiveTenantsForPublicBrowse() {
        List<Tenant> activeTenants = tenantRepository.findByStatusOrderByCreatedAtDesc(TenantStatus.ACTIVE);
        if (activeTenants.isEmpty()) {
            return List.of();
        }

        List<UUID> tenantIds = activeTenants.stream().map(Tenant::getId).toList();
        Map<UUID, List<PublicTenantProductResponse>> productMap = loanProductRepository.findByTenantIdIn(tenantIds).stream()
                .collect(Collectors.groupingBy(
                        LoanProduct::getTenantId,
                        Collectors.mapping(tenantMapper::toPublicTenantProductResponse, Collectors.toList())
                ));

        return activeTenants.stream()
                .map(tenant -> {
                    PublicTenantBrowseResponse response = tenantMapper.toPublicTenantBrowseResponse(tenant);
                    response.setLoanProducts(productMap.getOrDefault(tenant.getId(), List.of()));
                    return response;
                })
                .toList();
    }

    @Override
    @TenantAware
    @Transactional
    public TenantActivationResponse activateTenant(UUID tenantId, ActivateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        return TenantActivationResponse.builder()
                .tenantId(tenant.getId())
                .status(tenant.getStatus().name())
                .activatedAt(Instant.now())
                .build();
    }

    @Override
    @TenantAware
    @Transactional
    public TenantSuspensionResponse suspendTenant(UUID tenantId, SuspendTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);

        return TenantSuspensionResponse.builder()
                .tenantId(tenant.getId())
                .status(tenant.getStatus().name())
                .reason(request.getReason())
                .suspendedAt(Instant.now())
                .build();
    }

    private String buildMambuFailureMessage(UUID tenantId, Exception ex) {
        String base = "Tenant onboarding failed while provisioning in Mambu. tenantId=" + tenantId
                + ", mambuBaseUrl=" + mambuBaseUrl;

        if (ex instanceof RetryableException retryable) {
            String reason = retryable.getMessage() != null ? retryable.getMessage() : "connection failure";
            return base + ", cause=" + reason
                    + ". Ensure mambu-mock-service is running and reachable.";
        }

        if (ex instanceof FeignException feignException) {
            String responseBody = feignException.contentUTF8();
            if (responseBody == null || responseBody.isBlank()) {
                return base + ", mambuStatus=" + feignException.status()
                        + ", cause=" + feignException.getMessage();
            }
            return base + ", mambuStatus=" + feignException.status()
                    + ", mambuResponse=" + responseBody;
        }

        String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return base + ", cause=" + reason;
    }
}
