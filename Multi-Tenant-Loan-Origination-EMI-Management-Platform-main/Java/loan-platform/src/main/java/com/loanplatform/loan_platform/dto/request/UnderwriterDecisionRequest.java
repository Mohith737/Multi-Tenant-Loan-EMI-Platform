package com.loanplatform.loan_platform.dto.request;

import com.loanplatform.loan_platform.domain.loan.model.UnderwriterDecisionType;
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
public class UnderwriterDecisionRequest {

    @NotNull
    private UnderwriterDecisionType decision;

    @Size(max = 1000)
    private String remarks;
}
