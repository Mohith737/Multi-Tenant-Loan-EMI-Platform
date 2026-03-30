package com.loanplatform.loan_platform.dto.response;

import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
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
@Schema(description = "Tenant loan product mapping response")
public class LoanProductResponse {

    @Schema(example = "a6f8fd7b-c468-4a0b-aead-4f296ac251d8")
    private UUID loanProductId;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID tenantId;

    @Schema(example = "LP_A1B2C3D4")
    private String mambuProductId;

    @Schema(example = "8a818f8f8d9f0abc0190123456789abc")
    private String mambuProductKey;

    private LoanProductConfigRequest loanProductConfig;

    private Instant createdAt;
}
