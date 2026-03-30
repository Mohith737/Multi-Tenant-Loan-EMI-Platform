package com.loanplatform.loan_platform.domain.loan.model;

public enum LoanInstallmentStatus {
    PENDING,
    PROCESSING,
    PAID,
    RETRY_SCHEDULED,
    REQUIRES_ACTION,
    WAIVED,
    OVERDUE,
    FAILED
}
