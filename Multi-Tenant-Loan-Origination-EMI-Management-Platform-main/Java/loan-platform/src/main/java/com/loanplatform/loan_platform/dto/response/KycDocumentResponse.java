package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocumentResponse {
    private UUID kycDocumentId;
    private UUID borrowerId;
    private String documentType;
    private String status;
    private String documentReference;
    private String rejectionReason;
    private Instant submittedAt;
    private Instant verifiedAt;
}
