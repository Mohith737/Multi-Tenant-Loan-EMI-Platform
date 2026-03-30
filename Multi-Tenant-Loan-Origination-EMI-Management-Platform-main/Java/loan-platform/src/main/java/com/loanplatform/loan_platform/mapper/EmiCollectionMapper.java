package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttempt;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.ReconciliationReport;
import com.loanplatform.loan_platform.dto.response.BorrowerInstallmentScheduleResponse;
import com.loanplatform.loan_platform.dto.response.PaymentHistoryResponse;
import com.loanplatform.loan_platform.dto.response.ReconciliationReportResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmiCollectionMapper {

    @Mapping(target = "installmentId", source = "id")
    @Mapping(target = "paymentStatus", expression = "java(installment.getStatus().name())")
    BorrowerInstallmentScheduleResponse.InstallmentItem toInstallmentItem(LoanInstallment installment);

    @Mapping(target = "installmentId", source = "installment.id")
    @Mapping(target = "installmentNumber", source = "installment.installmentNumber")
    @Mapping(target = "paidAmount", source = "installment.totalDue")
    @Mapping(target = "paidDate", source = "installment.paidDate")
    @Mapping(target = "stripePaymentIntentId", source = "attempt.stripePaymentIntentId")
    @Mapping(target = "stripeStatus", source = "attempt.stripeStatus")
    @Mapping(target = "mambuTransactionId", source = "attempt.mambuTransactionId")
    @Mapping(target = "mambuTransactionKey", source = "attempt.mambuTransactionKey")
    @Mapping(target = "status", expression = "java(installment.getStatus().name())")
    @Mapping(target = "attemptedAt", source = "attempt.attemptedAt")
    PaymentHistoryResponse.PaymentItem toPaymentHistoryItem(LoanInstallment installment, EmiPaymentAttempt attempt);

    @Mapping(target = "reviewedBy", expression = "java(report.getReviewedBy() == null ? null : report.getReviewedBy().toString())")
    ReconciliationReportResponse toReconciliationResponse(ReconciliationReport report);
}
