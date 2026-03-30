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
@Schema(description = "Tenant registration response")
public class TenantRegistrationResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;

    @Schema(example = "ABC Finance Ltd")
    private String name;

    @Schema(example = "abcfinance.com")
    private String domain;

    @Schema(example = "ACTIVE")
    private String status;

    @Schema(example = "ENTERPRISE")
    private String planTier;

    @Schema(example = "branch_abc_001")
    private String mambuBranchId;

    @Schema(example = "branch_key_001")
    private String mambuBranchKey;

    @Schema(example = "2024-01-15T10:30:00Z")
    private Instant createdAt;
}
