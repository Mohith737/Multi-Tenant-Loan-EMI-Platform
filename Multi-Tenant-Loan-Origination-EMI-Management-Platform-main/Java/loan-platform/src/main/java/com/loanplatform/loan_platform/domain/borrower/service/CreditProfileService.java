package com.loanplatform.loan_platform.domain.borrower.service;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.model.EligibilityCategory;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.dto.request.CreditProfileRequest;
import com.loanplatform.loan_platform.dto.response.CreditProfileResponse;
import com.loanplatform.loan_platform.exception.BorrowerNotFoundException;
import com.loanplatform.loan_platform.exception.BorrowerConflictException;
import com.loanplatform.loan_platform.exception.KycNotVerifiedException;
import com.loanplatform.loan_platform.mapper.BorrowerMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditProfileService {

    private final BorrowerRepository borrowerRepository;
    private final CreditProfileRepository creditProfileRepository;
    private final BorrowerMapper borrowerMapper;
    private final MambuSyncService mambuSyncService;

    @TenantAware
    @Transactional
    public CreditProfileResponse setup(UUID tenantId, UUID borrowerId, CreditProfileRequest request) {
        Borrower borrower = borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)
                .orElseThrow(() -> new BorrowerNotFoundException("Borrower not found: " + borrowerId));

        if (creditProfileRepository.findByBorrowerIdAndTenantId(borrowerId, tenantId).isPresent()) {
            throw new BorrowerConflictException("Credit profile already configured for borrower");
        }

        if (borrower.getStatus() != BorrowerStatus.KYC_VERIFIED) {
            throw new KycNotVerifiedException("Credit profile setup requires borrower status KYC_VERIFIED");
        }

        BigDecimal dtiRatio = request.getExistingEmiObligations()
                .multiply(BigDecimal.valueOf(100))
                .divide(request.getMonthlyIncome(), 2, RoundingMode.HALF_UP);

        EligibilityCategory eligibilityCategory = resolveEligibilityCategory(request.getCreditScore(), dtiRatio);

        CreditProfile creditProfile = CreditProfile.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .build();

        creditProfile.setCreditScore(request.getCreditScore());
        creditProfile.setCreditBureau(request.getCreditBureau().name());
        creditProfile.setMonthlyIncome(request.getMonthlyIncome());
        creditProfile.setExistingEmiObligations(request.getExistingEmiObligations());
        creditProfile.setEmploymentType(request.getEmploymentType());
        creditProfile.setEmployerName(request.getEmployerName());
        creditProfile.setEmploymentMonths(request.getEmploymentMonths());
        creditProfile.setDtiRatio(dtiRatio);
        creditProfile.setEligibilityCategory(eligibilityCategory.name());

        CreditProfile saved = creditProfileRepository.save(creditProfile);

        borrower.setStatus(BorrowerStatus.CREDIT_PROFILE_COMPLETE);
        borrowerRepository.save(borrower);

        try {
            MambuSyncService.MambuSyncResult syncResult = mambuSyncService.syncBorrower(tenantId, borrowerId);
            if (syncResult != null) {
                borrower.setMambuClientId(syncResult.mambuClientId());
                borrower.setMambuClientKey(syncResult.mambuClientKey());
            }
        } catch (Exception ex) {
            log.error("Immediate Mambu sync failed tenantId={} borrowerId={}", tenantId, borrowerId, ex);
        }

        return borrowerMapper.toCreditProfileResponse(saved);
    }

    private EligibilityCategory resolveEligibilityCategory(int creditScore, BigDecimal dtiRatio) {
        if (creditScore < 650 || dtiRatio.compareTo(BigDecimal.valueOf(60)) > 0) {
            return EligibilityCategory.SUBPRIME;
        }
        if (creditScore >= 750 && dtiRatio.compareTo(BigDecimal.valueOf(40)) < 0) {
            return EligibilityCategory.PRIME;
        }
        return EligibilityCategory.NEAR_PRIME;
    }
}
