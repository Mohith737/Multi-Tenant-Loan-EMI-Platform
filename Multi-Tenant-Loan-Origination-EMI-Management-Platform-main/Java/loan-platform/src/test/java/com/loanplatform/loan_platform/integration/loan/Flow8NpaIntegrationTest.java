package com.loanplatform.loan_platform.integration.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerAddress;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.service.NpaService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Flow8NpaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private NpaService npaService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private BorrowerRepository borrowerRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private LoanInstallmentRepository loanInstallmentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private MambuPort mambuPort;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    @MockBean
    private ValueOperations<String, String> valueOperations;

    private UUID tenantA;
    private UUID tenantB;
    private String loanAccountTenantA;

    @BeforeEach
    void setUp() {
        resetDatabaseState();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(mambuPort.patchLoanState(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> MambuLoanAccountResponse.builder()
                        .id(invocation.getArgument(0, String.class))
                        .accountState(invocation.getArgument(1, String.class))
                        .build());

        tenantA = createTenant("tenant-a-flow8.test");
        tenantB = createTenant("tenant-b-flow8.test");

        UUID loanProductA = createLoanProduct(tenantA, "LP_FLOW8_A");
        UUID borrowerA = createBorrower(tenantA, "borrower.a@flow8.dev", "+918888888880");
        loanAccountTenantA = "LN_FLOW8_A_001";
        UUID applicationA = createLoanApplication(tenantA, borrowerA, loanProductA, loanAccountTenantA);
        createInstallment(tenantA, borrowerA, applicationA, loanAccountTenantA, 1, LocalDate.now().minusDays(95), LoanInstallmentStatus.OVERDUE);

        UUID loanProductB = createLoanProduct(tenantB, "LP_FLOW8_B");
        UUID borrowerB = createBorrower(tenantB, "borrower.b@flow8.dev", "+918888888881");
        UUID applicationB = createLoanApplication(tenantB, borrowerB, loanProductB, "LN_FLOW8_B_001");
        createInstallment(tenantB, borrowerB, applicationB, "LN_FLOW8_B_001", 1, LocalDate.now().minusDays(5), LoanInstallmentStatus.PENDING);
    }

    @Test
    void npaFlagging_thenAdminList_returnsTenantNpaLoan() throws Exception {
        npaService.flagNpaLoans(LocalDate.now());

        mockMvc.perform(get("/api/v1/admin/npa")
                        .header("Authorization", "Bearer " + adminToken(tenantA, "tenant_admin_a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.npaloans[0].loanAccountId").value(loanAccountTenantA))
                .andExpect(jsonPath("$.npaloans[0].mambuState").value("NON_PERFORMING"))
                .andExpect(jsonPath("$.npaloans[0].recoveryStage").value("DAY_1_REMINDER"));
    }

    @Test
    void overrideNpaLoan_afterFlagging_clearsRecordFromActivePortfolio() throws Exception {
        npaService.flagNpaLoans(LocalDate.now());

        mockMvc.perform(post("/api/v1/admin/npa/{loanAccountId}/override", loanAccountTenantA)
                        .header("Authorization", "Bearer " + adminToken(tenantA, "tenant_admin_a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrideReason": "Settlement agreed",
                                  "adminNote": "Borrower paid lump sum"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanAccountId").value(loanAccountTenantA))
                .andExpect(jsonPath("$.npaLockCleared").value(true));

        mockMvc.perform(get("/api/v1/admin/npa")
                        .header("Authorization", "Bearer " + adminToken(tenantA, "tenant_admin_a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void tenantIsolation_tenantBCannotSeeTenantANpaRecords() throws Exception {
        npaService.flagNpaLoans(LocalDate.now());

        mockMvc.perform(get("/api/v1/admin/npa")
                        .header("Authorization", "Bearer " + adminToken(tenantB, "tenant_admin_b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    private UUID createTenant(String domain) {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Tenant Flow8 " + domain)
                .domain(domain)
                .status(TenantStatus.ACTIVE)
                .mambuBranchId("branch_" + domain)
                .mambuBranchEncodedKey("branch_key_" + domain)
                .stripeAccountId("acct_" + domain.replace('.', '_'))
                .planTier("STANDARD")
                .build();
        return tenantRepository.save(tenant).getId();
    }

    private UUID createLoanProduct(UUID tenantId, String productKey) {
        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductId("LP_" + productKey)
                .mambuProductKey(productKey)
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .productName("Flow8 Product")
                        .minLoanAmount(new BigDecimal("10000"))
                        .maxLoanAmount(new BigDecimal("2000000"))
                        .minTenureMonths(6)
                        .maxTenureMonths(84)
                        .annualInterestRate(new BigDecimal("14.5"))
                        .processingFeePercent(new BigDecimal("1.5"))
                        .currency("INR")
                        .build())
                .build();
        return loanProductRepository.save(loanProduct).getId();
    }

    private UUID createBorrower(UUID tenantId, String email, String mobile) {
        Borrower borrower = Borrower.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Flow8")
                .lastName("Borrower")
                .email(email)
                .passwordHash("unused")
                .mobile(mobile)
                .dateOfBirth(LocalDate.of(1992, 4, 14))
                .gender("MALE")
                .panEncrypted("enc_pan_" + UUID.randomUUID())
                .aadhaarLastFour("1234")
                .address(BorrowerAddress.builder()
                        .line1("123 Main St")
                        .city("Bengaluru")
                        .state("Karnataka")
                        .pincode("560001")
                        .build())
                .status(BorrowerStatus.CREDIT_PROFILE_COMPLETE)
                .mambuClientId("cli_" + UUID.randomUUID())
                .mambuClientKey("enc_cli_" + UUID.randomUUID())
                .build();
        return borrowerRepository.save(borrower).getId();
    }

    private UUID createLoanApplication(UUID tenantId, UUID borrowerId, UUID loanProductId, String mambuLoanId) {
        LoanApplication application = LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanProductId(loanProductId)
                .loanProductKey("LP_FLOW8")
                .requestedAmount(new BigDecimal("400000"))
                .requestedTenureMonths(24)
                .loanPurpose("WORKING_CAPITAL")
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .loanState(LoanLifecycleState.ACTIVE_REPAYMENT)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(742)
                .dtiRatioSnapshot(new BigDecimal("23.10"))
                .monthlyIncomeSnapshot(new BigDecimal("100000"))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(new BigDecimal("500000"))
                .offersExpiresAt(Instant.now().plusSeconds(3600))
                .submittedAt(Instant.now())
                .mambuLoanId(mambuLoanId)
                .build();
        return loanApplicationRepository.save(application).getId();
    }

    private UUID createInstallment(
            UUID tenantId,
            UUID borrowerId,
            UUID applicationId,
            String mambuLoanId,
            int installmentNumber,
            LocalDate dueDate,
            LoanInstallmentStatus status
    ) {
        LoanInstallment installment = LoanInstallment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .mambuLoanId(mambuLoanId)
                .installmentNumber(installmentNumber)
                .dueDate(dueDate)
                .principalAmount(new BigDecimal("9000.00"))
                .interestAmount(new BigDecimal("1200.00"))
                .totalDue(new BigDecimal("10200.00"))
                .status(status)
                .retryCount(0)
                .mambuSyncPending(false)
                .waived(false)
                .build();
        return loanInstallmentRepository.save(installment).getId();
    }

    private String adminToken(UUID tenantId, String username) {
        return jwtTokenService.generateToken(
                username,
                List.of("TENANT_ADMIN"),
                tenantId
        ).token();
    }

    private void resetDatabaseState() {
        DatabaseCleanupHelper.clearDomainTables(jdbcTemplate);
    }
}
