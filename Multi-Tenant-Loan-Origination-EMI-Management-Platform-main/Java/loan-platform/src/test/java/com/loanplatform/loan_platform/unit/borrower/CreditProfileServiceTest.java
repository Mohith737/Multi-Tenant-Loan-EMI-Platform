package com.loanplatform.loan_platform.unit.borrower;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.model.CreditBureau;
import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.borrower.service.CreditProfileService;
import com.loanplatform.loan_platform.domain.borrower.service.MambuSyncService;
import com.loanplatform.loan_platform.dto.request.CreditProfileRequest;
import com.loanplatform.loan_platform.dto.response.CreditProfileResponse;
import com.loanplatform.loan_platform.exception.BorrowerConflictException;
import com.loanplatform.loan_platform.exception.KycNotVerifiedException;
import com.loanplatform.loan_platform.mapper.BorrowerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditProfileServiceTest {

    @Mock
    private BorrowerRepository borrowerRepository;

    @Mock
    private CreditProfileRepository creditProfileRepository;

    @Mock
    private BorrowerMapper borrowerMapper;

    @Mock
    private MambuSyncService mambuSyncService;

    @InjectMocks
    private CreditProfileService creditProfileService;

    @Test
    void setup_blockedIfKycNotVerified() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder().id(borrowerId).tenantId(tenantId).status(BorrowerStatus.KYC_SUBMITTED).build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> creditProfileService.setup(tenantId, borrowerId, request(720, BigDecimal.valueOf(100000), BigDecimal.valueOf(10000))))
                .isInstanceOf(KycNotVerifiedException.class);
    }

    @Test
    void setup_whenCreditProfileAlreadyExists_throwsConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder().id(borrowerId).tenantId(tenantId).status(BorrowerStatus.KYC_VERIFIED).build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(creditProfileRepository.findByBorrowerIdAndTenantId(borrowerId, tenantId))
                .thenReturn(Optional.of(CreditProfile.builder().id(UUID.randomUUID()).borrowerId(borrowerId).tenantId(tenantId).build()));

        assertThatThrownBy(() -> creditProfileService.setup(tenantId, borrowerId, request(740, BigDecimal.valueOf(100000), BigDecimal.valueOf(10000))))
                .isInstanceOf(BorrowerConflictException.class)
                .hasMessageContaining("already configured");
    }

    @Test
    void dtiCalculation_correct() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder().id(borrowerId).tenantId(tenantId).status(BorrowerStatus.KYC_VERIFIED).build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(creditProfileRepository.findByBorrowerIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.empty());
        when(creditProfileRepository.save(any(CreditProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerRepository.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerMapper.toCreditProfileResponse(any(CreditProfile.class))).thenAnswer(invocation -> {
            CreditProfile profile = invocation.getArgument(0);
            return CreditProfileResponse.builder()
                    .borrowerId(profile.getBorrowerId())
                    .dtiRatio(profile.getDtiRatio())
                    .eligibilityCategory(profile.getEligibilityCategory())
                    .build();
        });

        CreditProfileResponse response = creditProfileService.setup(
                tenantId,
                borrowerId,
                request(760, BigDecimal.valueOf(100000), BigDecimal.valueOf(25000))
        );

        assertThat(response.getDtiRatio()).isEqualByComparingTo("25.00");
        verify(mambuSyncService).syncBorrower(tenantId, borrowerId);
    }

    @Test
    void eligibilityCategory_allThreeBranches() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenAnswer(invocation ->
                Optional.of(Borrower.builder().id(borrowerId).tenantId(tenantId).status(BorrowerStatus.KYC_VERIFIED).build())
        );
        when(creditProfileRepository.findByBorrowerIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.empty());
        when(creditProfileRepository.save(any(CreditProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerRepository.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerMapper.toCreditProfileResponse(any(CreditProfile.class))).thenAnswer(invocation -> {
            CreditProfile profile = invocation.getArgument(0);
            return CreditProfileResponse.builder()
                    .eligibilityCategory(profile.getEligibilityCategory())
                    .dtiRatio(profile.getDtiRatio())
                    .build();
        });

        CreditProfileResponse prime = creditProfileService.setup(
                tenantId,
                borrowerId,
                request(780, BigDecimal.valueOf(100000), BigDecimal.valueOf(20000))
        );
        CreditProfileResponse nearPrime = creditProfileService.setup(
                tenantId,
                borrowerId,
                request(700, BigDecimal.valueOf(100000), BigDecimal.valueOf(50000))
        );
        CreditProfileResponse subprime = creditProfileService.setup(
                tenantId,
                borrowerId,
                request(620, BigDecimal.valueOf(100000), BigDecimal.valueOf(20000))
        );

        assertThat(prime.getEligibilityCategory()).isEqualTo("PRIME");
        assertThat(nearPrime.getEligibilityCategory()).isEqualTo("NEAR_PRIME");
        assertThat(subprime.getEligibilityCategory()).isEqualTo("SUBPRIME");
    }

    private CreditProfileRequest request(int score, BigDecimal income, BigDecimal emi) {
        return CreditProfileRequest.builder()
                .creditScore(score)
                .creditBureau(CreditBureau.CIBIL)
                .monthlyIncome(income)
                .existingEmiObligations(emi)
                .employmentType("SALARIED")
                .employerName("Acme")
                .employmentMonths(36)
                .build();
    }
}
