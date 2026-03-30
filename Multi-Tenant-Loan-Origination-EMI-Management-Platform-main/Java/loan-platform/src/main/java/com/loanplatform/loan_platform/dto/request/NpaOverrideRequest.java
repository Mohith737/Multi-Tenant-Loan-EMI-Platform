package com.loanplatform.loan_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpaOverrideRequest {

    @NotBlank(message = "overrideReason is required")
    @Size(max = 500, message = "overrideReason must not exceed 500 characters")
    private String overrideReason;

    @NotBlank(message = "adminNote is required")
    @Size(max = 1000, message = "adminNote must not exceed 1000 characters")
    private String adminNote;
}
