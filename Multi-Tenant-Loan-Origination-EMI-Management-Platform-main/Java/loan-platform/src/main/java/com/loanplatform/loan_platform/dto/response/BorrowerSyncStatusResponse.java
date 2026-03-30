package com.loanplatform.loan_platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Borrower Mambu sync status response")
public class BorrowerSyncStatusResponse {

    @Schema(example = "b550e840-e29b-41d4-a716-446655440001")
    private UUID borrowerId;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;

    @Schema(example = "CREDIT_PROFILE_COMPLETE")
    private String borrowerStatus;

    @Schema(example = "true")
    private boolean mambuClientCreated;

    @Schema(example = "cli_rahul_sharma_a1b2c3")
    private String mambuClientId;

    @Schema(example = "SYNCED", description = "SYNCED, PENDING_RETRY, or NOT_READY_FOR_MAMBU")
    private String mambuSyncStatus;

    @Schema(example = "2026-03-04T04:30:00Z")
    private Instant updatedAt;
}
