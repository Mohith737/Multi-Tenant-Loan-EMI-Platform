package com.loanplatform.loan_platform.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@Schema(description = "Tenant response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;
    private String name;
    private String domain;
    private String status;
    private String planTier;
    private String mambuBranchId;
    private String mambuBranchKey;
    private Instant createdAt;
    private Instant updatedAt;
}
