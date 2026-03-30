package com.loanplatform.loan_platform.port.inbound;

import com.loanplatform.loan_platform.dto.request.LoanDisbursementRequest;
import com.loanplatform.loan_platform.dto.response.DisbursementStatusResponse;
import com.loanplatform.loan_platform.dto.response.LoanDisbursementResponse;
import com.loanplatform.loan_platform.dto.response.LoanScheduleResponse;

import java.util.UUID;

public interface DisbursementUseCase {

    LoanDisbursementResponse disburse(UUID tenantId, UUID actorId, UUID applicationId, LoanDisbursementRequest request);

    DisbursementStatusResponse getDisbursementStatus(UUID tenantId, UUID applicationId);

    LoanScheduleResponse getScheduleForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId);

    LoanScheduleResponse getScheduleForTenant(UUID tenantId, UUID applicationId);

    LoanDisbursementResponse getDisbursementDetailsForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId);

    LoanDisbursementResponse getDisbursementDetailsForTenant(UUID tenantId, UUID applicationId);
}
