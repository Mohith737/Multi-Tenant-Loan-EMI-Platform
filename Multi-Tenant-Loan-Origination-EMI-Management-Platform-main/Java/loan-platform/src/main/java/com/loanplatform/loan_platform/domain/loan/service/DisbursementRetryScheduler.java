package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisbursementRetryScheduler {

    private final LoanApplicationRepository loanApplicationRepository;
    private final DisbursementService disbursementService;

    @Scheduled(fixedDelayString = "${app.flow5.schedule-retry-ms:300000}")
    public void retryFailedSchedules() {
        List<LoanApplication> failed = loanApplicationRepository
                .findByScheduleFetchFailedTrueAndStatus(LoanApplicationStatus.DISBURSED);

        for (LoanApplication application : failed) {
            try {
                disbursementService.retryFailedSchedulePersistence(application.getTenantId(), application.getId());
            } catch (Exception ex) {
                log.error("Flow5 schedule retry failed tenantId={} applicationId={}",
                        application.getTenantId(), application.getId(), ex);
            }
        }
    }
}
