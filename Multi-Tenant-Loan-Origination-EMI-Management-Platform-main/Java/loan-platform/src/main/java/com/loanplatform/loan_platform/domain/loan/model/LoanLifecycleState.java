package com.loanplatform.loan_platform.domain.loan.model;

public enum LoanLifecycleState {
    APPLICATION_DRAFT,
    APPLICATION_SUBMITTED,
    OFFER_SELECTED,
    OFFER_EXPIRED,
    UNDER_VERIFICATION,
    APPROVED,
    DISBURSED,
    ACTIVE_REPAYMENT,
    NON_PERFORMING,
    LEGAL_ESCALATION,
    FORECLOSED,
    REJECTED
}
