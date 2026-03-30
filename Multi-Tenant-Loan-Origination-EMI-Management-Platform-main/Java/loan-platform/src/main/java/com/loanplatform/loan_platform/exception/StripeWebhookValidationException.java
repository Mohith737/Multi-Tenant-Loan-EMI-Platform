package com.loanplatform.loan_platform.exception;

public class StripeWebhookValidationException extends RuntimeException {

    public StripeWebhookValidationException(String message) {
        super(message);
    }

    public StripeWebhookValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
