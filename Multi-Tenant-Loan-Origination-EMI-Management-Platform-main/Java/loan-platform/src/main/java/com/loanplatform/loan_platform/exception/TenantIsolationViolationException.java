package com.loanplatform.loan_platform.exception;

public class TenantIsolationViolationException extends RuntimeException {
    public TenantIsolationViolationException(String message) {
        super(message);
    }
}
