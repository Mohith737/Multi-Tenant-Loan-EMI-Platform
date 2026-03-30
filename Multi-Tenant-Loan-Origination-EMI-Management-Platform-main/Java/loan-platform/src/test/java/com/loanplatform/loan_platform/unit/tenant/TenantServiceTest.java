package com.loanplatform.loan_platform.unit.tenant;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchResponse;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.domain.tenant.service.TenantService;
import com.loanplatform.loan_platform.dto.request.SuspendTenantRequest;
import com.loanplatform.loan_platform.dto.request.TenantRegistrationRequest;
import com.loanplatform.loan_platform.dto.response.TenantRegistrationResponse;
import com.loanplatform.loan_platform.exception.MambuIntegrationException;
import com.loanplatform.loan_platform.exception.TenantConflictException;
import com.loanplatform.loan_platform.mapper.TenantMapper;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private MambuPort mambuPort;

    @Mock
    private TenantMapper tenantMapper;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void registerTenant_validRequest_createsPendingThenActivatesAfterBranchCreation() {
        TenantRegistrationRequest request = buildTenantRegistrationRequest("acme.example.com");

        when(tenantRepository.existsByDomain(request.getDomain())).thenReturn(false);
        List<TenantStatus> savedStatuses = new ArrayList<>();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            savedStatuses.add(tenant.getStatus());
            return tenant;
        });
        when(mambuPort.createBranch(anyString(), eq(request.getName()), eq(request.getAdminEmail()), eq(request.getAdminPhone())))
                .thenReturn(MambuBranchResponse.builder().id("branch_001").encodedKey("branch_key_001").build());
        when(tenantMapper.toTenantRegistrationResponse(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            return TenantRegistrationResponse.builder()
                    .tenantId(tenant.getId())
                    .name(tenant.getName())
                    .domain(tenant.getDomain())
                    .status(tenant.getStatus().name())
                    .planTier(tenant.getPlanTier())
                    .mambuBranchId(tenant.getMambuBranchId())
                    .mambuBranchKey(tenant.getMambuBranchEncodedKey())
                    .createdAt(Instant.now())
                    .build();
        });

        TenantRegistrationResponse response = tenantService.registerTenant(request);

        assertThat(response.getStatus()).isEqualTo(TenantStatus.ACTIVE.name());
        assertThat(response.getMambuBranchId()).isEqualTo("branch_001");
        assertThat(response.getMambuBranchKey()).isEqualTo("branch_key_001");

        verify(tenantRepository, times(2)).save(any(Tenant.class));
        assertThat(savedStatuses).containsExactly(TenantStatus.PENDING, TenantStatus.ACTIVE);
    }

    @Test
    void registerTenant_mambuBranchFails_keepsTenantPendingAndThrowsException() {
        TenantRegistrationRequest request = buildTenantRegistrationRequest("broken.example.com");

        when(tenantRepository.existsByDomain(request.getDomain())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mambuPort.createBranch(anyString(), eq(request.getName()), eq(request.getAdminEmail()), eq(request.getAdminPhone())))
                .thenThrow(new RuntimeException("Mambu branch API unavailable"));

        assertThatThrownBy(() -> tenantService.registerTenant(request))
                .isInstanceOf(MambuIntegrationException.class)
                .hasMessageContaining("Tenant onboarding failed while provisioning in Mambu");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, times(1)).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getStatus()).isEqualTo(TenantStatus.PENDING);
    }

    @Test
    void registerTenant_duplicateDomain_throwsDuplicateTenantException() {
        TenantRegistrationRequest request = buildTenantRegistrationRequest("duplicate.example.com");
        when(tenantRepository.existsByDomain(request.getDomain())).thenReturn(true);

        assertThatThrownBy(() -> tenantService.registerTenant(request))
                .isInstanceOf(TenantConflictException.class)
                .hasMessageContaining("Tenant domain already exists");
    }

    @Test
    void activateTenant_pendingTenant_setsStatusActive() {
        UUID tenantId = UUID.randomUUID();
        Tenant pendingTenant = buildTenant(tenantId, "pending.example.com", TenantStatus.PENDING);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(pendingTenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tenantService.activateTenant(tenantId, new com.loanplatform.loan_platform.dto.request.ActivateTenantRequest());

        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getStatus()).isEqualTo(TenantStatus.ACTIVE.name());
        assertThat(pendingTenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void suspendTenant_activeTenant_setsStatusSuspended() {
        UUID tenantId = UUID.randomUUID();
        Tenant activeTenant = buildTenant(tenantId, "active.example.com", TenantStatus.ACTIVE);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tenantService.suspendTenant(
                tenantId,
                SuspendTenantRequest.builder().reason("Compliance hold").build()
        );

        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getStatus()).isEqualTo(TenantStatus.SUSPENDED.name());
        assertThat(response.getReason()).isEqualTo("Compliance hold");
        assertThat(activeTenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }

    private TenantRegistrationRequest buildTenantRegistrationRequest(String domain) {
        return TenantRegistrationRequest.builder()
                .name("Acme Finance")
                .domain(domain)
                .adminEmail("admin@acme.example.com")
                .adminPhone("+919999999999")
                .planTier("ENTERPRISE")
                .build();
    }

    private Tenant buildTenant(UUID id, String domain, TenantStatus status) {
        return Tenant.builder()
                .id(id)
                .name("Tenant " + domain)
                .domain(domain)
                .status(status)
                .planTier("ENTERPRISE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
