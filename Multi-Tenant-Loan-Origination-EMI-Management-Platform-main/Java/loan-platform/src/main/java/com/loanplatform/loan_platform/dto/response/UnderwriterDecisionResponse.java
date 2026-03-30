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
public class UnderwriterDecisionResponse {

    private UUID applicationId;
    private String decision;
    private String status;
    private String loanState;
    private String mambuLoanId;
    private String rejectionReason;
    private Instant decidedAt;
}
