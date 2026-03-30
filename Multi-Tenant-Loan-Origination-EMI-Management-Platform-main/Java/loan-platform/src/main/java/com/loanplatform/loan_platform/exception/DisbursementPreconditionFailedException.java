package com.loanplatform.loan_platform.exception;

import java.util.List;

public class DisbursementPreconditionFailedException extends RuntimeException {

    private final List<String> failedConditions;

    public DisbursementPreconditionFailedException(List<String> failedConditions) {
        super("Disbursement preconditions failed");
        this.failedConditions = failedConditions;
    }

    public List<String> getFailedConditions() {
        return failedConditions;
    }
}
