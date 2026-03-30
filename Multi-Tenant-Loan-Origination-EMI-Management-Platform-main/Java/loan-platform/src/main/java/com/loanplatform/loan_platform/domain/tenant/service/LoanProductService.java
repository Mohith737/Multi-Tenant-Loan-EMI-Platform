package com.loanplatform.loan_platform.domain.tenant.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductResponse;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import com.loanplatform.loan_platform.dto.response.LoanProductResponse;
import com.loanplatform.loan_platform.exception.MambuIntegrationException;
import com.loanplatform.loan_platform.exception.TenantNotFoundException;
import com.loanplatform.loan_platform.mapper.TenantMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.inbound.LoanProductUseCase;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanProductService implements LoanProductUseCase {

    private final TenantRepository tenantRepository;
    private final LoanProductRepository loanProductRepository;
    private final MambuPort mambuPort;
    private final TenantMapper tenantMapper;

    @Value("${mambu.base-url}")
    private String mambuBaseUrl;

    @Override
    @TenantAware
    @Transactional
    public LoanProductResponse createLoanProduct(UUID tenantId, LoanProductConfigRequest request) {
        validateLoanConfigRange(request);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getMambuBranchEncodedKey() == null || tenant.getMambuBranchEncodedKey().isBlank()) {
            throw new IllegalArgumentException("Tenant does not have mambu branch mapping");
        }

        MambuLoanProductResponse productResponse = createMambuLoanProduct(tenantId, tenant.getMambuBranchEncodedKey(), request);

        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId(productResponse.getId())
                .mambuProductKey(productResponse.getEncodedKey())
                .loanProductConfig(LoanProductConfigSnapshot.fromRequest(request))
                .build();

        LoanProduct saved = loanProductRepository.save(loanProduct);
        return tenantMapper.toLoanProductResponse(saved);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true)
    public List<LoanProductResponse> listLoanProducts(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException("Tenant not found: " + tenantId);
        }
        return loanProductRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(tenantMapper::toLoanProductResponse)
                .toList();
    }

    private void validateLoanConfigRange(LoanProductConfigRequest config) {
        if (config.getMinLoanAmount().compareTo(config.getMaxLoanAmount()) > 0) {
            throw new IllegalArgumentException("minLoanAmount cannot be greater than maxLoanAmount");
        }
        if (config.getMinTenureMonths() > config.getMaxTenureMonths()) {
            throw new IllegalArgumentException("minTenureMonths cannot be greater than maxTenureMonths");
        }
    }

    private MambuLoanProductResponse createMambuLoanProduct(
            UUID tenantId,
            String branchEncodedKey,
            LoanProductConfigRequest request
    ) {
        try {
            return mambuPort.createLoanProduct(branchEncodedKey, request);
        } catch (Exception ex) {
            log.error("Loan product provisioning failed for tenantId={}", tenantId, ex);
            throw new MambuIntegrationException(buildMambuLoanProductFailureMessage(tenantId, ex), ex);
        }
    }

    private String buildMambuLoanProductFailureMessage(UUID tenantId, Exception ex) {
        String base = "Tenant loan product provisioning failed in Mambu. tenantId=" + tenantId
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
