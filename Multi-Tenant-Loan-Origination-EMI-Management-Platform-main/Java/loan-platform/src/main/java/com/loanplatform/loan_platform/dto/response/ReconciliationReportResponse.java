package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReportResponse {

    private LocalDate reportDate;
    private Integer totalDueCount;
    private Integer totalCollectedCount;
    private Integer totalFailedCount;
    private BigDecimal totalAmountDue;
    private BigDecimal totalAmountCollected;
    private BigDecimal mambuTotalPosted;
    private BigDecimal discrepancyAmount;
    private boolean reconciled;
    private String reviewedBy;
    private Instant reviewedAt;
}
