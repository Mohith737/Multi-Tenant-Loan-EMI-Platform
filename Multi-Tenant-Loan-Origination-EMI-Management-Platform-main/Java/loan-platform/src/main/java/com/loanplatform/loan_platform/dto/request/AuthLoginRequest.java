package com.loanplatform.loan_platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request for JWT generation")
public class AuthLoginRequest {

    @NotBlank
    @Schema(example = "tenant_admin")
    private String username;

    @NotBlank
    @Schema(example = "tenant_admin_password")
    private String password;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Required for TENANT_ADMIN login. Optional for BORROWER login.")
    private UUID tenantId;
}
