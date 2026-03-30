package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFailedInstallmentsResponse {

    private Integer page;
    private Integer size;
    private Long total;
    private List<BorrowerInstallmentScheduleResponse.InstallmentItem> installments;
}
