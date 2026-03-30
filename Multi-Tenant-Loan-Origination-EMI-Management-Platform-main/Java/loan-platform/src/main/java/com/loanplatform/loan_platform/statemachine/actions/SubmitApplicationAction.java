package com.loanplatform.loan_platform.statemachine.actions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class SubmitApplicationAction {

    public void execute(UUID tenantId, UUID applicationId, String requestId) {
        log.debug("SubmitApplicationAction executed tenantId={} applicationId={} requestId={}", tenantId, applicationId, requestId);
    }
}
