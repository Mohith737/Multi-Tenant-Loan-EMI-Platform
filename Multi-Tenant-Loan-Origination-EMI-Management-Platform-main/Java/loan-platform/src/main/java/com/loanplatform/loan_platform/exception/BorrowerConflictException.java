package com.loanplatform.loan_platform.exception;

public class BorrowerConflictException extends RuntimeException {
    public BorrowerConflictException(String message) {
        super(message);
    }
}
