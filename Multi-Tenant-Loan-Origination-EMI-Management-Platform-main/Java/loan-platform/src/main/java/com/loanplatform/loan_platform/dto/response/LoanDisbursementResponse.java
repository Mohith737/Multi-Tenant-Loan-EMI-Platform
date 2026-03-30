package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDisbursementResponse {

    private UUID applicationId;
    private String mambuLoanId;
    private String status;
    private String loanState;
    private String disbursementStatus;
    private String mambuDisbursementTxnId;
    private String stripeTransferId;
    private BigDecimal netDisbursedAmount;
    private BigDecimal processingFeeCharged;
    private Instant disbursedAt;
    private LocalDate firstRepaymentDate;
    private Integer totalInstallments;
}
