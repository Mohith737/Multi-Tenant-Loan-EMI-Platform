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
public class LoanOfferSelectionResponse {
    private UUID applicationId;
    private UUID selectedOfferId;
    private String status;
    private String loanState;
    private Instant selectedAt;
}
