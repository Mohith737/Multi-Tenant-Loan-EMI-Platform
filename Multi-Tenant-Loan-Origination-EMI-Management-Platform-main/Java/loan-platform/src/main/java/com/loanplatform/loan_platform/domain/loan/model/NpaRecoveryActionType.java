package com.loanplatform.loan_platform.domain.loan.model;

public enum NpaRecoveryActionType {
    FLAGGED_NON_PERFORMING,
    DAY_1_REMINDER,
    DAY_7_ESCALATION,
    DAY_30_LEGAL_ESCALATION,
    OVERRIDE_CLEARED
}
