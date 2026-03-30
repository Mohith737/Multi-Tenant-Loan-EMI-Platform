package com.loanplatform.loan_platform.unit.borrower;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.service.BorrowerService;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.dto.request.BorrowerAddressRequest;
import com.loanplatform.loan_platform.dto.request.BorrowerRegistrationRequest;
import com.loanplatform.loan_platform.dto.response.BorrowerRegistrationResponse;
import com.loanplatform.loan_platform.dto.response.BorrowerSyncStatusResponse;
import com.loanplatform.loan_platform.exception.DuplicatePanException;
import com.loanplatform.loan_platform.exception.TenantNotFoundException;
import com.loanplatform.loan_platform.security.JwtTokenService;
import com.loanplatform.loan_platform.util.EncryptionUtil;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowerServiceTest {

    @Mock
    private BorrowerRepository borrowerRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EncryptionUtil encryptionUtil;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private BorrowerService borrowerService;

    @Test
    void register_validRequest() {
        BorrowerRegistrationRequest request = validRequest();
        UUID tenantId = request.getTenantId();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant(tenantId)));
        when(encryptionUtil.encrypt("ABCDE1234F")).thenReturn("enc-pan");
        when(borrowerRepository.existsByTenantIdAndPanEncrypted(tenantId, "enc-pan")).thenReturn(false);
        when(borrowerRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(borrowerRepository.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenService.generateToken(any(), eq(java.util.List.of("BORROWER")), eq(tenantId), any()))
                .thenReturn(new JwtTokenService.TokenResult("jwt-token", Instant.now().plusSeconds(3600), 3600));

        BorrowerRegistrationResponse response = borrowerService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        ArgumentCaptor<Borrower> borrowerCaptor = ArgumentCaptor.forClass(Borrower.class);
        verify(borrowerRepository).save(borrowerCaptor.capture());
        Borrower saved = borrowerCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(BorrowerStatus.UNVERIFIED);
        assertThat(saved.getPanEncrypted()).isEqualTo("enc-pan");
        assertThat(saved.getAadhaarLastFour()).isEqualTo("1234");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void register_duplicatePan_throws409() {
        BorrowerRegistrationRequest request = validRequest();
        UUID tenantId = request.getTenantId();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenant(tenantId)));
        when(encryptionUtil.encrypt("ABCDE1234F")).thenReturn("enc-pan");
        when(borrowerRepository.existsByTenantIdAndPanEncrypted(tenantId, "enc-pan")).thenReturn(true);

        assertThatThrownBy(() -> borrowerService.register(request))
                .isInstanceOf(DuplicatePanException.class)
                .hasMessageContaining("PAN");
    }

    @Test
    void register_invalidTenant_throws404() {
        BorrowerRegistrationRequest request = validRequest();
        when(tenantRepository.findById(request.getTenantId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> borrowerService.register(request))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining("Active tenant not found");
    }

    @Test
    void getSyncStatus_synced() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .status(BorrowerStatus.CREDIT_PROFILE_COMPLETE)
                .mambuClientId("cli_123")
                .updatedAt(Instant.now())
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));

        BorrowerSyncStatusResponse response = borrowerService.getSyncStatus(tenantId, borrowerId);

        assertThat(response.isMambuClientCreated()).isTrue();
        assertThat(response.getMambuClientId()).isEqualTo("cli_123");
        assertThat(response.getMambuSyncStatus()).isEqualTo("SYNCED");
    }

    @Test
    void getSyncStatus_pendingRetry() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .status(BorrowerStatus.CREDIT_PROFILE_COMPLETE)
                .mambuClientId(null)
                .updatedAt(Instant.now())
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));

        BorrowerSyncStatusResponse response = borrowerService.getSyncStatus(tenantId, borrowerId);

        assertThat(response.isMambuClientCreated()).isFalse();
        assertThat(response.getMambuSyncStatus()).isEqualTo("PENDING_RETRY");
    }

    @Test
    void getSyncStatus_notReadyForMambu() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .status(BorrowerStatus.KYC_VERIFIED)
                .mambuClientId(null)
                .updatedAt(Instant.now())
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));

        BorrowerSyncStatusResponse response = borrowerService.getSyncStatus(tenantId, borrowerId);

        assertThat(response.isMambuClientCreated()).isFalse();
        assertThat(response.getMambuSyncStatus()).isEqualTo("NOT_READY_FOR_MAMBU");
    }

    private BorrowerRegistrationRequest validRequest() {
        return BorrowerRegistrationRequest.builder()
                .firstName("Rahul")
                .lastName("Sharma")
                .email("rahul@example.com")
                .password("S3cureP@ss")
                .mobile("+919876543210")
                .dateOfBirth(LocalDate.now().minusYears(30))
                .gender("MALE")
                .panNumber("ABCDE1234F")
                .aadhaarNumber("987654321234")
                .tenantId(UUID.randomUUID())
                .address(BorrowerAddressRequest.builder()
                        .line1("123 MG Road")
                        .city("Bengaluru")
                        .state("Karnataka")
                        .pincode("560001")
                        .build())
                .build();
    }

    private Tenant activeTenant(UUID tenantId) {
        return Tenant.builder()
                .id(tenantId)
                .name("Tenant")
                .domain("tenant.example.com")
                .status(TenantStatus.ACTIVE)
                .build();
    }
}
