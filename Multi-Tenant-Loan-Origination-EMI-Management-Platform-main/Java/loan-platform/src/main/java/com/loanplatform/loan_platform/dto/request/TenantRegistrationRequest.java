package com.loanplatform.loan_platform.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tenant onboarding request")
public class TenantRegistrationRequest {

    @NotBlank
    @Schema(example = "ABC Finance Ltd")
    private String name;

    @NotBlank
    @Schema(example = "abcfinance.com")
    private String domain;

    @NotBlank
    @Email
    @Schema(example = "admin@abcfinance.com")
    private String adminEmail;

    @NotBlank
    @Schema(example = "+919876543210")
    private String adminPhone;

    @NotBlank
    @Schema(example = "ENTERPRISE")
    private String planTier;
}
