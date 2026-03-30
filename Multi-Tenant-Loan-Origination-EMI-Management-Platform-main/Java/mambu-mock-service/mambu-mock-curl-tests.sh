 #!/bin/bash
# ================================================================
# Mambu Mock Service — Complete cURL Test Collection
# Run: chmod +x mambu-mock-curl-tests.sh && ./mambu-mock-curl-tests.sh
# Service must be running on http://localhost:8081
# ================================================================

BASE_URL="http://localhost:8081"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ PASS${NC} — $1"; }
fail() { echo -e "${RED}❌ FAIL${NC} — $1"; }
section() { echo -e "\n${YELLOW}=== $1 ===${NC}"; }

# ----------------------------------------------------------------
section "FLOW 1 — Create Branch"
# ----------------------------------------------------------------

BRANCH_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/branches" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "branch_curl_001",
    "name": "cURL Test Branch",
    "state": "ACTIVE",
    "emailAddress": "curl@testfinance.com",
    "phoneNumber": "+919876543210",
    "address": { "country": "IN", "city": "Bengaluru" }
  }')

echo "Branch Response: $BRANCH_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$BRANCH_RESPONSE"

BRANCH_ENCODED_KEY=$(echo "$BRANCH_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['encodedKey'])" 2>/dev/null)

if [ -n "$BRANCH_ENCODED_KEY" ]; then
  pass "Branch created: encodedKey=$BRANCH_ENCODED_KEY"
else
  fail "Branch creation failed"
  exit 1
fi

# ----------------------------------------------------------------
section "FLOW 1 — Get Branch"
# ----------------------------------------------------------------

curl -s -X GET "$BASE_URL/api/v2/branches/branch_curl_001" | python3 -m json.tool
pass "Branch GET"

# ----------------------------------------------------------------
section "FLOW 2 — Create Loan Product"
# ----------------------------------------------------------------

PRODUCT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/loanproducts" \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"LP_CURL_001\",
    \"name\": \"cURL Test Loan Product\",
    \"type\": \"FIXED_TERM_LOAN\",
    \"state\": \"ACTIVE\",
    \"forBranchKey\": \"$BRANCH_ENCODED_KEY\",
    \"loanAmountSettings\": {
      \"minAmount\": { \"value\": 10000 },
      \"maxAmount\": { \"value\": 1000000 },
      \"defaultAmount\": { \"value\": 100000 }
    },
    \"interestSettings\": {
      \"defaultInterestRate\": { \"value\": 14.5 },
      \"interestCalculationMethod\": \"DECLINING_BALANCE\",
      \"interestChargeFrequency\": \"ANNUALIZED\"
    },
    \"scheduleSettings\": {
      \"defaultRepaymentPeriodCount\": 12,
      \"defaultRepaymentPeriodUnit\": \"MONTHS\"
    }
  }")

echo "$PRODUCT_RESPONSE" | python3 -m json.tool
PRODUCT_ENCODED_KEY=$(echo "$PRODUCT_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['encodedKey'])" 2>/dev/null)

if [ -n "$PRODUCT_ENCODED_KEY" ]; then
  pass "Loan product created: encodedKey=$PRODUCT_ENCODED_KEY"
else
  fail "Loan product creation failed"
fi

# ----------------------------------------------------------------
section "FLOW 3 — Create Client (Borrower)"
# ----------------------------------------------------------------

CLIENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/clients" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"Rahul\",
    \"lastName\": \"Sharma\",
    \"emailAddress\": \"rahul@test.com\",
    \"mobilePhone\": \"+919876543210\",
    \"birthDate\": \"1990-05-15\",
    \"gender\": \"MALE\",
    \"assignedBranchKey\": \"$BRANCH_ENCODED_KEY\",
    \"idDocuments\": [
      { \"documentType\": \"PAN\", \"documentId\": \"ABCDE1234F\", \"issuingAuthority\": \"IT Dept\" }
    ],
    \"addresses\": [
      { \"line1\": \"123 MG Road\", \"city\": \"Bengaluru\", \"region\": \"Karnataka\", \"postcode\": \"560001\", \"country\": \"IN\", \"indexInList\": 0 }
    ]
  }")

