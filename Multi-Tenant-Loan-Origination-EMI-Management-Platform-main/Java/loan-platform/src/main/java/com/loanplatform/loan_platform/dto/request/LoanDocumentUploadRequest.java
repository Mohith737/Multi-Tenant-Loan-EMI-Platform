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
public class LoanDocumentUploadRequest {

    @NotBlank
    @Size(max = 60)
    private String documentType;

    @NotBlank
    @Size(max = 500)
    private String documentReference;
}
