package com.loanplatform.loan_platform.port.inbound;

import com.loanplatform.loan_platform.dto.request.PrepaymentExecutionRequest;
import com.loanplatform.loan_platform.dto.request.PrepaymentSimulationRequest;
import com.loanplatform.loan_platform.dto.response.NocResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentAdminViewResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentExecutionResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentSimulationResponse;

import java.util.UUID;

public interface PrepaymentUseCase {

    PrepaymentSimulationResponse simulatePrepayment(
            UUID tenantId,
            UUID borrowerId,
            String loanAccountId,
            PrepaymentSimulationRequest request
    );

    PrepaymentExecutionResponse executePrepayment(
            UUID tenantId,
            UUID borrowerId,
            String loanAccountId,
            PrepaymentExecutionRequest request
    );

    PrepaymentExecutionResponse getPrepaymentRequest(
            UUID tenantId,
            UUID borrowerId,
            String loanAccountId,
            UUID requestId
    );

    PrepaymentAdminViewResponse getAdminPrepayments(UUID tenantId, int page, int size);

    NocResponse getNocForBorrower(UUID tenantId, UUID borrowerId, String loanAccountId);

    NocResponse getNocForTenant(UUID tenantId, String loanAccountId);
}