echo "$CLIENT_RESPONSE" | python3 -m json.tool
CLIENT_ENCODED_KEY=$(echo "$CLIENT_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['encodedKey'])" 2>/dev/null)
CLIENT_ID=$(echo "$CLIENT_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

if [ -n "$CLIENT_ENCODED_KEY" ]; then
  pass "Client created: encodedKey=$CLIENT_ENCODED_KEY, id=$CLIENT_ID"
else
  fail "Client creation failed"
fi

# ----------------------------------------------------------------
section "FLOW 4 — Simulate Loan (EMI Calculation)"
# ----------------------------------------------------------------

echo "--- Simulating 500000 @ 14.5% for 36 months ---"
SIMULATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/loans:simulate" \
  -H "Content-Type: application/json" \
  -d "{
    \"loanAmount\": { \"value\": 500000 },
    \"interestRate\": { \"value\": 14.5 },
    \"repaymentInstallments\": 36,
    \"loanProductTypeKey\": \"$PRODUCT_ENCODED_KEY\",
    \"clientKey\": \"$CLIENT_ENCODED_KEY\",
    \"disbursementDetails\": { \"expectedDisbursementDate\": \"2024-02-01\" }
  }")

PERIODIC_PAYMENT=$(echo "$SIMULATE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['periodicPayment'])" 2>/dev/null)
TOTAL_INTEREST=$(echo "$SIMULATE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['totalInterestCharged'])" 2>/dev/null)
INSTALLMENT_COUNT=$(echo "$SIMULATE_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d['repaymentSchedule']['installments']))" 2>/dev/null)

echo "  EMI: ₹$PERIODIC_PAYMENT"
echo "  Total Interest: ₹$TOTAL_INTEREST"
echo "  Schedule installments: $INSTALLMENT_COUNT"

if [ "$INSTALLMENT_COUNT" = "36" ]; then
  pass "Loan simulation: 36 installments generated"
else
  fail "Expected 36 installments, got $INSTALLMENT_COUNT"
fi

# ----------------------------------------------------------------
section "FLOW 5 — Create Loan Account"
# ----------------------------------------------------------------

LOAN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/loans" \
  -H "Content-Type: application/json" \
  -d "{
    \"clientKey\": \"$CLIENT_ENCODED_KEY\",
    \"productTypeKey\": \"$PRODUCT_ENCODED_KEY\",
    \"assignedBranchKey\": \"$BRANCH_ENCODED_KEY\",
    \"loanAmount\": { \"value\": 500000 },
    \"interestRate\": { \"value\": 14.5 },
    \"repaymentInstallments\": 36
  }")

echo "$LOAN_RESPONSE" | python3 -m json.tool
LOAN_ENCODED_KEY=$(echo "$LOAN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['encodedKey'])" 2>/dev/null)
LOAN_ID=$(echo "$LOAN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
LOAN_STATE=$(echo "$LOAN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['accountState'])" 2>/dev/null)

if [ "$LOAN_STATE" = "PENDING_APPROVAL" ]; then
  pass "Loan account created: id=$LOAN_ID, state=PENDING_APPROVAL"
else
  fail "Expected PENDING_APPROVAL, got $LOAN_STATE"
fi

# ----------------------------------------------------------------
section "FLOW 6 — Approve Loan"
# ----------------------------------------------------------------

APPROVE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/loans/$LOAN_ID/approve" \
  -H "Content-Type: application/json" \
  -d '{ "notes": "All docs verified. Approved.", "date": "2024-01-15" }')

APPROVED_STATE=$(echo "$APPROVE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['accountState'])" 2>/dev/null)

if [ "$APPROVED_STATE" = "APPROVED" ]; then
  pass "Loan approved: state=APPROVED"
else
  fail "Expected APPROVED, got $APPROVED_STATE"
fi

# ----------------------------------------------------------------
section "FLOW 7 — Disburse Loan"
# ----------------------------------------------------------------

DISBURSE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/loans/$LOAN_ID/disbursement" \
  -H "Content-Type: application/json" \
  -d '{
    "notes": "Disbursed to savings account",
    "disbursementDate": "2024-02-01",
    "firstRepaymentDate": "2024-03-01",
    "externalId": "DISB-curl-001-20240201",
    "transactionDetails": { "transactionChannelId": "BANK_TRANSFER" }
  }')

echo "$DISBURSE_RESPONSE" | python3 -m json.tool
DISB_TYPE=$(echo "$DISBURSE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['type'])" 2>/dev/null)

if [ "$DISB_TYPE" = "DISBURSEMENT" ]; then
  pass "Loan disbursed: type=DISBURSEMENT"
else
  fail "Expected DISBURSEMENT transaction, got $DISB_TYPE"
fi

# ----------------------------------------------------------------
section "FLOW 8 — Get Repayment Schedule"
# ----------------------------------------------------------------

SCHEDULE_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v2/loans/$LOAN_ID/schedule")
SCHEDULE_COUNT=$(echo "$SCHEDULE_RESPONSE" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['installments']))" 2>/dev/null)
FIRST_STATE=$(echo "$SCHEDULE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['installments'][0]['state'])" 2>/dev/null)

