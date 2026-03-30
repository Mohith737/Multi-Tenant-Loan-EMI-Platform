package com.loanplatform.loan_platform.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycSubmissionRequest {

    @NotEmpty
    private List<@Valid KycDocumentInput> documents;
}
