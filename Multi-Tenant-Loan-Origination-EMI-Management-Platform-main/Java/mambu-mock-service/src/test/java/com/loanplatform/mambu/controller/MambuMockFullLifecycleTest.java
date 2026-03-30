package com.loanplatform.mambu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full end-to-end integration test of Mambu Mock Service
 * Tests the complete loan lifecycle:
 * Branch → Product → Client → Loan → Approve → Disburse → Schedule → Repayment → NPA
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MambuMockFullLifecycleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Shared state across ordered tests
    static String branchEncodedKey;
    static String branchId = "branch_test_001";
    static String productEncodedKey;
    static String productId = "LP_TEST_001";
    static String clientEncodedKey;
    static String clientId;
    static String loanEncodedKey;
    static String loanId;
    static String disbursementTxnId;

    // =========================================================
    // FLOW 1 — BRANCH CREATION
    // =========================================================

    @Test @Order(1)
    @DisplayName("POST /api/v2/branches — Create branch")
    void shouldCreateBranch() throws Exception {
        String body = """
            {
              "id": "branch_test_001",
              "name": "Test Finance Branch",
              "state": "ACTIVE",
              "emailAddress": "admin@testfinance.com",
              "phoneNumber": "+919876543210",
              "address": { "country": "IN", "city": "Mumbai" }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v2/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encodedKey").isNotEmpty())
                .andExpect(jsonPath("$.id").value("branch_test_001"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        branchEncodedKey = (String) response.get("encodedKey");
        assertThat(branchEncodedKey).isNotBlank().hasSize(24);
    }

    @Test @Order(2)
    @DisplayName("GET /api/v2/branches/{id} — Get branch")
    void shouldGetBranch() throws Exception {
        mockMvc.perform(get("/api/v2/branches/{id}", branchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(branchId))
                .andExpect(jsonPath("$.name").value("Test Finance Branch"));
    }

    @Test @Order(3)
    @DisplayName("POST /api/v2/branches — Duplicate branch ID returns 400")
    void shouldRejectDuplicateBranch() throws Exception {
        String body = """
            { "id": "branch_test_001", "name": "Duplicate Branch" }
            """;
        mockMvc.perform(post("/api/v2/branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // FLOW 2 — LOAN PRODUCT CREATION
    // =========================================================

    @Test @Order(4)
    @DisplayName("POST /api/v2/loanproducts — Create loan product")
    void shouldCreateLoanProduct() throws Exception {
        String body = String.format("""
            {
              "id": "LP_TEST_001",
              "name": "Test Personal Loan",
              "type": "FIXED_TERM_LOAN",
              "state": "ACTIVE",
              "forBranchKey": "%s",
              "loanAmountSettings": {
                "minAmount": { "value": 10000 },
                "maxAmount": { "value": 1000000 },
                "defaultAmount": { "value": 100000 }
              },
              "interestSettings": {
                "defaultInterestRate": { "value": 14.5 },
                "interestCalculationMethod": "DECLINING_BALANCE",
                "interestChargeFrequency": "ANNUALIZED"
              },
              "scheduleSettings": {
                "defaultRepaymentPeriodCount": 12,
                "defaultRepaymentPeriodUnit": "MONTHS"
              }
            }
            """, branchEncodedKey);

        MvcResult result = mockMvc.perform(post("/api/v2/loanproducts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encodedKey").isNotEmpty())
                .andExpect(jsonPath("$.id").value("LP_TEST_001"))
                .andExpect(jsonPath("$.interestSettings.defaultInterestRate.value").value(14.5))
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        productEncodedKey = (String) response.get("encodedKey");
        assertThat(productEncodedKey).isNotBlank();
    }

    // =========================================================
    // FLOW 3 — CLIENT CREATION
    // =========================================================

    @Test @Order(5)
    @DisplayName("POST /api/v2/clients — Create Mambu client")
    void shouldCreateClient() throws Exception {
        String body = String.format("""
            {
              "firstName": "Rahul",
              "lastName": "Sharma",
              "emailAddress": "rahul@test.com",
              "mobilePhone": "+919876543210",
              "birthDate": "1990-05-15",
              "gender": "MALE",
              "assignedBranchKey": "%s",
              "idDocuments": [
                { "documentType": "PAN", "documentId": "ABCDE1234F", "issuingAuthority": "IT Dept" }
              ],
              "addresses": [
                { "line1": "123 MG Road", "city": "Bengaluru", "region": "Karnataka", "postcode": "560001", "country": "IN", "indexInList": 0 }
              ]
            }
            """, branchEncodedKey);

        MvcResult result = mockMvc.perform(post("/api/v2/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.encodedKey").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Rahul"))
                .andExpect(jsonPath("$.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.idDocuments[0].documentType").value("PAN"))
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        clientEncodedKey = (String) response.get("encodedKey");
        clientId = (String) response.get("id");
        assertThat(clientEncodedKey).isNotBlank();
    }

    // =========================================================
    // FLOW 4 — LOAN SIMULATION
    // =========================================================

    @Test @Order(6)
    @DisplayName("POST /api/v2/loans:simulate — Simulate loan EMI")
    void shouldSimulateLoan() throws Exception {
        String body = String.format("""
            {
              "loanAmount": { "value": 500000 },
              "interestRate": { "value": 14.5 },
              "repaymentInstallments": 36,
              "loanProductTypeKey": "%s",
              "clientKey": "%s",
              "disbursementDetails": { "expectedDisbursementDate": "2024-02-01" }
            }
            """, productEncodedKey, clientEncodedKey);

        mockMvc.perform(post("/api/v2/loans:simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodicPayment").isNumber())
                .andExpect(jsonPath("$.totalInterestCharged").isNumber())
                .andExpect(jsonPath("$.repaymentSchedule.installments").isArray())
                .andExpect(jsonPath("$.repaymentSchedule.installments.length()").value(36))
                .andExpect(jsonPath("$.repaymentSchedule.installments[0].number").value(1))
                .andExpect(jsonPath("$.repaymentSchedule.installments[0].principal.amount.value").isNumber());
    }

    // =========================================================
    // FLOW 5 — LOAN ACCOUNT CREATION
    // =========================================================

    @Test @Order(7)
    @DisplayName("POST /api/v2/loans — Create loan account")
    void shouldCreateLoanAccount() throws Exception {
        String body = String.format("""
            {
              "clientKey": "%s",
              "productTypeKey": "%s",
              "assignedBranchKey": "%s",
              "loanAmount": { "value": 500000 },
              "interestRate": { "value": 14.5 },
              "repaymentInstallments": 36
            }
            """, clientEncodedKey, productEncodedKey, branchEncodedKey);

        MvcResult result = mockMvc.perform(post("/api/v2/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountState").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.loanAmount.value").value(500000))
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        loanEncodedKey = (String) response.get("encodedKey");
        loanId = (String) response.get("id");
        assertThat(loanId).isNotBlank();
    }

    // =========================================================
    // FLOW 6 — LOAN APPROVAL
    // =========================================================

    @Test @Order(8)
    @DisplayName("POST /api/v2/loans/{id}/approve — Approve loan")
    void shouldApproveLoan() throws Exception {
        String body = """
            { "notes": "Docs verified. Approved.", "date": "2024-01-15" }
            """;

        mockMvc.perform(post("/api/v2/loans/{id}/approve", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("APPROVED"))
                .andExpect(jsonPath("$.approvedDate").isNotEmpty());
    }

    @Test @Order(9)
    @DisplayName("POST /api/v2/loans/{id}/approve — Cannot approve already approved loan")
    void shouldRejectDoubleApproval() throws Exception {
        mockMvc.perform(post("/api/v2/loans/{id}/approve", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // FLOW 7 — DISBURSEMENT
    // =========================================================

    @Test @Order(10)
    @DisplayName("POST /api/v2/loans/{id}/disbursement — Disburse loan and generate schedule")
    void shouldDisburseLoan() throws Exception {
        String body = """
            {
              "notes": "Disbursed to savings account",
              "disbursementDate": "2024-02-01",
              "firstRepaymentDate": "2024-03-01",
              "externalId": "DISB-loan-001-20240201",
              "transactionDetails": { "transactionChannelId": "BANK_TRANSFER" }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v2/loans/{id}/disbursement", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DISBURSEMENT"))
                .andExpect(jsonPath("$.amount").isNumber())
                .andExpect(jsonPath("$.parentAccountId").value(loanId))
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        disbursementTxnId = (String) response.get("id");
        assertThat(disbursementTxnId).isNotBlank();
    }

    @Test @Order(11)
    @DisplayName("POST /api/v2/loans/{id}/disbursement — Duplicate externalId returns same transaction (idempotency)")
    void shouldHandleDuplicateDisbursementIdempotently() throws Exception {
        String body = """
            {
              "notes": "Duplicate disbursement attempt",
              "disbursementDate": "2024-02-01",
              "firstRepaymentDate": "2024-03-01",
              "externalId": "DISB-loan-001-20240201",
              "transactionDetails": { "transactionChannelId": "BANK_TRANSFER" }
            }
            """;

        mockMvc.perform(post("/api/v2/loans/{id}/disbursement", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(disbursementTxnId))
                .andExpect(jsonPath("$.externalId").value("DISB-loan-001-20240201"));
    }

    // =========================================================
    // FLOW 8 — GET SCHEDULE
    // =========================================================

    @Test @Order(12)
    @DisplayName("GET /api/v2/loans/{id}/schedule — Get repayment schedule")
    void shouldGetSchedule() throws Exception {
        mockMvc.perform(get("/api/v2/loans/{id}/schedule", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments").isArray())
                .andExpect(jsonPath("$.installments.length()").value(36))
                .andExpect(jsonPath("$.installments[0].number").value(1))
                .andExpect(jsonPath("$.installments[0].state").value("PENDING"))
                .andExpect(jsonPath("$.installments[0].principal.amount.value").isNumber())
                .andExpect(jsonPath("$.installments[0].interest.amount.value").isNumber());
    }

    // =========================================================
    // FLOW 9 — REPAYMENT
    // =========================================================

    @Test @Order(13)
    @DisplayName("POST /api/v2/loans/{id}/repayments — Post EMI repayment")
    void shouldPostRepayment() throws Exception {
        String body = """
            {
              "amount": 17217.77,
              "date": "2024-03-01",
              "notes": "EMI 1 via Stripe",
              "externalId": "EMI:loan_001:1:tenant_test",
              "transactionDetails": { "transactionChannelId": "ONLINE_PAYMENT" }
            }
            """;

        mockMvc.perform(post("/api/v2/loans/{id}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REPAYMENT"))
                .andExpect(jsonPath("$.amount").value(17217.77))
                .andExpect(jsonPath("$.externalId").value("EMI:loan_001:1:tenant_test"));
    }

    @Test @Order(14)
    @DisplayName("POST /api/v2/loans/{id}/repayments — Duplicate externalId returns same transaction (idempotency)")
    void shouldHandleDuplicateRepaymentIdempotently() throws Exception {
        String body = """
            {
              "amount": 17217.77,
              "date": "2024-03-01",
              "notes": "Duplicate EMI attempt",
              "externalId": "EMI:loan_001:1:tenant_test"
            }
            """;

        // Should return 201 with the same existing transaction (not a new one)
        mockMvc.perform(post("/api/v2/loans/{id}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value("EMI:loan_001:1:tenant_test"));
    }

    @Test @Order(15)
    @DisplayName("GET /api/v2/loans/{id}/schedule — Installment 1 is now PAID")
    void shouldShowInstallment1AsPaid() throws Exception {
        mockMvc.perform(get("/api/v2/loans/{id}/schedule", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments[0].state").value("PAID"))
                .andExpect(jsonPath("$.installments[0].lastPaidDate").isNotEmpty())
                .andExpect(jsonPath("$.installments[1].state").value("PENDING"));
    }

    // =========================================================
    // FLOW 10 — EARLY REPAYMENT PREVIEW
    // =========================================================

    @Test @Order(16)
    @DisplayName("GET /api/v2/loans/{id}/preview-early-repayment — Foreclosure calculation")
    void shouldPreviewEarlyRepayment() throws Exception {
        mockMvc.perform(get("/api/v2/loans/{id}/preview-early-repayment", loanId)
                        .param("paymentDate", "2024-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingPrincipal.value").isNumber())
                .andExpect(jsonPath("$.prepaymentPenalty.value").isNumber())
                .andExpect(jsonPath("$.totalSettlementAmount.value").isNumber());
    }

    // =========================================================
    // FLOW 11 — NPA FLAGGING
    // =========================================================

    @Test @Order(17)
    @DisplayName("PATCH /api/v2/loans/{id} — Flag loan as NON_PERFORMING (NPA)")
    void shouldFlagLoanAsNpa() throws Exception {
        String body = """
            {
              "loanState": "NON_PERFORMING",
              "notes": "90+ DPD flagged by batch job"
            }
            """;

        mockMvc.perform(patch("/api/v2/loans/{id}", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("NON_PERFORMING"));
    }

    @Test @Order(18)
    @DisplayName("PUT /api/v2/loans/{id} — Compatibility alias updates loan state")
    void shouldUpdateLoanStateUsingPutCompatibilityAlias() throws Exception {
        String body = """
            {
              "loanState": "ACTIVE",
              "notes": "Rollback after transfer failure"
            }
            """;

        mockMvc.perform(put("/api/v2/loans/{id}", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountState").value("ACTIVE"));
    }

    // =========================================================
    // FLOW 12 — PORTFOLIO REPORTING
    // =========================================================

    @Test @Order(19)
    @DisplayName("GET /api/v2/loans — Portfolio query by branch")
    void shouldQueryPortfolio() throws Exception {
        mockMvc.perform(get("/api/v2/loans")
                        .param("branchId", branchEncodedKey)
                        .param("limit", "100")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loans").isArray())
                .andExpect(jsonPath("$.pagingDetails.totalCount").isNumber());
    }

    @Test @Order(20)
    @DisplayName("GET /api/v2/loans/{id} — Not found returns 404")
    void shouldReturn404ForUnknownLoan() throws Exception {
        mockMvc.perform(get("/api/v2/loans/{id}", "nonexistent_loan_id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
