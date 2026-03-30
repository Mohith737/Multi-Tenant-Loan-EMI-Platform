package com.loanplatform.loan_platform.domain.borrower.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.exception.BorrowerNotFoundException;
import com.loanplatform.loan_platform.exception.TenantNotFoundException;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MambuSyncService {

    private final BorrowerRepository borrowerRepository;
    private final TenantRepository tenantRepository;
    private final MambuPort mambuPort;
    private final EncryptionUtil encryptionUtil;

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MambuSyncResult syncBorrower(UUID tenantId, UUID borrowerId) {
        Borrower borrower = borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)
                .orElseThrow(() -> new BorrowerNotFoundException("Borrower not found: " + borrowerId));

        return syncBorrowerInternal(borrower);
    }

    @Scheduled(fixedDelayString = "${app.mambu.sync.retry-ms:300000}")
    @Transactional
    public void retryFailedSyncs() {
        List<Borrower> pendingSyncBorrowers = borrowerRepository.findByStatusAndMambuClientIdIsNull(BorrowerStatus.CREDIT_PROFILE_COMPLETE);
        for (Borrower borrower : pendingSyncBorrowers) {
            try {
                syncBorrowerInternal(borrower);
            } catch (Exception ex) {
                log.error("Retry Mambu sync failed tenantId={} borrowerId={}", borrower.getTenantId(), borrower.getId(), ex);
            }
        }
    }

    private MambuSyncResult syncBorrowerInternal(Borrower borrower) {
        if (borrower.getMambuClientId() != null && !borrower.getMambuClientId().isBlank()) {
            return new MambuSyncResult(borrower.getMambuClientId(), borrower.getMambuClientKey());
        }

        Tenant tenant = tenantRepository.findById(borrower.getTenantId())
                .filter(value -> value.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new TenantNotFoundException("Active tenant not found: " + borrower.getTenantId()));

        if (tenant.getMambuBranchEncodedKey() == null || tenant.getMambuBranchEncodedKey().isBlank()) {
            throw new IllegalStateException("Tenant Mambu branch key missing for tenantId=" + tenant.getId());
        }

        String pan = encryptionUtil.decrypt(borrower.getPanEncrypted());

        MambuClientCreateRequest request = MambuClientCreateRequest.builder()
                .firstName(borrower.getFirstName())
                .lastName(borrower.getLastName())
                .emailAddress(borrower.getEmail())
                .mobilePhone(borrower.getMobile())
                .birthDate(borrower.getDateOfBirth().toString())
                .gender(borrower.getGender())
                .assignedBranchKey(tenant.getMambuBranchEncodedKey())
                .idDocuments(List.of(
                        MambuClientCreateRequest.IdDocument.builder()
                                .documentType("PAN")
                                .documentId(pan)
                                .build()
                ))
                .build();

        MambuClientResponse response = mambuPort.createClient(request);
        borrower.setMambuClientId(response.getId());
        borrower.setMambuClientKey(response.getEncodedKey());
        borrowerRepository.save(borrower);
        return new MambuSyncResult(response.getId(), response.getEncodedKey());
    }

    public record MambuSyncResult(String mambuClientId, String mambuClientKey) {
    }
}
