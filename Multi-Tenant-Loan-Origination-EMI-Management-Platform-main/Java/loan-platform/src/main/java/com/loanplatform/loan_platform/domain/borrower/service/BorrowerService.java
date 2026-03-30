package com.loanplatform.loan_platform.domain.borrower.service;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.dto.request.BorrowerRegistrationRequest;
import com.loanplatform.loan_platform.dto.response.BorrowerRegistrationResponse;
import com.loanplatform.loan_platform.dto.response.BorrowerSyncStatusResponse;
import com.loanplatform.loan_platform.exception.BorrowerConflictException;
import com.loanplatform.loan_platform.exception.BorrowerNotFoundException;
import com.loanplatform.loan_platform.exception.DuplicatePanException;
import com.loanplatform.loan_platform.exception.TenantNotFoundException;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.security.JwtTokenService;
import com.loanplatform.loan_platform.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BorrowerService {

    private final BorrowerRepository borrowerRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;
    private final JwtTokenService jwtTokenService;

    @TenantAware
    @Transactional
    public BorrowerRegistrationResponse register(BorrowerRegistrationRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .filter(value -> value.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new TenantNotFoundException("Active tenant not found: " + request.getTenantId()));

        validateAdultAge(request.getDateOfBirth());

        String panEncrypted = encryptionUtil.encrypt(request.getPanNumber());
        if (borrowerRepository.existsByTenantIdAndPanEncrypted(tenant.getId(), panEncrypted)) {
            throw new DuplicatePanException("PAN already registered for tenant");
        }
        if (borrowerRepository.existsByTenantIdAndEmailIgnoreCase(tenant.getId(), request.getEmail())) {
            throw new BorrowerConflictException("Borrower email already exists for tenant");
        }

        String aadhaarNumber = request.getAadhaarNumber();
        String aadhaarLastFour = aadhaarNumber.substring(aadhaarNumber.length() - 4);
        request.setAadhaarNumber(null);

        Borrower borrower = Borrower.builder()
                .id(UUID.randomUUID())
                .tenantId(tenant.getId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .mobile(request.getMobile())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .panEncrypted(panEncrypted)
                .aadhaarLastFour(aadhaarLastFour)
                .address(BorrowerAddress.builder()
                        .line1(request.getAddress().getLine1())
                        .city(request.getAddress().getCity())
                        .state(request.getAddress().getState())
                        .pincode(request.getAddress().getPincode())
                        .build())
                .status(BorrowerStatus.UNVERIFIED)
                .build();

        borrowerRepository.save(borrower);

        var token = jwtTokenService.generateToken(
                borrower.getId().toString(),
                List.of("BORROWER"),
                borrower.getTenantId(),
                Map.of("borrowerId", borrower.getId().toString())
        );

        return BorrowerRegistrationResponse.builder()
                .accessToken(token.token())
                .build();
    }

    @TenantAware
    @Transactional(readOnly = true)
    public BorrowerSyncStatusResponse getSyncStatus(UUID tenantId, UUID borrowerId) {
        Borrower borrower = borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)
                .orElseThrow(() -> new BorrowerNotFoundException("Borrower not found: " + borrowerId));

        boolean mambuClientCreated = borrower.getMambuClientId() != null && !borrower.getMambuClientId().isBlank();
        String mambuSyncStatus;
        if (mambuClientCreated) {
            mambuSyncStatus = "SYNCED";
        } else if (borrower.getStatus() == BorrowerStatus.CREDIT_PROFILE_COMPLETE) {
            mambuSyncStatus = "PENDING_RETRY";
        } else {
            mambuSyncStatus = "NOT_READY_FOR_MAMBU";
        }

        return BorrowerSyncStatusResponse.builder()
                .borrowerId(borrower.getId())
                .tenantId(borrower.getTenantId())
                .borrowerStatus(borrower.getStatus().name())
                .mambuClientCreated(mambuClientCreated)
                .mambuClientId(borrower.getMambuClientId())
                .mambuSyncStatus(mambuSyncStatus)
                .updatedAt(borrower.getUpdatedAt())
                .build();
    }

    private void validateAdultAge(LocalDate dateOfBirth) {
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        if (age < 18) {
            throw new IllegalArgumentException("Borrower must be at least 18 years old");
        }
    }
}