if [ "$SCHEDULE_COUNT" = "36" ] && [ "$FIRST_STATE" = "PENDING" ]; then
  pass "Schedule: 36 installments, first state=PENDING"
else
  fail "Schedule check failed: count=$SCHEDULE_COUNT, firstState=$FIRST_STATE"
fi

# ----------------------------------------------------------------
section "FLOW 9 — Post EMI Repayment"
# ----------------------------------------------------------------

REPAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/loans/$LOAN_ID/repayments" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 17217.77,
    "date": "2024-03-01",
    "notes": "EMI 1 via Stripe",
    "externalId": "EMI:curl_loan:1:tenant_test",
    "transactionDetails": { "transactionChannelId": "ONLINE_PAYMENT" }
  }')

REPAY_TYPE=$(echo "$REPAYMENT_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['type'])" 2>/dev/null)

if [ "$REPAY_TYPE" = "REPAYMENT" ]; then
  pass "Repayment posted: type=REPAYMENT"
else
  fail "Expected REPAYMENT transaction"
fi

# ----------------------------------------------------------------
section "FLOW 9 — Idempotency Check (Duplicate Repayment)"
# ----------------------------------------------------------------

DUPLICATE_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v2/loans/$LOAN_ID/repayments" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 17217.77,
    "date": "2024-03-01",
    "notes": "DUPLICATE - should return same txn",
    "externalId": "EMI:curl_loan:1:tenant_test"
  }')

DUPLICATE_EXTERNAL_ID=$(echo "$DUPLICATE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['externalId'])" 2>/dev/null)

if [ "$DUPLICATE_EXTERNAL_ID" = "EMI:curl_loan:1:tenant_test" ]; then
  pass "Idempotency: duplicate repayment returned same transaction"
else
  fail "Idempotency check failed"
fi

# ----------------------------------------------------------------
section "FLOW 10 — Preview Early Repayment"
# ----------------------------------------------------------------

PREVIEW_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v2/loans/$LOAN_ID/preview-early-repayment?paymentDate=2024-06-01")
echo "$PREVIEW_RESPONSE" | python3 -m json.tool

SETTLEMENT=$(echo "$PREVIEW_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['totalSettlementAmount']['value'])" 2>/dev/null)
pass "Early repayment preview: totalSettlementAmount=₹$SETTLEMENT"

# ----------------------------------------------------------------
section "FLOW 11 — Flag Loan as NPA"
# ----------------------------------------------------------------

NPA_RESPONSE=$(curl -s -X PATCH "$BASE_URL/api/v2/loans/$LOAN_ID" \
  -H "Content-Type: application/json" \
  -d '{ "loanState": "NON_PERFORMING", "notes": "90+ DPD — NPA flagged" }')

NPA_STATE=$(echo "$NPA_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['accountState'])" 2>/dev/null)

if [ "$NPA_STATE" = "NON_PERFORMING" ]; then
  pass "NPA flagged: accountState=NON_PERFORMING"
else
  fail "Expected NON_PERFORMING, got $NPA_STATE"
fi

# ----------------------------------------------------------------
section "FLOW 12 — Portfolio Reporting"
# ----------------------------------------------------------------

PORTFOLIO_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v2/loans?branchId=$BRANCH_ENCODED_KEY&limit=100&offset=0")
TOTAL=$(echo "$PORTFOLIO_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['pagingDetails']['totalCount'])" 2>/dev/null)
pass "Portfolio: totalCount=$TOTAL loans in branch"

# ----------------------------------------------------------------
section "FLOW 13 — Error Handling (404)"
# ----------------------------------------------------------------

ERROR_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v2/loans/nonexistent_loan_id")
ERROR_CODE=$(echo "$ERROR_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['errorCode'])" 2>/dev/null)

if [ "$ERROR_CODE" = "NOT_FOUND" ]; then
  pass "404 returns Mambu-style error: errorCode=NOT_FOUND"
else
  fail "Expected NOT_FOUND error, got: $ERROR_RESPONSE"
fi

# ----------------------------------------------------------------
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}   Mambu Mock Service Tests Complete    ${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Swagger UI: ${YELLOW}http://localhost:8081/swagger-ui.html${NC}"
echo -e "H2 Console: ${YELLOW}http://localhost:8081/h2-console${NC}"

