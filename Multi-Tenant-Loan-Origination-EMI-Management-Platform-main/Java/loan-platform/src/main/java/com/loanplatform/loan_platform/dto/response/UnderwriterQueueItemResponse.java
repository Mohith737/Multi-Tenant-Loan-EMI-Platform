package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwriterQueueItemResponse {

    private UUID applicationId;
    private UUID borrowerId;
    private int documentsSubmitted;
    private String status;
    private String loanState;
}
