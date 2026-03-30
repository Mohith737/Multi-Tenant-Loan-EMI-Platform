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
public class LoanDocumentResponse {

    private UUID documentId;
    private UUID applicationId;
    private String documentType;
    private String documentReference;
    private String status;
    private String rejectionReason;
    private UUID verifiedBy;
    private Instant verifiedAt;
}
