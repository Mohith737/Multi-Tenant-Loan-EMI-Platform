package com.loanplatform.loan_platform.domain.loan.model;

import java.util.EnumSet;
import java.util.Set;

public enum LoanApplicationStatus {
    APPLICATION_SUBMITTED,
    OFFER_SELECTED,
    OFFER_EXPIRED,
    UNDER_VERIFICATION,
    APPROVED,
    DISBURSED,
    ACTIVE_REPAYMENT,
    CLOSED,
    REJECTED;

    public static Set<LoanApplicationStatus> activeStatuses() {
        return EnumSet.of(
                APPLICATION_SUBMITTED,
                OFFER_SELECTED,
                UNDER_VERIFICATION,
                APPROVED,
                DISBURSED,
                ACTIVE_REPAYMENT
        );
    }
}
