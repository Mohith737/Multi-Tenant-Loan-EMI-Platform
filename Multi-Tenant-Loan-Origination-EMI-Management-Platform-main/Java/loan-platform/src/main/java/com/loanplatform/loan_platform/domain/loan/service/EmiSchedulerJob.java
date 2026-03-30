package com.loanplatform.loan_platform.domain.loan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmiSchedulerJob {

    private final EmiCollectionService emiCollectionService;

    @Scheduled(cron = "${app.flow6.scheduler.collection-cron:0 0 9 * * *}", zone = "${app.flow6.scheduler.zone:Asia/Kolkata}")
    public void runDailyCollection() {
        LocalDate processingDate = LocalDate.now();
        log.debug("Flow6 EMI scheduler triggered processingDate={}", processingDate);
        emiCollectionService.dispatchDueInstallments(processingDate, false);
    }
}
