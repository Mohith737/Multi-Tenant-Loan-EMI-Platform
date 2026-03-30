package com.loanplatform.loan_platform.exception;

public class LoanEligibilityFailedException extends RuntimeException {
    public LoanEligibilityFailedException(String message) {
        super(message);
    }
}
