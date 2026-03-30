package com.loanplatform.loan_platform.exception;

public class LoanOfferExpiredException extends RuntimeException {
    public LoanOfferExpiredException(String message) {
        super(message);
    }
}
