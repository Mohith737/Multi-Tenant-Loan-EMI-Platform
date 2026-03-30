package com.loanplatform.loan_platform.port.inbound;

import com.loanplatform.loan_platform.dto.request.EmiWaiveRequest;
import com.loanplatform.loan_platform.dto.response.AdminDueInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.AdminFailedInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.AdminOverdueInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.BorrowerInstallmentScheduleResponse;
import com.loanplatform.loan_platform.dto.response.PaymentHistoryResponse;
import com.loanplatform.loan_platform.dto.response.ReconciliationReportResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface EmiCollectionUseCase {

    BorrowerInstallmentScheduleResponse getInstallmentsForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId);

    PaymentHistoryResponse getPaymentHistoryForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId);

    AdminDueInstallmentsResponse getDueTodayForAdmin(UUID tenantId, String statusFilter, int page, int size);

    AdminFailedInstallmentsResponse getFailedForAdmin(UUID tenantId, int page, int size);

    AdminOverdueInstallmentsResponse getOverdueForAdmin(UUID tenantId, int page, int size);

    void waiveInstallment(UUID tenantId, UUID actorId, UUID installmentId, EmiWaiveRequest request);

    ReconciliationReportResponse getReconciliationReport(UUID tenantId, LocalDate reportDate);
}
