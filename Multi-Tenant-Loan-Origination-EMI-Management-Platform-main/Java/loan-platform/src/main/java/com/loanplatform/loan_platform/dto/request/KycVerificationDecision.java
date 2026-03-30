package com.loanplatform.loan_platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "KYC verification decision")
public enum KycVerificationDecision {
    VERIFIED,
    REJECTED
}
