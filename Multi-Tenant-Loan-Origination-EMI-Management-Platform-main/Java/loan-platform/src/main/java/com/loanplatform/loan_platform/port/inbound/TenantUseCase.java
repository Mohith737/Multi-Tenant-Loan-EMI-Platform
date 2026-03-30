package com.loanplatform.loan_platform.port.inbound;

import com.loanplatform.loan_platform.dto.request.ActivateTenantRequest;
import com.loanplatform.loan_platform.dto.request.SuspendTenantRequest;
import com.loanplatform.loan_platform.dto.request.TenantRegistrationRequest;
import com.loanplatform.loan_platform.dto.response.PublicTenantBrowseResponse;
import com.loanplatform.loan_platform.dto.response.TenantActivationResponse;
import com.loanplatform.loan_platform.dto.response.TenantRegistrationResponse;
import com.loanplatform.loan_platform.dto.response.TenantResponse;
import com.loanplatform.loan_platform.dto.response.TenantSuspensionResponse;

import java.util.List;
import java.util.UUID;

public interface TenantUseCase {
    TenantRegistrationResponse registerTenant(TenantRegistrationRequest request);

    TenantResponse getTenant(UUID tenantId);

    List<TenantResponse> listTenants();

    List<PublicTenantBrowseResponse> listActiveTenantsForPublicBrowse();

    TenantActivationResponse activateTenant(UUID tenantId, ActivateTenantRequest request);

    TenantSuspensionResponse suspendTenant(UUID tenantId, SuspendTenantRequest request);
}
