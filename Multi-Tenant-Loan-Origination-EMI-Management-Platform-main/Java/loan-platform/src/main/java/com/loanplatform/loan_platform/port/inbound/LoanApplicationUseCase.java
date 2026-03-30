package com.loanplatform.loan_platform.port.inbound;

import com.loanplatform.loan_platform.dto.request.LoanApplicationRequest;
import com.loanplatform.loan_platform.dto.request.LoanOfferSelectionRequest;
import com.loanplatform.loan_platform.dto.response.LoanApplicationResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferSelectionResponse;

import java.util.List;
import java.util.UUID;

public interface LoanApplicationUseCase {

    LoanApplicationResponse submitApplication(UUID tenantId, UUID borrowerId, LoanApplicationRequest request);

    List<LoanOfferResponse> getOffers(UUID tenantId, UUID borrowerId, UUID applicationId);

    LoanOfferSelectionResponse selectOffer(UUID tenantId, UUID borrowerId, UUID applicationId, LoanOfferSelectionRequest request);

    LoanApplicationResponse getApplication(UUID tenantId, UUID borrowerId, UUID applicationId);

    LoanApplicationResponse getApplicationForTenant(UUID tenantId, UUID applicationId);
}
