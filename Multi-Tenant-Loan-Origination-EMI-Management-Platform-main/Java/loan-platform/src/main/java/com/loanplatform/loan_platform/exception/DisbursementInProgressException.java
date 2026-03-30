package com.loanplatform.loan_platform.exception;

public class DisbursementInProgressException extends RuntimeException {

    public DisbursementInProgressException(String message) {
        super(message);
    }
}
