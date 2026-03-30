package com.loanplatform.loan_platform.exception;

public class StripeTransferException extends RuntimeException {
    public StripeTransferException(String message) {
        super(message);
    }

    public StripeTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
