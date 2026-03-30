package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanDocument;
import com.loanplatform.loan_platform.domain.loan.model.LoanDocumentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanStateHistory;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanDocumentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.dto.request.LoanDocumentUploadRequest;
import com.loanplatform.loan_platform.dto.response.LoanDocumentResponse;
import com.loanplatform.loan_platform.exception.LoanApplicationNotFoundException;
import com.loanplatform.loan_platform.exception.UnderwriterDecisionConflictException;
import com.loanplatform.loan_platform.mapper.LoanDocumentMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDocumentService {

    private static final String BANK_ACCOUNT_DETAILS = "BANK_ACCOUNT_DETAILS";
    private static final String INCOME_SOURCE_PROOF = "INCOME_SOURCE_PROOF";
    private static final String LAND_PAPER = "LAND_PAPER";
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");
    private static final Map<String, String> DOCUMENT_TYPE_ALIASES = Map.ofEntries(
            Map.entry("BANK_ACCOUNT_DETAILS", BANK_ACCOUNT_DETAILS),
            Map.entry("BANK_ACCOUNT_DETAIL", BANK_ACCOUNT_DETAILS),
            Map.entry("BANK_DETAILS", BANK_ACCOUNT_DETAILS),
            Map.entry("ACCOUNT_DETAILS", BANK_ACCOUNT_DETAILS),
            Map.entry("ACCOUNT_DETAIL_DOC", BANK_ACCOUNT_DETAILS),
            Map.entry("BANK_STATEMENT", BANK_ACCOUNT_DETAILS),
            Map.entry("INCOME_SOURCE_PROOF", INCOME_SOURCE_PROOF),
            Map.entry("INCOME_SOURCE", INCOME_SOURCE_PROOF),
            Map.entry("INCOME_PROOF", INCOME_SOURCE_PROOF),
            Map.entry("SOURCE_OF_INCOME", INCOME_SOURCE_PROOF),
            Map.entry("SALARY_SLIP", INCOME_SOURCE_PROOF),
            Map.entry("PAYSLIP", INCOME_SOURCE_PROOF),
            Map.entry("LAND_PAPER", LAND_PAPER),
            Map.entry("LAND_DOCUMENT", LAND_PAPER),
            Map.entry("PROPERTY_PAPER", LAND_PAPER),
            Map.entry("COLLATERAL_DOCUMENT", LAND_PAPER),
            Map.entry("EXTRA_COLLATERAL_DOCUMENT", LAND_PAPER)
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanDocumentRepository loanDocumentRepository;
    private final LoanStateHistoryRepository loanStateHistoryRepository;
    private final LoanStateMachinePort loanStateMachinePort;
    private final KafkaEventPort kafkaEventPort;
    private final LoanDocumentMapper loanDocumentMapper;
    @Value("${app.flow4.documents.low-amount-max:300000}")
    private BigDecimal lowAmountMax;
    @Value("${app.flow4.documents.mid-amount-max:700000}")
    private BigDecimal midAmountMax;

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public LoanDocumentResponse uploadDocument(
            UUID tenantId,
            UUID borrowerId,
            UUID applicationId,
            LoanDocumentUploadRequest request
    ) {
        String requestId = UUID.randomUUID().toString();
        log.debug("Loan document upload entry tenantId={} borrowerId={} applicationId={} documentType={} requestId={}",
                tenantId, borrowerId, applicationId, request.getDocumentType(), requestId);

        LoanApplication application = loanApplicationRepository.findForUpdate(applicationId, tenantId, borrowerId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        if (application.getSelectedOfferId() == null) {
            throw new IllegalArgumentException("Selected offer is required before uploading documents");
        }
        if (application.getStatus() == LoanApplicationStatus.APPROVED || application.getStatus() == LoanApplicationStatus.REJECTED) {
            throw new IllegalStateException("Documents cannot be uploaded after final underwriting decision");
        }

        String normalizedType = normalizeAndValidateIncomingDocumentType(request.getDocumentType());
        String normalizedReference = request.getDocumentReference().trim();
        List<LoanDocument> existingDocuments = loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId);
        boolean duplicateReference = existingDocuments.stream()
                .anyMatch(doc -> normalizeDocumentType(doc.getDocumentType()).equals(normalizedType)
                        && doc.getDocumentReference().equals(normalizedReference));
        if (duplicateReference) {
            throw new IllegalArgumentException("Duplicate document upload for same application/documentType/reference");
        }

        LoanDocument document = loanDocumentRepository.save(LoanDocument.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .tenantId(tenantId)
                .documentType(normalizedType)
                .documentReference(normalizedReference)
                .status(LoanDocumentStatus.UPLOADED)
                .build());

        transitionToUnderVerificationIfRequired(application, requestId, "borrower:" + borrowerId);

        kafkaEventPort.publish(
                "loan.documents.uploaded",
                tenantId,
                applicationId,
                "documentId=" + document.getId() + ",documentType=" + document.getDocumentType()
        );

        log.debug("Loan document upload exit tenantId={} borrowerId={} applicationId={} documentId={} requestId={}",
                tenantId, borrowerId, applicationId, document.getId(), requestId);
        return loanDocumentMapper.toLoanDocumentResponse(document);
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<LoanDocumentResponse> getDocumentsForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId) {
        loanApplicationRepository.findByIdAndTenantIdAndBorrowerId(applicationId, tenantId, borrowerId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        return loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId).stream()
                .map(loanDocumentMapper::toLoanDocumentResponse)
                .toList();
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<LoanDocumentResponse> getDocumentsForTenant(UUID tenantId, UUID applicationId) {
        loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        return loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId).stream()
                .map(loanDocumentMapper::toLoanDocumentResponse)
                .toList();
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public long countDocuments(UUID tenantId, UUID applicationId) {
        return loanDocumentRepository.countByTenantIdAndApplicationId(tenantId, applicationId);
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public void validateRequiredDocumentsForDecision(UUID tenantId, LoanApplication application) {
        if (!tenantId.equals(application.getTenantId())) {
            throw new IllegalArgumentException("Tenant mismatch while validating required documents");
        }

        List<LoanDocument> documents = loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, application.getId());
        Set<String> uploadedTypes = documents.stream()
                .map(doc -> normalizeDocumentType(doc.getDocumentType()))
                .collect(Collectors.toSet());
        Set<String> missingTypes = requiredDocumentTypes(application).stream()
                .filter(requiredType -> !uploadedTypes.contains(requiredType))
                .collect(Collectors.toSet());
        if (!missingTypes.isEmpty()) {
            throw new UnderwriterDecisionConflictException(
                    "Missing required documents for decision: " + String.join(", ", missingTypes)
            );
        }
    }

    private void transitionToUnderVerificationIfRequired(
            LoanApplication application,
            String requestId,
            String changedBy
    ) {
        if (application.getStatus() == LoanApplicationStatus.UNDER_VERIFICATION) {
            return;
        }
        List<LoanDocument> currentDocuments = loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(
                application.getTenantId(),
                application.getId()
        );
        if (!hasAllRequiredDocuments(application, currentDocuments)) {
            return;
        }

        LoanLifecycleState currentState = application.getLoanState();
        LoanLifecycleState nextState = loanStateMachinePort.transition(currentState, LoanLifecycleEvent.START_UNDER_VERIFICATION);
        application.setLoanState(nextState);
        application.setStatus(LoanApplicationStatus.UNDER_VERIFICATION);
        loanApplicationRepository.save(application);

        loanStateHistoryRepository.save(LoanStateHistory.builder()
                .id(UUID.randomUUID())
                .tenantId(application.getTenantId())
                .applicationId(application.getId())
                .fromState(currentState == null ? null : currentState.name())
                .toState(nextState.name())
                .event(LoanLifecycleEvent.START_UNDER_VERIFICATION.name())
                .changedBy(changedBy)
                .requestId(requestId)
                .changedAt(Instant.now())
                .build());

        kafkaEventPort.publish(
                "loan.underwriter.queue.entered",
                application.getTenantId(),
                application.getId(),
                "state=" + nextState.name()
        );
    }

    private boolean hasAllRequiredDocuments(
            LoanApplication application,
            List<LoanDocument> existingDocuments
    ) {
        Set<String> uploadedDocumentTypes = existingDocuments.stream()
                .map(doc -> normalizeDocumentType(doc.getDocumentType()))
                .collect(Collectors.toCollection(HashSet::new));
        return uploadedDocumentTypes.containsAll(requiredDocumentTypes(application));
    }

    private Set<String> requiredDocumentTypes(LoanApplication application) {
        if (application.getRequestedAmount() == null) {
            throw new IllegalStateException("requestedAmount is required for document verification policy");
        }
        if (lowAmountMax.compareTo(midAmountMax) > 0) {
            throw new IllegalStateException("Invalid document threshold configuration: low-amount-max must be <= mid-amount-max");
        }

        BigDecimal requestedAmount = application.getRequestedAmount();
        Set<String> requiredTypes = new HashSet<>();
        requiredTypes.add(BANK_ACCOUNT_DETAILS);
        if (requestedAmount.compareTo(lowAmountMax) > 0) {
            requiredTypes.add(INCOME_SOURCE_PROOF);
        }
        if (requestedAmount.compareTo(midAmountMax) > 0) {
            requiredTypes.add(LAND_PAPER);
        }
        return requiredTypes;
    }

    private String normalizeDocumentType(String documentType) {
        String normalized = NON_ALPHANUMERIC.matcher(documentType.trim().toUpperCase(Locale.ROOT))
                .replaceAll("_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "")
                .replaceAll("_{2,}", "_");
        return DOCUMENT_TYPE_ALIASES.getOrDefault(normalized, normalized);
    }

    private String normalizeAndValidateIncomingDocumentType(String documentType) {
        String normalizedType = normalizeDocumentType(documentType);
        if (normalizedType.isBlank()) {
            throw new IllegalArgumentException("Invalid documentType. Provide a supported document category");
        }
        return normalizedType;
    }
}
