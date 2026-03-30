package com.loanplatform.loan_platform.port.outbound;

import java.util.UUID;

public interface BorrowerNotificationPort {

    void onKycSubmitted(UUID borrowerId, UUID tenantId, int documentCount);

    void onKycVerified(UUID borrowerId, UUID tenantId);

    void onKycRejected(UUID borrowerId, UUID tenantId, String reason);

    void onLoanApproved(UUID borrowerId, UUID tenantId, UUID applicationId);

    void onLoanRejected(UUID borrowerId, UUID tenantId, UUID applicationId, String reason);

    void onLoanDisbursed(UUID borrowerId, UUID tenantId, UUID applicationId, String summaryMessage);
}
