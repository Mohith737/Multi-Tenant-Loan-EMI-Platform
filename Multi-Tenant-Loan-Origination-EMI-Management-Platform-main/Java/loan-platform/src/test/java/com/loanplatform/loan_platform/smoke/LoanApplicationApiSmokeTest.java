package com.loanplatform.loan_platform.smoke;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.CreditProfileRepository;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.security.JwtTokenService;
import com.loanplatform.loan_platform.testsupport.DatabaseCleanupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoanApplicationApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

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
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private MambuPort mambuPort;

    private UUID tenantId;
    private UUID borrowerId;

    @BeforeEach
    void setUp() {
        resetDatabaseState();

        tenantId = createTenant();
        createLoanProduct(tenantId);
        borrowerId = createBorrowerAndCredit(tenantId);

        when(mambuPort.simulateLoan(any())).thenReturn(MambuLoanSimulationResponse.builder()
                .periodicPayment(BigDecimal.valueOf(17217.77))
                .totalInterestCharged(BigDecimal.valueOf(119839.72))
                .annualPercentageRate(BigDecimal.valueOf(14.5))
                .totalAmountRepaid(BigDecimal.valueOf(619839.72))
                .build());
    }

    private void resetDatabaseState() {
        DatabaseCleanupHelper.clearDomainTables(jdbcTemplate);
    }

    @Test
    void submitLoanApplication_smoke_returnsCreatedWithOffers() throws Exception {
        String token = jwtTokenService.generateToken(
                "smoke.borrower@example.com",
                java.util.List.of("BORROWER"),
                tenantId,
                Map.of("borrowerId", borrowerId.toString())
        ).token();

        mockMvc.perform(
                        post("/api/v1/loans/applications")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loanProductKey": "LP_SMOKE_001",
                                          "requestedAmount": 500000,
                                          "requestedTenureMonths": 36,
                                          "loanPurpose": "HOME_RENOVATION",
                                          "requestedDisbursementDate": "2026-03-15"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLICATION_SUBMITTED"))
                .andExpect(jsonPath("$.offers.length()").value(3));
    }

    private UUID createTenant() {
        return tenantRepository.save(Tenant.builder()
                .id(UUID.randomUUID())
                .name("Smoke Tenant")
                .domain("smoke.loanos.dev")
                .status(TenantStatus.ACTIVE)
                .mambuBranchId("branch_smoke_001")
                .mambuBranchEncodedKey("branch_key_smoke_001")
                .planTier("STANDARD")
                .build()).getId();
    }

    private void createLoanProduct(UUID tenantId) {
        loanProductRepository.save(LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_SMOKE_001")
                .mambuProductKey("LP_SMOKE_001")
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .productName("Smoke Personal Loan")
                        .minLoanAmount(BigDecimal.valueOf(10000))
                        .maxLoanAmount(BigDecimal.valueOf(1000000))
                        .minTenureMonths(6)
                        .maxTenureMonths(60)
                        .annualInterestRate(BigDecimal.valueOf(14.5))
                        .processingFeePercent(BigDecimal.valueOf(1.5))
                        .currency("INR")
                        .build())
                .build());
    }

    private UUID createBorrowerAndCredit(UUID tenantId) {
        Borrower borrower = borrowerRepository.save(Borrower.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Smoke")
                .lastName("Borrower")
                .email("smoke.borrower@example.com")
                .passwordHash("unused")
                .mobile("+919876543211")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("MALE")
                .panEncrypted("enc")
                .aadhaarLastFour("0000")
                .address(BorrowerAddress.builder()
                        .line1("Line1")
                        .city("Bengaluru")
                        .state("Karnataka")
                        .pincode("560001")
                        .build())
                .status(BorrowerStatus.CREDIT_PROFILE_COMPLETE)
                .mambuClientId("cli_smoke_001")
                .mambuClientKey("cli_key_smoke_001")
                .build());

        creditProfileRepository.save(CreditProfile.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrower.getId())
                .creditScore(760)
                .creditBureau("CIBIL")
                .monthlyIncome(BigDecimal.valueOf(100000))
                .existingEmiObligations(BigDecimal.valueOf(15000))
                .employmentType("SALARIED")
                .employmentMonths(36)
                .dtiRatio(BigDecimal.valueOf(15.00))
                .eligibilityCategory("PRIME")
                .build());

        return borrower.getId();
    }
}
