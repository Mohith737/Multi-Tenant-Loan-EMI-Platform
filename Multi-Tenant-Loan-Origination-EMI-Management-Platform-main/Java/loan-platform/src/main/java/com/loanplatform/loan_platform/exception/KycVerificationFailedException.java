package com.loanplatform.loan_platform.exception;

public class KycVerificationFailedException extends RuntimeException {
    public KycVerificationFailedException(String message) {
        super(message);
    }
}
