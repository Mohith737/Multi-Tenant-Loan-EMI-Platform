package com.loanplatform.loan_platform.exception;

public class TenantConflictException extends RuntimeException {
    public TenantConflictException(String message) {
        super(message);
    }
}
