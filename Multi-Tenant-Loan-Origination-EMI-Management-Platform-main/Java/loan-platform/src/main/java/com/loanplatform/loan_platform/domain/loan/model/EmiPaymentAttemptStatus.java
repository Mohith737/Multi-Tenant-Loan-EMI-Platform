package com.loanplatform.loan_platform.domain.loan.model;

public enum EmiPaymentAttemptStatus {
    INITIATED,
    STRIPE_SUCCEEDED,
    STRIPE_FAILED,
    MAMBU_POSTED,
    MAMBU_POST_FAILED
}
