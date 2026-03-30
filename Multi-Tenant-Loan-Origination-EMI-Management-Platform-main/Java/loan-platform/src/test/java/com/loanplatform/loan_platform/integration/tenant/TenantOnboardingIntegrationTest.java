package com.loanplatform.loan_platform.integration.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductResponse;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.KycDocumentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import com.loanplatform.loan_platform.dto.request.TenantRegistrationRequest;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantOnboardingIntegrationTest {

    private static final String ADMIN_USER = "platform_admin";
    private static final String ADMIN_PASSWORD = "REDACTED_SEE_ENV";

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
    private CreditProfileRepository creditProfileRepository;

    @Autowired
    private KycDocumentRepository kycDocumentRepository;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanStateHistoryRepository loanStateHistoryRepository;

    @MockBean
    private MambuPort mambuPort;

    @BeforeEach
    void setUp() {
        loanStateHistoryRepository.deleteAll();
        loanOfferRepository.deleteAll();
        loanApplicationRepository.deleteAll();
        creditProfileRepository.deleteAll();
        kycDocumentRepository.deleteAll();
        borrowerRepository.deleteAll();
        loanProductRepository.deleteAll();
        tenantRepository.deleteAll();

        when(mambuPort.createBranch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(MambuBranchResponse.builder().id("branch_acme_001").encodedKey("branch_key_001").build());

        when(mambuPort.createLoanProduct(anyString(), any(LoanProductConfigRequest.class))).thenAnswer(invocation -> {
            LoanProductConfigRequest request = invocation.getArgument(1);
            if ("Acme Product Two".equals(request.getProductName())) {
                return MambuLoanProductResponse.builder().id("LP_ACME_002").encodedKey("loan_product_key_002").build();
            }
            return MambuLoanProductResponse.builder().id("LP_ACME_001").encodedKey("loan_product_key_001").build();
        });
    }

    @Test
    void postTenants_returnsCreated_persistsTenantAndCallsOnlyBranch() throws Exception {
        String requestBody = objectMapper.writeValueAsString(buildTenantRequest("acme-post.example.com"));

        String responseBody = mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Finance"))
                .andExpect(jsonPath("$.domain").value("acme-post.example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.mambuBranchId").value("branch_acme_001"))
                .andExpect(jsonPath("$.mambuBranchKey").value("branch_key_001"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        UUID tenantId = UUID.fromString(response.path("tenantId").asText());

        Optional<Tenant> storedTenant = tenantRepository.findById(tenantId);
        assertThat(storedTenant).isPresent();
        assertThat(storedTenant.get().getDomain()).isEqualTo("acme-post.example.com");
        assertThat(storedTenant.get().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(storedTenant.get().getMambuBranchId()).isEqualTo("branch_acme_001");
        assertThat(storedTenant.get().getMambuBranchEncodedKey()).isEqualTo("branch_key_001");

        verify(mambuPort, times(1)).createBranch(anyString(), eq("Acme Finance"), eq("admin@acme.example.com"), eq("+919999999999"));
        verify(mambuPort, never()).createLoanProduct(anyString(), any(LoanProductConfigRequest.class));
    }

    @Test
    void postTenants_withPlatformAdminBearer_returnsCreated() throws Exception {
        String platformAdminJwt = loginPlatformAdmin();

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .header("Authorization", "Bearer " + platformAdminJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(buildTenantRequest("acme-bearer.example.com")))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Finance"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void postLoanProduct_returnsCreated_persistsMappingAndCallsMambu() throws Exception {
        UUID tenantId = createTenantAndReturnId("acme-create-product.example.com");

        String requestBody = objectMapper.writeValueAsString(buildLoanProductConfig("Acme Product One"));

        mockMvc.perform(
                        post("/api/v1/loan-products")
                                .with(tenantAdminJwt(tenantId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.mambuProductId").value("LP_ACME_001"))
                .andExpect(jsonPath("$.mambuProductKey").value("loan_product_key_001"));

        assertThat(loanProductRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).hasSize(1);
        verify(mambuPort, times(1)).createLoanProduct(eq("branch_key_001"), any(LoanProductConfigRequest.class));
    }

    @Test
    void createMultipleLoanProducts_thenList_returnsBothMappings() throws Exception {
        UUID tenantId = createTenantAndReturnId("acme-list-product.example.com");

        mockMvc.perform(
                        post("/api/v1/loan-products")
                                .with(tenantAdminJwt(tenantId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(buildLoanProductConfig("Acme Product One")))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/loan-products")
                                .with(tenantAdminJwt(tenantId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(buildLoanProductConfig("Acme Product Two")))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/loan-products")
                                .with(tenantAdminJwt(tenantId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mambuProductId").value("LP_ACME_002"))
                .andExpect(jsonPath("$[1].mambuProductId").value("LP_ACME_001"));

        verify(mambuPort, times(2)).createLoanProduct(eq("branch_key_001"), any(LoanProductConfigRequest.class));
    }

    @Test
    void postLoanProduct_withPlatformAdmin_returnsForbiddenNotInternalError() throws Exception {
        createTenantAndReturnId("acme-access-denied.example.com");

        mockMvc.perform(
                        post("/api/v1/loan-products")
                                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(buildLoanProductConfig("Acme Product One")))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void postTenants_duplicateDomain_returnsConflict() throws Exception {
        String requestBody = objectMapper.writeValueAsString(buildTenantRequest("acme-dup.example.com"));

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));

        verify(mambuPort, times(1)).createBranch(anyString(), anyString(), anyString(), anyString());
    }

    private RequestPostProcessor tenantAdminJwt(UUID tenantId) {
        return jwt()
                .jwt(token -> token.claim("tenantId", tenantId.toString()).claim("roles", java.util.List.of("TENANT_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }

    private String loginPlatformAdmin() throws Exception {
        String response = mockMvc.perform(
                        post("/api/v1/auth/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "platform_admin",
                                          "password": "REDACTED_SEE_ENV"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private UUID createTenantAndReturnId(String domain) throws Exception {
        String createRequest = objectMapper.writeValueAsString(buildTenantRequest(domain));
        String createResponse = mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequest)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(createResponse).path("tenantId").asText());
    }

    private TenantRegistrationRequest buildTenantRequest(String domain) {
        return TenantRegistrationRequest.builder()
                .name("Acme Finance")
                .domain(domain)
                .adminEmail("admin@acme.example.com")
                .adminPhone("+919999999999")
                .planTier("ENTERPRISE")
                .build();
    }

    private LoanProductConfigRequest buildLoanProductConfig(String name) {
        return LoanProductConfigRequest.builder()
                .productName(name)
                .minLoanAmount(BigDecimal.valueOf(10_000))
                .maxLoanAmount(BigDecimal.valueOf(500_000))
                .minTenureMonths(6)
                .maxTenureMonths(48)
                .annualInterestRate(BigDecimal.valueOf(14.5))
                .processingFeePercent(BigDecimal.valueOf(1.5))
                .prepaymentPenaltyPercent(BigDecimal.valueOf(2.0))
                .currency("INR")
                .build();
    }
}
