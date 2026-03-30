package com.loanplatform.loan_platform.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerificationRequest {

    @NotNull
    private KycVerificationDecision decision;

    @Size(max = 500)
    private String rejectionReason;

    @AssertTrue(message = "rejectionReason is mandatory when decision is REJECTED")
    public boolean isRejectionReasonValid() {
        if (decision != KycVerificationDecision.REJECTED) {
            return true;
        }
        return rejectionReason != null && !rejectionReason.isBlank();
    }
}
