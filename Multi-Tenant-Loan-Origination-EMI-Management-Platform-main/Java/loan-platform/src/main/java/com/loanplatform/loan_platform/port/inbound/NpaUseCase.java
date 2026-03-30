package com.loanplatform.loan_platform.port.inbound;

import com.loanplatform.loan_platform.dto.request.NpaOverrideRequest;
import com.loanplatform.loan_platform.dto.response.NpaOverrideResponse;
import com.loanplatform.loan_platform.dto.response.NpaPortfolioResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface NpaUseCase {

    NpaPortfolioResponse getNpaPortfolio(UUID tenantId, int page, int size);

    NpaOverrideResponse overrideNpaLoan(UUID tenantId, UUID actorId, String loanAccountId, NpaOverrideRequest request);

    void flagNpaLoans(LocalDate processingDate);

    void runRecoveryEscalation(LocalDate processingDate);
}
