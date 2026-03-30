package com.loanplatform.loan_platform.integration.loan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.KycDocumentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanDocumentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.loan.repository.UnderwriterDecisionRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoanApplicationFlowIntegrationTest {

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

        tenantA = createTenant("Tenant A", "tenant-a.loanos.dev");
        tenantB = createTenant("Tenant B", "tenant-b.loanos.dev");

        createLoanProduct(tenantA, "LP_TENANT_A");
        createLoanProduct(tenantB, "LP_TENANT_B");

        borrowerA = createBorrowerWithCredit(tenantA, "rahul.a@example.com", "cli_key_a");
        borrowerB = createBorrowerWithCredit(tenantB, "rahul.b@example.com", "cli_key_b");

        when(mambuPort.simulateLoan(any())).thenAnswer(invocation -> {
            var req = invocation.getArgument(0, com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationRequest.class);
            int tenure = req.getRepaymentInstallments();
            BigDecimal emi = BigDecimal.valueOf(500000.0 / tenure + 2500);
            BigDecimal totalRepaid = emi.multiply(BigDecimal.valueOf(tenure));
            BigDecimal totalInterest = totalRepaid.subtract(BigDecimal.valueOf(500000));
            return MambuLoanSimulationResponse.builder()
                    .periodicPayment(emi.setScale(2, RoundingMode.HALF_UP))
                    .totalInterestCharged(totalInterest.setScale(2, RoundingMode.HALF_UP))
                    .annualPercentageRate(BigDecimal.valueOf(14.5))
                    .totalAmountRepaid(totalRepaid.setScale(2, RoundingMode.HALF_UP))
                    .build();
        });
    }

    @Test
    void submitAndSelectOffer_happyPath_success() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "rahul.a@example.com");

        String submitResponse = mockMvc.perform(
                        post("/api/v1/loans/applications")
                                .header("Authorization", "Bearer " + borrowerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(submitRequest("LP_TENANT_A", 500000, 36))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLICATION_SUBMITTED"))
                .andExpect(jsonPath("$.loanState").value("APPLICATION_SUBMITTED"))
                .andExpect(jsonPath("$.offers.length()").value(3))
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

        mockMvc.perform(
                        get("/api/v1/loans/applications/{applicationId}", applicationId)
                                .header("Authorization", "Bearer " + borrowerToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFER_SELECTED"));
    }

    @Test
    void submitApplication_duplicateActiveApplication_returnsConflict() throws Exception {
        String borrowerToken = borrowerToken(tenantA, borrowerA, "rahul.a@example.com");

        mockMvc.perform(
                        post("/api/v1/loans/applications")
                                .header("Authorization", "Bearer " + borrowerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(submitRequest("LP_TENANT_A", 500000, 36))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/loans/applications")
                                .header("Authorization", "Bearer " + borrowerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(submitRequest("LP_TENANT_A", 450000, 24))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_ACTIVE_LOAN"));
    }

    @Test
    void getOffers_crossTenantAccess_returnsNotFound() throws Exception {
        String tenantAToken = borrowerToken(tenantA, borrowerA, "rahul.a@example.com");
        String tenantBToken = borrowerToken(tenantB, borrowerB, "rahul.b@example.com");

        String submitResponse = mockMvc.perform(
                        post("/api/v1/loans/applications")
                                .header("Authorization", "Bearer " + tenantAToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(submitRequest("LP_TENANT_A", 500000, 36))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID applicationId = UUID.fromString(objectMapper.readTree(submitResponse).path("applicationId").asText());

        mockMvc.perform(
                        get("/api/v1/loans/applications/{applicationId}/offers", applicationId)
                                .header("Authorization", "Bearer " + tenantBToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOAN_APPLICATION_NOT_FOUND"));
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

    private String submitRequest(String loanProductKey, int requestedAmount, int tenureMonths) {
        return """
                {
                  "loanProductKey": "%s",
                  "requestedAmount": %d,
                  "requestedTenureMonths": %d,
                  "loanPurpose": "HOME_RENOVATION"
                }
                """.formatted(loanProductKey, requestedAmount, tenureMonths);
    }
}
