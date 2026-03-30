package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MambuRepaymentSyncRetryJob {

    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LedgerUpdateService ledgerUpdateService;

    @Scheduled(cron = "${app.flow6.scheduler.mambu-sync-retry-cron:0 */5 * * * *}", zone = "${app.flow6.scheduler.zone:Asia/Kolkata}")
    public void retryPendingMambuSync() {
        List<LoanInstallment> pending = loanInstallmentRepository.findMambuSyncPendingOlderThan(Instant.now().minusSeconds(60));
        log.debug("Flow6 mambu sync retry triggered pendingCount={}", pending.size());

        for (LoanInstallment installment : pending) {
            try {
                ledgerUpdateService.retryPendingMambuSync(installment);
            } catch (Exception ex) {
                log.error("Flow6 mambu sync retry failed tenantId={} installmentId={}",
                        installment.getTenantId(), installment.getId(), ex);
            }
        }
    }
}
