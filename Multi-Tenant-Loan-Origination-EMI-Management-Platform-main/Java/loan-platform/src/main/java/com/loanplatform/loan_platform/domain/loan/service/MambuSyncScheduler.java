package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MambuSyncScheduler {

    private final LoanApplicationRepository loanApplicationRepository;
    private final UnderwriterService underwriterService;

    @Scheduled(fixedDelayString = "${app.mambu.sync.retry-ms:300000}")
    @Transactional(propagation = Propagation.REQUIRED)
    public void retryFailedMambuSync() {
        List<LoanApplication> failedApplications = loanApplicationRepository.findByMambuSyncFailedTrueAndStatus(
                LoanApplicationStatus.UNDER_VERIFICATION
        );
        for (LoanApplication application : failedApplications) {
            try {
                underwriterService.retryMambuSync(application);
            } catch (Exception ex) {
                log.error("Mambu sync retry failed tenantId={} applicationId={}",
                        application.getTenantId(), application.getId(), ex);
            }
        }
    }
}
