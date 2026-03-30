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
public class TenantSuspensionResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;
    private String status;
    private Instant suspendedAt;
    private String reason;
}
