package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.port.inbound.NpaUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class NpaSchedulerJob {

    private final NpaUseCase npaUseCase;

    @Value("${app.flow8.scheduler.zone:Asia/Kolkata}")
    private String schedulerZone;

    @Scheduled(cron = "${app.flow8.scheduler.flag-cron:0 30 2 * * *}", zone = "${app.flow8.scheduler.zone:Asia/Kolkata}")
    public void runNpaFlagging() {
        LocalDate processingDate = LocalDate.now(ZoneId.of(schedulerZone));
        log.debug("Flow8 NPA flagging scheduler triggered processingDate={}", processingDate);
        npaUseCase.flagNpaLoans(processingDate);
    }

    @Scheduled(cron = "${app.flow8.scheduler.escalation-cron:0 45 2 * * *}", zone = "${app.flow8.scheduler.zone:Asia/Kolkata}")
    public void runEscalation() {
        LocalDate processingDate = LocalDate.now(ZoneId.of(schedulerZone));
        log.debug("Flow8 NPA escalation scheduler triggered processingDate={}", processingDate);
        npaUseCase.runRecoveryEscalation(processingDate);
    }
}
