package com.loanplatform.loan_platform.exception;

public class LedgerSyncException extends RuntimeException {

    public LedgerSyncException(String message) {
        super(message);
    }

    public LedgerSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
