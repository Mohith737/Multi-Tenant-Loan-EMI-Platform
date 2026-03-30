package com.loanplatform.loan_platform.integration.loan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.KycDocumentRepository;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanDocumentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.loan.repository.UnderwriterDecisionRepository;
import com.loanplatform.loan_platform.domain.loan.service.MambuSyncScheduler;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Flow4DocumentApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private CreditProfileRepository creditProfileRepository;
    @Autowired
    private KycDocumentRepository kycDocumentRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private LoanOfferRepository loanOfferRepository;
    @Autowired
    private LoanDocumentRepository loanDocumentRepository;
    @Autowired
    private UnderwriterDecisionRepository underwriterDecisionRepository;
    @Autowired
    private LoanStateHistoryRepository loanStateHistoryRepository;
    @Autowired
    private MambuSyncScheduler mambuSyncScheduler;

    @MockBean
    private MambuPort mambuPort;

    private UUID tenantA;
    private UUID tenantB;
    private UUID borrowerA;
    private UUID borrowerB;

    @BeforeEach
    void setUp() {
        loanStateHistoryRepository.deleteAll();
        underwriterDecisionRepository.deleteAll();
        loanDocumentRepository.deleteAll();
        loanOfferRepository.deleteAll();
        loanApplicationRepository.deleteAll();
        creditProfileRepository.deleteAll();
        kycDocumentRepository.deleteAll();
        borrowerRepository.deleteAll();
        loanProductRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantA = createTenant("Tenant A", "tenant-a-flow4.loanos.dev");
        tenantB = createTenant("Tenant B", "tenant-b-flow4.loanos.dev");

        createLoanProduct(tenantA, "LP_TENANT_A");
        createLoanProduct(tenantB, "LP_TENANT_B");

        borrowerA = createBorrowerWithCredit(tenantA, "borrower.a@flow4.dev", "cli_flow4_a");
        borrowerB = createBorrowerWithCredit(tenantB, "borrower.b@flow4.dev", "cli_flow4_b");

        when(mambuPort.simulateLoan(any())).thenAnswer(invocation -> {
            var req = invocation.getArgument(0, com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationRequest.class);
            int tenure = req.getRepaymentInstallments();
            BigDecimal principal = req.getLoanAmount().getValue();
            BigDecimal emi = principal.divide(BigDecimal.valueOf(tenure), 2, RoundingMode.HALF_UP).add(BigDecimal.valueOf(2500));
            BigDecimal totalRepaid = emi.multiply(BigDecimal.valueOf(tenure));
            BigDecimal totalInterest = totalRepaid.subtract(principal);
            return MambuLoanSimulationResponse.builder()
                    .periodicPayment(emi)
                    .totalInterestCharged(totalInterest.setScale(2, RoundingMode.HALF_UP))
                    .annualPercentageRate(BigDecimal.valueOf(14.5))
                    .totalAmountRepaid(totalRepaid.setScale(2, RoundingMode.HALF_UP))
                    .build();
        });

        when(mambuPort.createLoanAccount(any())).thenReturn(
                MambuLoanAccountResponse.builder().id("LN_FLOW4_001").accountState("PENDING_APPROVAL").build()
        );
        when(mambuPort.approveLoanAccount(any(), any())).thenReturn(
                MambuLoanAccountResponse.builder().id("LN_FLOW4_001").accountState("APPROVED").build()
        );
    }

    @Test
    void uploadQueueAndApprove_happyPath_success() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "borrower.a@flow4.dev");
        String underwriterToken = adminToken(tenantA, "underwriter-a");

        UUID applicationId = createAndSelectOffer(tenantA, borrowerToken, "LP_TENANT_A", 500000);

        mockMvc.perform(post("/api/v1/loans/{applicationId}/documents", applicationId)
                        .header("Authorization", "Bearer " + borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "account detail doc",
                                  "documentReference": "s3://loan-docs/appA/bank-details.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.documentType").value("BANK_ACCOUNT_DETAILS"));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/documents", applicationId)
                        .header("Authorization", "Bearer " + borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "income proof",
                                  "documentReference": "s3://loan-docs/appA/income-proof.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.documentType").value("INCOME_SOURCE_PROOF"));

        mockMvc.perform(get("/api/v1/underwriter/queue")
                        .header("Authorization", "Bearer " + underwriterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.queue[0].applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.queue[0].status").value("UNDER_VERIFICATION"));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + underwriterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVED",
                                  "remarks": "All documents verified"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.loanState").value("APPROVED"))
                .andExpect(jsonPath("$.mambuLoanId").value("LN_FLOW4_001"));

        mockMvc.perform(get("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + borrowerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void underwriterDecision_duplicateDecision_returnsConflict() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "borrower.a@flow4.dev");
        String underwriterToken = adminToken(tenantA, "underwriter-a");

        UUID applicationId = createAndSelectOffer(tenantA, borrowerToken, "LP_TENANT_A", 500000);
        uploadDocument(applicationId, borrowerToken, "account detail doc", "s3://loan-docs/appA/bank-details.pdf");
        uploadDocument(applicationId, borrowerToken, "income_source", "s3://loan-docs/appA/income-proof.pdf");

        mockMvc.perform(post("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + underwriterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECTED",
                                  "remarks": "Bank statement mismatch"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + underwriterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVED",
                                  "remarks": "Retry decision should fail"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNDERWRITER_DECISION_CONFLICT"));
    }

    @Test
    void flow4_tenantIsolation_crossTenantAccessDeniedByNotFound() throws Exception {
        String borrowerTokenA = borrowerToken(tenantA, borrowerA, "borrower.a@flow4.dev");
        String borrowerTokenB = borrowerToken(tenantB, borrowerB, "borrower.b@flow4.dev");
        String underwriterTokenB = adminToken(tenantB, "underwriter-b");

        UUID applicationId = createAndSelectOffer(tenantA, borrowerTokenA, "LP_TENANT_A", 500000);
        uploadDocument(applicationId, borrowerTokenA, "bank_details", "s3://loan-docs/appA/address.pdf");

        mockMvc.perform(get("/api/v1/loans/{applicationId}/documents", applicationId)
                        .header("Authorization", "Bearer " + borrowerTokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + underwriterTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVED",
                                  "remarks": "Cross tenant should fail"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_NOT_FOUND"));
    }

    @Test
    void uploadDocument_invalidPayload_returnsBadRequestValidationError() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "borrower.a@flow4.dev");
        UUID applicationId = createAndSelectOffer(tenantA, borrowerToken, "LP_TENANT_A", 500000);

        mockMvc.perform(post("/api/v1/loans/{applicationId}/documents", applicationId)
                        .header("Authorization", "Bearer " + borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "  ",
                                  "documentReference": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/documents", applicationId)
                        .header("Authorization", "Bearer " + borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "###",
                                  "documentReference": "s3://loan-docs/appA/invalid-type.pdf"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void approveDecision_whenMambuFails_thenSchedulerRetryCompletesApproval() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "borrower.a@flow4.dev");
        String underwriterToken = adminToken(tenantA, "underwriter-a");

        UUID applicationId = createAndSelectOffer(tenantA, borrowerToken, "LP_TENANT_A", 500000);
        uploadDocument(applicationId, borrowerToken, "bank statement", "s3://loan-docs/appA/bank-proof.pdf");
        uploadDocument(applicationId, borrowerToken, "INCOME_PROOF", "s3://loan-docs/appA/failure-case.pdf");

        when(mambuPort.createLoanAccount(any())).thenThrow(new RuntimeException("Mambu create failed"));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + underwriterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVED",
                                  "remarks": "Try approve with temporary Mambu outage"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"))
                .andExpect(jsonPath("$.loanState").value("UNDER_VERIFICATION"));

        LoanApplication failedApplication = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantA).orElseThrow();
        assertThat(failedApplication.isMambuSyncFailed()).isTrue();
        assertThat(failedApplication.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_VERIFICATION);

        doReturn(
                MambuLoanAccountResponse.builder().id("LN_FLOW4_RETRY").accountState("PENDING_APPROVAL").build()
        ).when(mambuPort).createLoanAccount(any());
        doReturn(
                MambuLoanAccountResponse.builder().id("LN_FLOW4_RETRY").accountState("APPROVED").build()
        ).when(mambuPort).approveLoanAccount(any(), any());

        mambuSyncScheduler.retryFailedMambuSync();

        LoanApplication retried = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantA).orElseThrow();
        assertThat(retried.isMambuSyncFailed()).isFalse();
        assertThat(retried.getStatus()).isEqualTo(LoanApplicationStatus.APPROVED);
        assertThat(retried.getMambuLoanId()).isEqualTo("LN_FLOW4_RETRY");
    }

    @Test
    void uploadDocument_lowAmountOnlyBankDetails_entersUnderwriterQueue() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "borrower.a@flow4.dev");
        String underwriterToken = adminToken(tenantA, "underwriter-a");

        UUID applicationId = createAndSelectOffer(tenantA, borrowerToken, "LP_TENANT_A", 250000);
        uploadDocument(applicationId, borrowerToken, "bank account details", "s3://loan-docs/appA/low-amount-bank.pdf");

        mockMvc.perform(get("/api/v1/underwriter/queue")
                        .header("Authorization", "Bearer " + underwriterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.queue[0].applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.queue[0].status").value("UNDER_VERIFICATION"));
    }

    @Test
    void underwriterDecision_highAmountWithoutLandPaper_returnsConflict_thenSucceedsAfterUpload() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "borrower.a@flow4.dev");
        String underwriterToken = adminToken(tenantA, "underwriter-a");

        UUID applicationId = createAndSelectOffer(tenantA, borrowerToken, "LP_TENANT_A", 900000);
        uploadDocument(applicationId, borrowerToken, "bank account detail", "s3://loan-docs/appA/high-bank.pdf");
        uploadDocument(applicationId, borrowerToken, "income proof", "s3://loan-docs/appA/high-income.pdf");

        mockMvc.perform(get("/api/v1/underwriter/queue")
                        .header("Authorization", "Bearer " + underwriterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + underwriterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVED",
                                  "remarks": "Try before collateral doc"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNDERWRITER_DECISION_CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("UNDER_VERIFICATION")));

        uploadDocument(applicationId, borrowerToken, "property paper", "s3://loan-docs/appA/high-land.pdf");

        mockMvc.perform(get("/api/v1/underwriter/queue")
                        .header("Authorization", "Bearer " + underwriterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.queue[0].applicationId").value(applicationId.toString()));

        mockMvc.perform(post("/api/v1/loans/{applicationId}/decision", applicationId)
                        .header("Authorization", "Bearer " + underwriterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVED",
                                  "remarks": "All required docs uploaded"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.loanState").value("APPROVED"));
    }

    private UUID createAndSelectOffer(UUID tenantId, String borrowerToken, String productKey, int requestedAmount) throws Exception {
        String submitResponse = mockMvc.perform(
                        post("/api/v1/loans/applications")
                                .header("Authorization", "Bearer " + borrowerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loanProductKey": "%s",
                                          "requestedAmount": %d,
                                          "requestedTenureMonths": 36,
                                          "loanPurpose": "Flow 4 Test"
                                        }
                                        """.formatted(productKey, requestedAmount))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLICATION_SUBMITTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode submitJson = objectMapper.readTree(submitResponse);
        UUID applicationId = UUID.fromString(submitJson.path("applicationId").asText());
        UUID selectedOfferId = UUID.fromString(submitJson.path("offers").get(0).path("offerId").asText());

        mockMvc.perform(
                        post("/api/v1/loans/applications/{applicationId}/offers/select", applicationId)
                                .header("Authorization", "Bearer " + borrowerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "offerId": "%s"
                                        }
                                        """.formatted(selectedOfferId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFER_SELECTED"))
                .andExpect(jsonPath("$.loanState").value("OFFER_SELECTED"));

        LoanApplication app = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId).orElseThrow();
        assertThat(app.getSelectedOfferId()).isEqualTo(selectedOfferId);
        return applicationId;
    }

    private void uploadDocument(UUID applicationId, String borrowerToken, String documentType, String reference) throws Exception {
        mockMvc.perform(post("/api/v1/loans/{applicationId}/documents", applicationId)
                        .header("Authorization", "Bearer " + borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType": "%s",
                                  "documentReference": "%s"
                                }
                                """.formatted(documentType, reference)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    private UUID createTenant(String name, String domain) {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name(name)
                .domain(domain)
                .status(TenantStatus.ACTIVE)
                .mambuBranchId("branch_" + name.replace(" ", "_").toLowerCase())
                .mambuBranchEncodedKey("branch_key_" + name.replace(" ", "_").toLowerCase())
                .planTier("STANDARD")
                .build();
        return tenantRepository.save(tenant).getId();
    }

    private void createLoanProduct(UUID tenantId, String mambuProductKey) {
        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_" + mambuProductKey)
                .mambuProductKey(mambuProductKey)
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .productName("Personal Loan")
                        .minLoanAmount(BigDecimal.valueOf(10000))
                        .maxLoanAmount(BigDecimal.valueOf(1000000))
                        .minTenureMonths(6)
                        .maxTenureMonths(60)
                        .annualInterestRate(BigDecimal.valueOf(14.5))
                        .processingFeePercent(BigDecimal.valueOf(1.5))
                        .currency("INR")
                        .build())
                .build();
        loanProductRepository.save(loanProduct);
    }

    private UUID createBorrowerWithCredit(UUID tenantId, String email, String mambuClientKey) {
        Borrower borrower = Borrower.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Rahul")
                .lastName("Sharma")
                .email(email)
                .passwordHash("unused")
                .mobile("+919876543210")
                .dateOfBirth(java.time.LocalDate.of(1990, 5, 15))
                .gender("MALE")
                .panEncrypted("encrypted_pan")
                .aadhaarLastFour("1234")
                .address(BorrowerAddress.builder()
                        .line1("123 MG Road")
                        .city("Bengaluru")
                        .state("Karnataka")
                        .pincode("560001")
                        .build())
                .status(BorrowerStatus.CREDIT_PROFILE_COMPLETE)
                .mambuClientId("cli_" + UUID.randomUUID())
                .mambuClientKey(mambuClientKey)
                .build();

        Borrower saved = borrowerRepository.save(borrower);

        creditProfileRepository.save(CreditProfile.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(saved.getId())
                .creditScore(770)
                .creditBureau("CIBIL")
                .monthlyIncome(BigDecimal.valueOf(120000))
                .existingEmiObligations(BigDecimal.valueOf(20000))
                .employmentType("SALARIED")
                .employerName("Acme")
                .employmentMonths(48)
                .dtiRatio(BigDecimal.valueOf(16.67))
                .eligibilityCategory("PRIME")
                .build());

        return saved.getId();
    }

    private String borrowerToken(UUID tenantId, UUID borrowerId, String email) {
        return jwtTokenService.generateToken(
                email,
                java.util.List.of("BORROWER"),
                tenantId,
                Map.of("borrowerId", borrowerId.toString())
        ).token();
    }

    private String adminToken(UUID tenantId, String username) {
        return jwtTokenService.generateToken(
                username,
                java.util.List.of("TENANT_ADMIN"),
                tenantId,
                Map.of("userId", UUID.randomUUID().toString())
        ).token();
    }
}
