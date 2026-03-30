package com.loanplatform.loan_platform.exception;

import java.util.List;

public class EmiCollectionPreconditionFailedException extends RuntimeException {

    private final List<String> failedConditions;

    public EmiCollectionPreconditionFailedException(List<String> failedConditions) {
        super("EMI collection preconditions failed");
        this.failedConditions = failedConditions;
    }

    public List<String> getFailedConditions() {
        return failedConditions;
    }
}
