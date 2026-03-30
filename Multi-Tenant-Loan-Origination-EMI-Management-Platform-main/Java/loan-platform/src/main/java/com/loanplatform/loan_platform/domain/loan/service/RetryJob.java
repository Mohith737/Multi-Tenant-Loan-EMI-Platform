package com.loanplatform.loan_platform.domain.loan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryJob {

    private final EmiCollectionService emiCollectionService;

    @Scheduled(cron = "${app.flow6.scheduler.retry-cron:0 0 18 * * *}", zone = "${app.flow6.scheduler.zone:Asia/Kolkata}")
    public void runRetryCollection() {
        LocalDate processingDate = LocalDate.now();
        log.debug("Flow6 retry scheduler triggered processingDate={}", processingDate);
        emiCollectionService.dispatchDueInstallments(processingDate, true);
    }
}
