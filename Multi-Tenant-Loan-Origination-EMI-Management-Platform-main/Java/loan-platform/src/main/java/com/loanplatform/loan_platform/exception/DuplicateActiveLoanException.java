package com.loanplatform.loan_platform.exception;

public class DuplicateActiveLoanException extends RuntimeException {
    public DuplicateActiveLoanException(String message) {
        super(message);
    }
}
