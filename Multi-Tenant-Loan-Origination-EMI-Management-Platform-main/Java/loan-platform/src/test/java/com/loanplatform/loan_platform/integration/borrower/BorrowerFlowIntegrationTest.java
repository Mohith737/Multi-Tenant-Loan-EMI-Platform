package com.loanplatform.loan_platform.integration.borrower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.KycDocumentRepository;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BorrowerFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private KycDocumentRepository kycDocumentRepository;

    @Autowired
    private CreditProfileRepository creditProfileRepository;

    @MockBean
    private MambuPort mambuPort;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() {
        creditProfileRepository.deleteAll();
        kycDocumentRepository.deleteAll();
        borrowerRepository.deleteAll();
        loanProductRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantA = createActiveTenant("Tenant A", "tenant-a.example.com", "branch_key_a");
        tenantB = createActiveTenant("Tenant B", "tenant-b.example.com", "branch_key_b");
        createLoanProduct(tenantA, "Tenant A Personal Loan");
        createLoanProduct(tenantB, "Tenant B Gold Loan");

        when(mambuPort.createClient(any(MambuClientCreateRequest.class)))
                .thenReturn(MambuClientResponse.builder().id("cli_001").encodedKey("enc_cli_001").build());
    }

    @Test
    void fullHappyPath_flow2() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loanProducts[0].productName").exists());

        String registerResponse = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest(tenantA, "rahul+happy@example.com"))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String borrowerJwt = objectMapper.readTree(registerResponse).path("accessToken").asText();
        String adminJwt = loginTenantAdmin(tenantA);

        String kycResponse = mockMvc.perform(
                        post("/api/v1/kyc/submit")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(kycSubmissionRequest(true))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submittedDocs.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode docs = objectMapper.readTree(kycResponse).path("submittedDocs");
        for (JsonNode doc : docs) {
            UUID docId = UUID.fromString(doc.path("kycDocumentId").asText());
            mockMvc.perform(
                            put("/api/v1/admin/kyc/{kycDocumentId}/verify", docId)
                                    .header("Authorization", "Bearer " + adminJwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "decision": "VERIFIED"
                                            }
                                            """)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VERIFIED"));
        }

        mockMvc.perform(
                        post("/api/v1/credit-profile")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "creditScore": 770,
                                          "creditBureau": "CIBIL",
                                          "monthlyIncome": 120000,
                                          "existingEmiObligations": 25000,
                                          "employmentType": "SALARIED",
                                          "employerName": "Acme Corp",
                                          "employmentMonths": 60
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eligibilityCategory").value("PRIME"));

        mockMvc.perform(
                        get("/api/v1/borrowers/me/sync-status")
                                .header("Authorization", "Bearer " + borrowerJwt)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowerStatus").value("CREDIT_PROFILE_COMPLETE"))
                .andExpect(jsonPath("$.mambuClientCreated").value(true))
                .andExpect(jsonPath("$.mambuSyncStatus").value("SYNCED"))
                .andExpect(jsonPath("$.mambuClientId").value("cli_001"));

        ArgumentCaptor<MambuClientCreateRequest> captor = ArgumentCaptor.forClass(MambuClientCreateRequest.class);
        verify(mambuPort, times(1)).createClient(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo("Rahul");
        assertThat(captor.getValue().getIdDocuments()).hasSize(1);
        assertThat(captor.getValue().getIdDocuments().getFirst().getDocumentType()).isEqualTo("PAN");
    }

    @Test
    void register_invalidTenant_returns404() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest(UUID.randomUUID(), "rahul+invalid-tenant@example.com"))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_FOUND"));
    }

    @Test
    void submitKyc_missingPan_returns400() throws Exception {
        String registerResponse = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest(tenantA, "rahul+missing-pan@example.com"))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String borrowerJwt = objectMapper.readTree(registerResponse).path("accessToken").asText();

        mockMvc.perform(
                        post("/api/v1/kyc/submit")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "documents": [
                                            {
                                              "documentType": "AADHAAR",
                                              "documentNumber": "123412341234",
                                              "documentReference": "s3://kyc/aadhaar.pdf"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitKyc_sameDocumentNumberAcrossPanAndAadhaar_returns400() throws Exception {
        String registerResponse = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest(tenantA, "rahul+duplicate-doc@example.com"))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String borrowerJwt = objectMapper.readTree(registerResponse).path("accessToken").asText();

        mockMvc.perform(
                        post("/api/v1/kyc/submit")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "documents": [
                                            {
                                              "documentType": "PAN_CARD",
                                              "documentNumber": "ABCDE1234F",
                                              "documentReference": "s3://kyc/pan.pdf"
                                            },
                                            {
                                              "documentType": "AADHAAR",
                                              "documentNumber": "ABCDE1234F",
                                              "documentReference": "s3://kyc/aadhaar.pdf"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void adminTenantIsolationViolation_returns403() throws Exception {
        String registerResponse = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest(tenantA, "rahul+isolation@example.com"))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String borrowerJwt = objectMapper.readTree(registerResponse).path("accessToken").asText();
        String tenantBAdminJwt = loginTenantAdmin(tenantB);

        String kycResponse = mockMvc.perform(
                        post("/api/v1/kyc/submit")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(kycSubmissionRequest(true))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID docId = UUID.fromString(objectMapper.readTree(kycResponse).path("submittedDocs").get(0).path("kycDocumentId").asText());

        mockMvc.perform(
                        put("/api/v1/admin/kyc/{kycDocumentId}/verify", docId)
                                .header("Authorization", "Bearer " + tenantBAdminJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "decision": "VERIFIED"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TENANT_ISOLATION_VIOLATION"));
    }

    @Test
    void creditProfile_beforeKycVerified_returns422_andMambuFailureKeepsStatusComplete() throws Exception {
        String registerResponse = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest(tenantA, "rahul+blocked@example.com"))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String borrowerJwt = objectMapper.readTree(registerResponse).path("accessToken").asText();

        mockMvc.perform(
                        post("/api/v1/credit-profile")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(creditProfileRequest(730, 100000, 20000))
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("KYC_NOT_VERIFIED"));

        String adminJwt = loginTenantAdmin(tenantA);
        String kycResponse = mockMvc.perform(
                        post("/api/v1/kyc/submit")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(kycSubmissionRequest(true))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode docs = objectMapper.readTree(kycResponse).path("submittedDocs");
        for (JsonNode doc : docs) {
            UUID docId = UUID.fromString(doc.path("kycDocumentId").asText());
            mockMvc.perform(
                            put("/api/v1/admin/kyc/{kycDocumentId}/verify", docId)
                                    .header("Authorization", "Bearer " + adminJwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "decision": "VERIFIED"
                                            }
                                            """)
                    )
                    .andExpect(status().isOk());
        }

        when(mambuPort.createClient(any(MambuClientCreateRequest.class))).thenThrow(new RuntimeException("mambu failure"));

        mockMvc.perform(
                        post("/api/v1/credit-profile")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(creditProfileRequest(730, 100000, 20000))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/borrowers/me/sync-status")
                                .header("Authorization", "Bearer " + borrowerJwt)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowerStatus").value("CREDIT_PROFILE_COMPLETE"))
                .andExpect(jsonPath("$.mambuClientCreated").value(false))
                .andExpect(jsonPath("$.mambuSyncStatus").value("PENDING_RETRY"))
                .andExpect(jsonPath("$.mambuClientId").isEmpty());

        UUID borrowerId = UUID.fromString(SignedJWT.parse(borrowerJwt).getJWTClaimsSet().getStringClaim("borrowerId"));
        Borrower borrower = borrowerRepository.findById(borrowerId).orElseThrow();
        assertThat(borrower.getStatus()).isEqualTo(BorrowerStatus.CREDIT_PROFILE_COMPLETE);
        assertThat(borrower.getMambuClientId()).isNull();
    }

    @Test
    void creditProfile_secondSubmission_returns409Conflict() throws Exception {
        String registerResponse = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest(tenantA, "rahul+repeat-credit@example.com"))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String borrowerJwt = objectMapper.readTree(registerResponse).path("accessToken").asText();
        String adminJwt = loginTenantAdmin(tenantA);

        String kycResponse = mockMvc.perform(
                        post("/api/v1/kyc/submit")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(kycSubmissionRequest(true))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode docs = objectMapper.readTree(kycResponse).path("submittedDocs");
        for (JsonNode doc : docs) {
            UUID docId = UUID.fromString(doc.path("kycDocumentId").asText());
            mockMvc.perform(
                            put("/api/v1/admin/kyc/{kycDocumentId}/verify", docId)
                                    .header("Authorization", "Bearer " + adminJwt)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "decision": "VERIFIED"
                                            }
                                            """)
                    )
                    .andExpect(status().isOk());
        }

        mockMvc.perform(
                        post("/api/v1/credit-profile")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(creditProfileRequest(760, 120000, 25000))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/credit-profile")
                                .header("Authorization", "Bearer " + borrowerJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(creditProfileRequest(780, 150000, 30000))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    private String registerRequest(UUID tenantId, String email) {
        return """
                {
                  "firstName": "Rahul",
                  "lastName": "Sharma",
                  "email": "%s",
                  "password": "REDACTED_SEE_ENV",
                  "mobile": "+919876543210",
                  "dateOfBirth": "1990-05-15",
                  "gender": "MALE",
                  "panNumber": "ABCDE1234F",
                  "aadhaarNumber": "987654321234",
                  "tenantId": "%s",
                  "address": {
                    "line1": "123 MG Road",
                    "city": "Bengaluru",
                    "state": "Karnataka",
                    "pincode": "560001"
                  }
                }
                """.formatted(email, tenantId);
    }

    private String kycSubmissionRequest(boolean withPan) {
        if (withPan) {
            return """
                    {
                      "documents": [
                        {
                          "documentType": "PAN_CARD",
                          "documentNumber": "ABCDE1234F",
                          "documentReference": "s3://kyc/pan.pdf"
                        },
                        {
                          "documentType": "AADHAAR",
                          "documentNumber": "123412341234",
                          "documentReference": "s3://kyc/aadhaar.pdf"
                        }
                      ]
                    }
                    """;
        }
        return """
                {
                  "documents": [
                    {
                      "documentType": "ADDRESS_PROOF",
                      "documentNumber": "ADDR-1",
                      "documentReference": "s3://kyc/address.pdf"
                    }
                  ]
                }
                """;
    }

    private String creditProfileRequest(int score, int monthlyIncome, int existingEmi) {
        return """
                {
                  "creditScore": %d,
                  "creditBureau": "CIBIL",
                  "monthlyIncome": %d,
                  "existingEmiObligations": %d,
                  "employmentType": "SALARIED",
                  "employerName": "Acme Corp",
                  "employmentMonths": 48
                }
                """.formatted(score, monthlyIncome, existingEmi);
    }

    private String loginTenantAdmin(UUID tenantId) throws Exception {
        String response = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "tenant_admin",
                                          "password": "REDACTED_SEE_ENV",
                                          "tenantId": "%s"
                                        }
                                        """.formatted(tenantId))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private UUID createActiveTenant(String name, String domain, String branchKey) {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name(name)
                .domain(domain)
                .status(TenantStatus.ACTIVE)
                .planTier("ENTERPRISE")
                .mambuBranchId("branch-" + name.toLowerCase().replace(" ", "-"))
                .mambuBranchEncodedKey(branchKey)
                .build();
        return tenantRepository.save(tenant).getId();
    }

    private void createLoanProduct(UUID tenantId, String name) {
        LoanProduct product = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP-" + UUID.randomUUID().toString().substring(0, 8))
                .mambuProductKey("LPK-" + UUID.randomUUID().toString().substring(0, 8))
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .productName(name)
                        .annualInterestRate(BigDecimal.valueOf(14.5))
                        .minLoanAmount(BigDecimal.valueOf(10000))
                        .maxLoanAmount(BigDecimal.valueOf(500000))
                        .minTenureMonths(6)
                        .maxTenureMonths(60)
                        .processingFeePercent(BigDecimal.valueOf(1.5))
                        .prepaymentPenaltyPercent(BigDecimal.valueOf(2.0))
                        .currency("INR")
                        .build())
                .build();
        loanProductRepository.save(product);
    }
}
