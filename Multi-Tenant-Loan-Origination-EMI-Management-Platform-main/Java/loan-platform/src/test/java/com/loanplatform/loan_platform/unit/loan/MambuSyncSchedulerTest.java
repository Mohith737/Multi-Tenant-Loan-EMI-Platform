package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.service.MambuSyncScheduler;
import com.loanplatform.loan_platform.domain.loan.service.UnderwriterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MambuSyncSchedulerTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private UnderwriterService underwriterService;

    @InjectMocks
    private MambuSyncScheduler mambuSyncScheduler;

    @Test
    void retryFailedMambuSync_whenFailedAppsExist_retriesEach() {
        LoanApplication app1 = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .mambuSyncFailed(true)
                .build();
        LoanApplication app2 = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .mambuSyncFailed(true)
                .build();

        when(loanApplicationRepository.findByMambuSyncFailedTrueAndStatus(LoanApplicationStatus.UNDER_VERIFICATION))
                .thenReturn(List.of(app1, app2));

        mambuSyncScheduler.retryFailedMambuSync();

        verify(underwriterService, times(1)).retryMambuSync(app1);
        verify(underwriterService, times(1)).retryMambuSync(app2);
    }
}
