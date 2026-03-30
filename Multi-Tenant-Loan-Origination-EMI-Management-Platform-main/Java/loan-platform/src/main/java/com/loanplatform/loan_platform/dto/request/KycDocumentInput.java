package com.loanplatform.loan_platform.dto.request;

import com.loanplatform.loan_platform.domain.borrower.model.KycDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocumentInput {

    @NotNull
    private KycDocumentType documentType;

    @NotBlank
    @Size(max = 128)
    private String documentNumber;

    @NotBlank
    @Size(max = 500)
    private String documentReference;
}
