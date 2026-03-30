package com.loanplatform.loan_platform.exception;

public class BorrowerNotFoundException extends RuntimeException {
    public BorrowerNotFoundException(String message) {
        super(message);
    }
}
