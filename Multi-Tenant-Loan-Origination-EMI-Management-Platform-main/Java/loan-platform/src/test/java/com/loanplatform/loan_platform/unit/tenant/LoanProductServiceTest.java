package com.loanplatform.loan_platform.unit.tenant;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductResponse;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.domain.tenant.service.LoanProductService;
import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import com.loanplatform.loan_platform.dto.response.LoanProductResponse;
import com.loanplatform.loan_platform.exception.MambuIntegrationException;
import com.loanplatform.loan_platform.exception.TenantNotFoundException;
import com.loanplatform.loan_platform.mapper.TenantMapper;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanProductServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private LoanProductRepository loanProductRepository;

    @Mock
    private MambuPort mambuPort;

    @Mock
    private TenantMapper tenantMapper;

    @InjectMocks
    private LoanProductService loanProductService;

    @Test
    void createLoanProduct_validRequest_createsTenantLoanProductMapping() {
        UUID tenantId = UUID.randomUUID();
        LoanProductConfigRequest loanConfig = buildLoanProductConfig("Acme Personal Loan");
        Tenant tenant = buildTenant(tenantId, "acme.example.com", TenantStatus.ACTIVE);
        tenant.setMambuBranchEncodedKey("branch_key_001");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(mambuPort.createLoanProduct("branch_key_001", loanConfig))
                .thenReturn(MambuLoanProductResponse.builder().id("LP_001").encodedKey("lp_key_001").build());
        when(loanProductRepository.save(any(LoanProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantMapper.toLoanProductResponse(any(LoanProduct.class))).thenAnswer(invocation -> {
            LoanProduct value = invocation.getArgument(0);
            return LoanProductResponse.builder()
                    .loanProductId(value.getId())
                    .tenantId(value.getTenantId())
                    .mambuProductId(value.getMambuProductId())
                    .mambuProductKey(value.getMambuProductKey())
                    .createdAt(value.getCreatedAt())
                    .build();
        });

        LoanProductResponse response = loanProductService.createLoanProduct(tenantId, loanConfig);

        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getMambuProductId()).isEqualTo("LP_001");
        verify(mambuPort).createLoanProduct("branch_key_001", loanConfig);

        ArgumentCaptor<LoanProduct> captor = ArgumentCaptor.forClass(LoanProduct.class);
        verify(loanProductRepository).save(captor.capture());
        assertThat(captor.getValue().getMambuProductKey()).isEqualTo("lp_key_001");
    }

    @Test
    void createLoanProduct_missingTenant_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanProductService.createLoanProduct(tenantId, buildLoanProductConfig("Product")))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    void createLoanProduct_invalidAmountRange_throwsValidationError() {
        UUID tenantId = UUID.randomUUID();
        LoanProductConfigRequest badConfig = buildLoanProductConfig("Bad Product");
        badConfig.setMinLoanAmount(BigDecimal.valueOf(200000));
        badConfig.setMaxLoanAmount(BigDecimal.valueOf(100000));

        assertThatThrownBy(() -> loanProductService.createLoanProduct(tenantId, badConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLoanAmount");
    }

    @Test
    void createLoanProduct_mambuFailure_throwsBadGatewayMappedException() {
        UUID tenantId = UUID.randomUUID();
        LoanProductConfigRequest loanConfig = buildLoanProductConfig("Acme Personal Loan");
        Tenant tenant = buildTenant(tenantId, "acme.example.com", TenantStatus.ACTIVE);
        tenant.setMambuBranchEncodedKey("branch_key_001");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(mambuPort.createLoanProduct(eq("branch_key_001"), eq(loanConfig))).thenThrow(new RuntimeException("mambu down"));

        assertThatThrownBy(() -> loanProductService.createLoanProduct(tenantId, loanConfig))
                .isInstanceOf(MambuIntegrationException.class)
                .hasMessageContaining("Tenant loan product provisioning failed in Mambu");
    }

    @Test
    void listLoanProducts_missingTenant_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> loanProductService.listLoanProducts(tenantId))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    void listLoanProducts_validTenant_returnsMappedProducts() {
        UUID tenantId = UUID.randomUUID();
        LoanProduct first = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_001")
                .mambuProductKey("lp_key_001")
                .createdAt(Instant.now())
                .build();
        LoanProduct second = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_002")
                .mambuProductKey("lp_key_002")
                .createdAt(Instant.now())
                .build();

        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(loanProductRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(first, second));
        when(tenantMapper.toLoanProductResponse(first)).thenReturn(
                LoanProductResponse.builder().tenantId(tenantId).mambuProductId("LP_001").build()
        );
        when(tenantMapper.toLoanProductResponse(second)).thenReturn(
                LoanProductResponse.builder().tenantId(tenantId).mambuProductId("LP_002").build()
        );

        List<LoanProductResponse> responses = loanProductService.listLoanProducts(tenantId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getMambuProductId()).isEqualTo("LP_001");
        assertThat(responses.get(1).getMambuProductId()).isEqualTo("LP_002");
    }

    private LoanProductConfigRequest buildLoanProductConfig(String productName) {
        return LoanProductConfigRequest.builder()
                .productName(productName)
                .minLoanAmount(BigDecimal.valueOf(10_000))
                .maxLoanAmount(BigDecimal.valueOf(500_000))
                .minTenureMonths(6)
                .maxTenureMonths(48)
                .annualInterestRate(BigDecimal.valueOf(14.5))
                .processingFeePercent(BigDecimal.valueOf(1.5))
                .prepaymentPenaltyPercent(BigDecimal.valueOf(2.0))
                .currency("INR")
                .build();
    }

    private Tenant buildTenant(UUID id, String domain, TenantStatus status) {
        return Tenant.builder()
                .id(id)
                .name("Tenant " + domain)
                .domain(domain)
                .status(status)
                .planTier("ENTERPRISE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
