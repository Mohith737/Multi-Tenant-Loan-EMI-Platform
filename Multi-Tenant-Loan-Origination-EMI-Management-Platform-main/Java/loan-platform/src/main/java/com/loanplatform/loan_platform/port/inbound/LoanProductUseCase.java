package com.loanplatform.loan_platform.port.inbound;

import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import com.loanplatform.loan_platform.dto.response.LoanProductResponse;

import java.util.List;
import java.util.UUID;

public interface LoanProductUseCase {
    LoanProductResponse createLoanProduct(UUID tenantId, LoanProductConfigRequest request);

    List<LoanProductResponse> listLoanProducts(UUID tenantId);
}
