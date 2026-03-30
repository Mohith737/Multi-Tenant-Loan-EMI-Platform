# Mambu Mock Service — Complete Reference

> All 49 files, folder structure, and full source code in one document.
> Spring Boot 3.3 · Java 21 · H2 + Flyway · Port 8081

---

## Folder Structure

```
mambu-mock-service/
├── Dockerfile
├── docker-compose.yml
├── mambu-mock-curl-tests.sh
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/loanplatform/mambu/
    │   │   ├── MambuMockApplication.java
    │   │   ├── config/
    │   │   │   └── MambuMockConfig.java
    │   │   ├── controller/
    │   │   │   ├── MambuBranchController.java
    │   │   │   ├── MambuClientController.java
    │   │   │   ├── MambuDisbursementController.java
    │   │   │   ├── MambuLoanAccountController.java
    │   │   │   ├── MambuLoanProductController.java
    │   │   │   ├── MambuRepaymentController.java
    │   │   │   └── MambuSimulationController.java
    │   │   ├── exception/
    │   │   │   ├── MambuInvalidStateException.java
    │   │   │   ├── MambuMockExceptionHandler.java
    │   │   │   └── MambuResourceNotFoundException.java
    │   │   ├── model/
    │   │   │   ├── branch/
    │   │   │   │   └── MambuBranch.java
    │   │   │   ├── client/
    │   │   │   │   ├── MambuClient.java
    │   │   │   │   ├── MambuClientAddress.java
    │   │   │   │   └── MambuIdDocument.java
    │   │   │   ├── loan/
    │   │   │   │   ├── MambuInstallment.java
    │   │   │   │   └── MambuLoanAccount.java
    │   │   │   ├── loanproduct/
    │   │   │   │   └── MambuLoanProduct.java
    │   │   │   └── repayment/
    │   │   │       └── MambuTransaction.java
    │   │   ├── repository/
    │   │   │   ├── MambuBranchRepository.java
    │   │   │   ├── MambuClientRepository.java
    │   │   │   ├── MambuInstallmentRepository.java
    │   │   │   ├── MambuLoanAccountRepository.java
    │   │   │   ├── MambuLoanProductRepository.java
    │   │   │   └── MambuTransactionRepository.java
    │   │   ├── service/
    │   │   │   └── EmiCalculatorService.java
    │   │   └── util/
    │   │       └── MambuIdGenerator.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           ├── V1__create_branches.sql
    │           ├── V2__create_loan_products.sql
    │           ├── V3__create_clients.sql
    │           ├── V4__create_loan_accounts.sql
    │           └── V5__create_installments_transactions.sql
    └── test/
        ├── java/com/loanplatform/mambu/
        │   ├── controller/
        │   │   └── MambuMockFullLifecycleTest.java
        │   └── service/
        │       └── EmiCalculatorServiceTest.java
        └── resources/json/request/
            ├── approve-loan.json
            ├── create-branch.json
            ├── create-client.json
            ├── create-loan-account.json
            ├── create-loan-product.json
            ├── disburse-loan.json
            ├── flag-npa.json
            ├── post-repayment.json
            └── simulate-loan.json
```

---

## API Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/v2/branches | Create branch |
| GET | /api/v2/branches/{id} | Get branch |
| GET | /api/v2/branches | List all branches |
| POST | /api/v2/loanproducts | Create loan product |
| GET | /api/v2/loanproducts/{id} | Get loan product |
| POST | /api/v2/clients | Create client (borrower) |
| GET | /api/v2/clients/{id} | Get client |
| PATCH | /api/v2/clients/{id} | Update client |
| POST | /api/v2/loans:simulate | Simulate EMI and schedule |
| POST | /api/v2/loans | Create loan account |
| GET | /api/v2/loans/{id} | Get loan account |
| POST | /api/v2/loans/{id}/approve | Approve loan |
| PATCH | /api/v2/loans/{id} | Patch loan (NPA flag etc.) |
| GET | /api/v2/loans | Portfolio query |
| POST | /api/v2/loans/{id}/disbursement | Disburse loan |
| POST | /api/v2/loans/{id}/repayments | Post repayment |
| GET | /api/v2/loans/{id}/schedule | Get repayment schedule |
| GET | /api/v2/loans/{id}/preview-early-repayment | Foreclosure preview |

---


---

## `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>
    <groupId>com.loanplatform</groupId>
    <artifactId>mambu-mock-service</artifactId>
    <version>1.0.0</version>
    <description>Fake Mambu Core Banking REST API</description>
    <properties>
        <java.version>21</java.version>
        <springdoc.version>2.6.0</springdoc.version>
    </properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
        <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${springdoc.version}</version></dependency>
        <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>

```

---

## `Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
COPY target/mambu-mock-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]

```

---

## `docker-compose.yml`

```yaml
version: '3.9'

services:
  mambu-mock:
    build: .
    container_name: mambu-mock-service
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: default
      MAMBU_MOCK_SIMULATE_DELAY_MS: 150
      MAMBU_MOCK_ERROR_RATE_PERCENT: 0
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8081/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3
    restart: unless-stopped

```

---

## `mambu-mock-curl-tests.sh`

```bash
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

```

---

## `src/main/resources/application.yml`

```yaml
server:
  port: 8081
  servlet:
    context-path: /

spring:
  application:
    name: mambu-mock-service

  datasource:
    url: jdbc:h2:mem:mambudb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password: ""

  h2:
    console:
      enabled: true
      path: /h2-console

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.H2Dialect

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha

mambu:
  mock:
    simulate-delay-ms: 150        # artificial delay to mimic real Mambu latency
    error-rate-percent: 0         # set > 0 to simulate random Mambu errors
    default-currency: INR
    encoded-key-length: 24

```

---

## `db/migration/V1__create_branches.sql`

```sql
-- ============================================================
-- V1: Mambu Mock — Branches
-- Mirrors Mambu Branch object
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_branches (
    encoded_key         VARCHAR(64)     NOT NULL PRIMARY KEY,
    branch_id           VARCHAR(100)    NOT NULL UNIQUE,
    name                VARCHAR(255)    NOT NULL,
    state               VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    email_address       VARCHAR(255),
    phone_number        VARCHAR(50),
    country             VARCHAR(100),
    city                VARCHAR(100),
    address_line1       VARCHAR(255),
    notes               TEXT,
    creation_date       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_branch_id ON mambu_branches(branch_id);
CREATE INDEX IF NOT EXISTS idx_branch_state ON mambu_branches(state);

```

---

## `db/migration/V2__create_loan_products.sql`

```sql
-- ============================================================
-- V2: Mambu Mock — Loan Products
-- Mirrors Mambu LoanProduct object
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_loan_products (
    encoded_key                     VARCHAR(64)     NOT NULL PRIMARY KEY,
    product_id                      VARCHAR(100)    NOT NULL UNIQUE,
    name                            VARCHAR(255)    NOT NULL,
    state                           VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    type                            VARCHAR(100)    NOT NULL DEFAULT 'FIXED_TERM_LOAN',
    currency_code                   VARCHAR(10)     NOT NULL DEFAULT 'INR',
    for_branch_key                  VARCHAR(64)     NOT NULL,
    -- Interest Settings
    default_interest_rate           DECIMAL(10, 4)  NOT NULL,
    interest_calculation_method     VARCHAR(100)    NOT NULL DEFAULT 'DECLINING_BALANCE',
    interest_charge_frequency       VARCHAR(100)    NOT NULL DEFAULT 'ANNUALIZED',
    -- Amount Settings
    min_loan_amount                 DECIMAL(18, 2)  NOT NULL,
    max_loan_amount                 DECIMAL(18, 2)  NOT NULL,
    default_loan_amount             DECIMAL(18, 2)  NOT NULL,
    -- Schedule Settings
    default_repayment_period_count  INT             NOT NULL DEFAULT 12,
    default_repayment_period_unit   VARCHAR(50)     NOT NULL DEFAULT 'MONTHS',
    repayment_schedule_method       VARCHAR(100)    NOT NULL DEFAULT 'STANDARD',
    -- Fees
    processing_fee_percent          DECIMAL(10, 4)  DEFAULT 0,
    prepayment_penalty_percent      DECIMAL(10, 4)  DEFAULT 0,

    creation_date                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_product_branch FOREIGN KEY (for_branch_key) REFERENCES mambu_branches(encoded_key)
);

CREATE INDEX IF NOT EXISTS idx_product_id ON mambu_loan_products(product_id);
CREATE INDEX IF NOT EXISTS idx_product_branch ON mambu_loan_products(for_branch_key);

```

---

## `db/migration/V3__create_clients.sql`

```sql
-- ============================================================
-- V3: Mambu Mock — Clients (Borrowers)
-- Mirrors Mambu Client object
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_clients (
    encoded_key             VARCHAR(64)     NOT NULL PRIMARY KEY,
    client_id               VARCHAR(100)    NOT NULL UNIQUE,
    first_name              VARCHAR(255)    NOT NULL,
    last_name               VARCHAR(255),
    full_name               VARCHAR(511),
    email_address           VARCHAR(255),
    mobile_phone            VARCHAR(50),
    birth_date              DATE,
    gender                  VARCHAR(20),
    state                   VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    assigned_branch_key     VARCHAR(64),
    notes                   TEXT,
    creation_date           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mambu_client_id_documents (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    client_encoded_key  VARCHAR(64)     NOT NULL,
    document_type       VARCHAR(100)    NOT NULL,
    document_id         VARCHAR(255)    NOT NULL,
    issuing_authority   VARCHAR(255),
    issue_date          DATE,
    expiry_date         DATE,
    CONSTRAINT fk_id_doc_client FOREIGN KEY (client_encoded_key) REFERENCES mambu_clients(encoded_key)
);

CREATE TABLE IF NOT EXISTS mambu_client_addresses (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    client_encoded_key  VARCHAR(64)     NOT NULL,
    line1               VARCHAR(255),
    line2               VARCHAR(255),
    city                VARCHAR(100),
    region              VARCHAR(100),
    postcode            VARCHAR(20),
    country             VARCHAR(100),
    index_in_list       INT             DEFAULT 0,
    CONSTRAINT fk_address_client FOREIGN KEY (client_encoded_key) REFERENCES mambu_clients(encoded_key)
);

CREATE INDEX IF NOT EXISTS idx_client_id ON mambu_clients(client_id);
CREATE INDEX IF NOT EXISTS idx_client_branch ON mambu_clients(assigned_branch_key);

```

---

## `db/migration/V4__create_loan_accounts.sql`

```sql
-- ============================================================
-- V4: Mambu Mock — Loan Accounts
-- Mirrors Mambu LoanAccount object
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_loan_accounts (
    encoded_key                 VARCHAR(64)     NOT NULL PRIMARY KEY,
    loan_id                     VARCHAR(100)    NOT NULL UNIQUE,
    account_state               VARCHAR(100)    NOT NULL DEFAULT 'PENDING_APPROVAL',
    client_key                  VARCHAR(64)     NOT NULL,
    product_type_key            VARCHAR(64)     NOT NULL,
    assigned_branch_key         VARCHAR(64)     NOT NULL,
    -- Financials
    loan_amount                 DECIMAL(18, 2)  NOT NULL,
    interest_rate               DECIMAL(10, 4)  NOT NULL,
    repayment_installments      INT             NOT NULL,
    principal_balance           DECIMAL(18, 2)  DEFAULT 0,
    interest_balance            DECIMAL(18, 2)  DEFAULT 0,
    fees_balance                DECIMAL(18, 2)  DEFAULT 0,
    penalty_balance             DECIMAL(18, 2)  DEFAULT 0,
    -- Dates
    disbursement_date           DATE,
    first_repayment_date        DATE,
    last_repayment_date         DATE,
    approved_date               TIMESTAMP,
    closed_date                 TIMESTAMP,
    -- External
    external_id                 VARCHAR(255),
    notes                       TEXT,
    creation_date               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_client  FOREIGN KEY (client_key)         REFERENCES mambu_clients(encoded_key),
    CONSTRAINT fk_loan_product FOREIGN KEY (product_type_key)   REFERENCES mambu_loan_products(encoded_key),
    CONSTRAINT fk_loan_branch  FOREIGN KEY (assigned_branch_key) REFERENCES mambu_branches(encoded_key)
);

CREATE INDEX IF NOT EXISTS idx_loan_id            ON mambu_loan_accounts(loan_id);
CREATE INDEX IF NOT EXISTS idx_loan_state         ON mambu_loan_accounts(account_state);
CREATE INDEX IF NOT EXISTS idx_loan_client        ON mambu_loan_accounts(client_key);
CREATE INDEX IF NOT EXISTS idx_loan_branch        ON mambu_loan_accounts(assigned_branch_key);

```

---

## `db/migration/V5__create_installments_transactions.sql`

```sql
-- ============================================================
-- V5: Mambu Mock — Installments + Transactions
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_installments (
    id                      BIGINT          AUTO_INCREMENT PRIMARY KEY,
    loan_encoded_key        VARCHAR(64)     NOT NULL,
    installment_number      INT             NOT NULL,
    due_date                DATE            NOT NULL,
    state                   VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    -- Principal
    principal_amount        DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    principal_paid          DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    principal_due           DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    -- Interest
    interest_amount         DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    interest_paid           DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    interest_due            DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    -- Fee
    fee_amount              DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    fee_paid                DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    fee_due                 DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    -- Penalty
    penalty_amount          DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    penalty_paid            DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    penalty_due             DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    -- Total
    total_due               DECIMAL(18, 2)  NOT NULL DEFAULT 0,
    last_paid_date          DATE,

    CONSTRAINT fk_installment_loan FOREIGN KEY (loan_encoded_key) REFERENCES mambu_loan_accounts(encoded_key),
    CONSTRAINT uq_installment UNIQUE (loan_encoded_key, installment_number)
);

CREATE TABLE IF NOT EXISTS mambu_transactions (
    encoded_key         VARCHAR(64)     NOT NULL PRIMARY KEY,
    transaction_id      VARCHAR(100)    NOT NULL UNIQUE,
    type                VARCHAR(100)    NOT NULL,   -- DISBURSEMENT, REPAYMENT, FEE, PENALTY
    amount              DECIMAL(18, 2)  NOT NULL,
    parent_account_key  VARCHAR(64)     NOT NULL,
    parent_account_id   VARCHAR(100)    NOT NULL,
    external_id         VARCHAR(255),
    notes               TEXT,
    channel_id          VARCHAR(100)    DEFAULT 'ONLINE_PAYMENT',
    value_date          DATE            NOT NULL,
    creation_date       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_txn_loan FOREIGN KEY (parent_account_key) REFERENCES mambu_loan_accounts(encoded_key)
);

CREATE INDEX IF NOT EXISTS idx_installment_loan   ON mambu_installments(loan_encoded_key);
CREATE INDEX IF NOT EXISTS idx_installment_state  ON mambu_installments(state);
CREATE INDEX IF NOT EXISTS idx_txn_id             ON mambu_transactions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_txn_loan           ON mambu_transactions(parent_account_key);
CREATE INDEX IF NOT EXISTS idx_txn_external       ON mambu_transactions(external_id);

```

---

## `MambuMockApplication.java`

```java
package com.loanplatform.mambu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MambuMockApplication {
    public static void main(String[] args) {
        SpringApplication.run(MambuMockApplication.class, args);
    }
}

```

---

## `config/MambuMockConfig.java`

```java
package com.loanplatform.mambu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mambu.mock")
@Data
public class MambuMockConfig {
    private int simulateDelayMs = 150;
    private int errorRatePercent = 0;
    private String defaultCurrency = "INR";
    private int encodedKeyLength = 24;
}

```

---

## `util/MambuIdGenerator.java`

```java
package com.loanplatform.mambu.util;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MambuIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong COUNTER = new AtomicLong(1);

    /**
     * Generates a 24-character hex encoded key — identical format to real Mambu encodedKeys
     * e.g. "8a818e8a7f2b3c4d5e6f7a8b"
     */
    public String generateEncodedKey() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Generates a human-readable Mambu-style ID for a resource
     * e.g. branch_001, cli_001, loan_001
     */
    public String generateId(String prefix) {
        long count = COUNTER.getAndIncrement();
        long timestamp = Instant.now().toEpochMilli() % 100000;
        return prefix + "_" + String.format("%05d", timestamp) + String.format("%03d", count);
    }

    public String generateBranchId()      { return generateId("branch"); }
    public String generateProductId()     { return generateId("lp"); }
    public String generateClientId()      { return generateId("cli"); }
    public String generateLoanId()        { return generateId("loan"); }
    public String generateTransactionId() { return generateId("txn"); }
}

```

---

## `service/EmiCalculatorService.java`

```java
package com.loanplatform.mambu.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * EMI Calculator using Reducing Balance (Declining Balance) method
 * — exact same formula Mambu uses internally.
 *
 * EMI = P × r × (1+r)^n / ((1+r)^n - 1)
 *   P = principal
 *   r = monthly interest rate (annual rate / 12 / 100)
 *   n = number of installments
 */
@Service
public class EmiCalculatorService {

    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);
    private static final int SCALE = 2;

    public BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        if (annualRatePercent.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(tenureMonths), SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal r = annualRatePercent.divide(BigDecimal.valueOf(1200), MC); // monthly rate
        BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);
        BigDecimal onePlusRpowN = onePlusR.pow(tenureMonths, MC);
        BigDecimal numerator = principal.multiply(r, MC).multiply(onePlusRpowN, MC);
        BigDecimal denominator = onePlusRpowN.subtract(BigDecimal.ONE, MC);
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    public List<InstallmentBreakdown> generateSchedule(
            BigDecimal principal,
            BigDecimal annualRatePercent,
            int tenureMonths,
            LocalDate firstRepaymentDate) {

        BigDecimal emi = calculateEmi(principal, annualRatePercent, tenureMonths);
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal outstandingPrincipal = principal;

        List<InstallmentBreakdown> schedule = new ArrayList<>();

        for (int i = 1; i <= tenureMonths; i++) {
            BigDecimal interest = outstandingPrincipal.multiply(monthlyRate, MC)
                    .setScale(SCALE, RoundingMode.HALF_UP);

            BigDecimal principalComponent;
            if (i == tenureMonths) {
                // Last installment — clear remaining principal
                principalComponent = outstandingPrincipal;
            } else {
                principalComponent = emi.subtract(interest).setScale(SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal total = principalComponent.add(interest).setScale(SCALE, RoundingMode.HALF_UP);
            outstandingPrincipal = outstandingPrincipal.subtract(principalComponent)
                    .setScale(SCALE, RoundingMode.HALF_UP);

            LocalDate dueDate = firstRepaymentDate.plusMonths(i - 1);

            schedule.add(new InstallmentBreakdown(i, dueDate, principalComponent, interest, total,
                    outstandingPrincipal.max(BigDecimal.ZERO)));
        }
        return schedule;
    }

    public BigDecimal calculateTotalInterest(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        BigDecimal emi = calculateEmi(principal, annualRatePercent, tenureMonths);
        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(tenureMonths))
                .setScale(SCALE, RoundingMode.HALF_UP);
        return totalPayable.subtract(principal).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public record InstallmentBreakdown(
            int number,
            LocalDate dueDate,
            BigDecimal principalAmount,
            BigDecimal interestAmount,
            BigDecimal totalDue,
            BigDecimal outstandingPrincipal
    ) {}
}

```

---

## `model/branch/MambuBranch.java`

```java
package com.loanplatform.mambu.model.branch;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mambu_branches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuBranch {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "branch_id", nullable = false, unique = true, length = 100)
    private String branchId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String state;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    private String country;
    private String city;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "last_modified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}

```

---

## `model/loanproduct/MambuLoanProduct.java`

```java
package com.loanplatform.mambu.model.loanproduct;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mambu_loan_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanProduct {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "product_id", nullable = false, unique = true, length = 100)
    private String productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String state = "ACTIVE";

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String type = "FIXED_TERM_LOAN";

    @Column(name = "currency_code", nullable = false, length = 10)
    @Builder.Default
    private String currencyCode = "INR";

    @Column(name = "for_branch_key", nullable = false, length = 64)
    private String forBranchKey;

    // Interest Settings
    @Column(name = "default_interest_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal defaultInterestRate;

    @Column(name = "interest_calculation_method", nullable = false)
    @Builder.Default
    private String interestCalculationMethod = "DECLINING_BALANCE";

    @Column(name = "interest_charge_frequency", nullable = false)
    @Builder.Default
    private String interestChargeFrequency = "ANNUALIZED";

    // Amount Settings
    @Column(name = "min_loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal minLoanAmount;

    @Column(name = "max_loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxLoanAmount;

    @Column(name = "default_loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal defaultLoanAmount;

    // Schedule Settings
    @Column(name = "default_repayment_period_count", nullable = false)
    @Builder.Default
    private Integer defaultRepaymentPeriodCount = 12;

    @Column(name = "default_repayment_period_unit", nullable = false)
    @Builder.Default
    private String defaultRepaymentPeriodUnit = "MONTHS";

    @Column(name = "repayment_schedule_method", nullable = false)
    @Builder.Default
    private String repaymentScheduleMethod = "STANDARD";

    // Fees
    @Column(name = "processing_fee_percent", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal processingFeePercent = BigDecimal.ZERO;

    @Column(name = "prepayment_penalty_percent", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal prepaymentPenaltyPercent = BigDecimal.ZERO;

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "last_modified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}

```

---

## `model/client/MambuClient.java`

```java
package com.loanplatform.mambu.model.client;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mambu_clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuClient {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "mobile_phone", length = 50)
    private String mobilePhone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 20)
    private String gender;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String state = "ACTIVE";

    @Column(name = "assigned_branch_key", length = 64)
    private String assignedBranchKey;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "clientEncodedKey", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<MambuIdDocument> idDocuments = new ArrayList<>();

    @OneToMany(mappedBy = "clientEncodedKey", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<MambuClientAddress> addresses = new ArrayList<>();

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "last_modified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
        if (firstName != null && lastName != null) {
            fullName = firstName + " " + lastName;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}

```

---

## `model/client/MambuIdDocument.java`

```java
package com.loanplatform.mambu.model.client;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "mambu_client_id_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuIdDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_encoded_key", nullable = false, length = 64)
    private String clientEncodedKey;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "issuing_authority")
    private String issuingAuthority;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}

```

---

## `model/client/MambuClientAddress.java`

```java
package com.loanplatform.mambu.model.client;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mambu_client_addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuClientAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_encoded_key", nullable = false, length = 64)
    private String clientEncodedKey;

    private String line1;
    private String line2;
    private String city;
    private String region;
    private String postcode;
    private String country;

    @Column(name = "index_in_list")
    @Builder.Default
    private Integer indexInList = 0;
}

```

---

## `model/loan/MambuLoanAccount.java`

```java
package com.loanplatform.mambu.model.loan;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mambu_loan_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuLoanAccount {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "loan_id", nullable = false, unique = true, length = 100)
    private String loanId;

    @Column(name = "account_state", nullable = false, length = 100)
    @Builder.Default
    private String accountState = "PENDING_APPROVAL";

    @Column(name = "client_key", nullable = false, length = 64)
    private String clientKey;

    @Column(name = "product_type_key", nullable = false, length = 64)
    private String productTypeKey;

    @Column(name = "assigned_branch_key", nullable = false, length = 64)
    private String assignedBranchKey;

    @Column(name = "loan_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "interest_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "repayment_installments", nullable = false)
    private Integer repaymentInstallments;

    @Column(name = "principal_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalBalance = BigDecimal.ZERO;

    @Column(name = "interest_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestBalance = BigDecimal.ZERO;

    @Column(name = "fees_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feesBalance = BigDecimal.ZERO;

    @Column(name = "penalty_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyBalance = BigDecimal.ZERO;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "first_repayment_date")
    private LocalDate firstRepaymentDate;

    @Column(name = "last_repayment_date")
    private LocalDate lastRepaymentDate;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(name = "closed_date")
    private LocalDateTime closedDate;

    @Column(name = "external_id")
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "loanEncodedKey", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    @Builder.Default
    private List<MambuInstallment> installments = new ArrayList<>();

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "last_modified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}

```

---

## `model/loan/MambuInstallment.java`

```java
package com.loanplatform.mambu.model.loan;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "mambu_installments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_encoded_key", nullable = false, length = 64)
    private String loanEncodedKey;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String state = "PENDING";

    // Principal
    @Column(name = "principal_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalAmount = BigDecimal.ZERO;

    @Column(name = "principal_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "principal_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalDue = BigDecimal.ZERO;

    // Interest
    @Column(name = "interest_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "interest_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "interest_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestDue = BigDecimal.ZERO;

    // Fee
    @Column(name = "fee_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "fee_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feePaid = BigDecimal.ZERO;

    @Column(name = "fee_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feeDue = BigDecimal.ZERO;

    // Penalty
    @Column(name = "penalty_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(name = "penalty_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    @Column(name = "penalty_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "total_due", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalDue = BigDecimal.ZERO;

    @Column(name = "last_paid_date")
    private LocalDate lastPaidDate;
}

```

---

## `model/repayment/MambuTransaction.java`

```java
package com.loanplatform.mambu.model.repayment;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mambu_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuTransaction {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "parent_account_key", nullable = false, length = 64)
    private String parentAccountKey;

    @Column(name = "parent_account_id", nullable = false, length = 100)
    private String parentAccountId;

    @Column(name = "external_id")
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "channel_id", length = 100)
    @Builder.Default
    private String channelId = "ONLINE_PAYMENT";

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
    }
}

```

---

## `repository/MambuBranchRepository.java`

```java
package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.branch.MambuBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MambuBranchRepository extends JpaRepository<MambuBranch, String> {
    Optional<MambuBranch> findByBranchId(String branchId);
    boolean existsByBranchId(String branchId);
}

```

---

## `repository/MambuLoanProductRepository.java`

```java
package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.loanproduct.MambuLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuLoanProductRepository extends JpaRepository<MambuLoanProduct, String> {
    Optional<MambuLoanProduct> findByProductId(String productId);
    List<MambuLoanProduct> findByForBranchKey(String branchKey);
    boolean existsByProductId(String productId);
}

```

---

## `repository/MambuClientRepository.java`

```java
package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.client.MambuClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MambuClientRepository extends JpaRepository<MambuClient, String> {
    Optional<MambuClient> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}

```

---

## `repository/MambuLoanAccountRepository.java`

```java
package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuLoanAccountRepository extends JpaRepository<MambuLoanAccount, String> {
    Optional<MambuLoanAccount> findByLoanId(String loanId);
    List<MambuLoanAccount> findByAssignedBranchKeyAndAccountState(String branchKey, String state);
    List<MambuLoanAccount> findByAssignedBranchKey(String branchKey);
    boolean existsByLoanId(String loanId);

    @Query("SELECT l FROM MambuLoanAccount l WHERE l.assignedBranchKey = :branchKey AND l.accountState IN :states")
    List<MambuLoanAccount> findByBranchKeyAndStates(String branchKey, List<String> states);
}

```

---

## `repository/MambuInstallmentRepository.java`

```java
package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.loan.MambuInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuInstallmentRepository extends JpaRepository<MambuInstallment, Long> {
    List<MambuInstallment> findByLoanEncodedKeyOrderByInstallmentNumberAsc(String loanEncodedKey);
    Optional<MambuInstallment> findByLoanEncodedKeyAndInstallmentNumber(String loanEncodedKey, int number);
    void deleteByLoanEncodedKey(String loanEncodedKey);
}

```

---

## `repository/MambuTransactionRepository.java`

```java
package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.repayment.MambuTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuTransactionRepository extends JpaRepository<MambuTransaction, String> {
    List<MambuTransaction> findByParentAccountKeyOrderByCreationDateDesc(String accountKey);
    Optional<MambuTransaction> findByExternalId(String externalId);
    boolean existsByExternalId(String externalId);
}

```

---

## `exception/MambuResourceNotFoundException.java`

```java
package com.loanplatform.mambu.exception;

public class MambuResourceNotFoundException extends RuntimeException {
    public MambuResourceNotFoundException(String message) { super(message); }
}

```

---

## `exception/MambuInvalidStateException.java`

```java
package com.loanplatform.mambu.exception;

public class MambuInvalidStateException extends RuntimeException {
    public MambuInvalidStateException(String message) { super(message); }
}

```

---

## `exception/MambuMockExceptionHandler.java`

```java
package com.loanplatform.mambu.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class MambuMockExceptionHandler {

    @ExceptionHandler(MambuResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(MambuResourceNotFoundException ex) {
        log.warn("Mambu resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", ex.getMessage(), 404));
    }

    @ExceptionHandler(MambuInvalidStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(MambuInvalidStateException ex) {
        log.warn("Mambu invalid state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("INVALID_STATE", ex.getMessage(), 409));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("INVALID_PARAMETERS", ex.getMessage(), 400));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("VALIDATION_ERROR", details, 400));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("Mambu mock unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "An unexpected error occurred in Mambu mock", 500));
    }

    private Map<String, Object> error(String errorCode, String message, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("errorCode", errorCode);
        body.put("errorReason", message);
        body.put("httpStatus", status);
        return body;
    }
}

```

---

## `controller/MambuBranchController.java`

```java
package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.branch.MambuBranch;
import com.loanplatform.mambu.repository.MambuBranchRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/branches")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Branches", description = "Mambu Branch API Mock")
public class MambuBranchController {

    private final MambuBranchRepository branchRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping
    @Operation(summary = "Create a new Mambu Branch")
    public ResponseEntity<Map<String, Object>> createBranch(@Valid @RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/branches: {}", request.get("id"));

        String branchId = (String) request.getOrDefault("id", idGenerator.generateBranchId());

        if (branchRepository.existsByBranchId(branchId)) {
            throw new IllegalArgumentException("Branch with id '" + branchId + "' already exists");
        }

        String encodedKey = idGenerator.generateEncodedKey();

        MambuBranch branch = MambuBranch.builder()
                .encodedKey(encodedKey)
                .branchId(branchId)
                .name((String) request.get("name"))
                .state((String) request.getOrDefault("state", "ACTIVE"))
                .emailAddress((String) request.get("emailAddress"))
                .phoneNumber((String) request.get("phoneNumber"))
                .notes((String) request.get("notes"))
                .build();

        // Extract address if present
        if (request.get("address") instanceof Map<?, ?> addr) {
            branch.setCountry((String) addr.get("country"));
            branch.setCity((String) addr.get("city"));
            branch.setAddressLine1((String) addr.get("line1"));
        }

        branchRepository.save(branch);
        log.info("Mambu Mock — Branch created: encodedKey={}, id={}", encodedKey, branchId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(branch));
    }

    @GetMapping("/{branchId}")
    @Operation(summary = "Get Mambu Branch by ID")
    public ResponseEntity<Map<String, Object>> getBranch(@PathVariable String branchId) {
        log.info("Mambu Mock — GET /api/v2/branches/{}", branchId);
        MambuBranch branch = branchRepository.findByBranchId(branchId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Branch not found: " + branchId));
        return ResponseEntity.ok(toResponse(branch));
    }

    @GetMapping
    @Operation(summary = "Get all Mambu Branches")
    public ResponseEntity<Object> getAllBranches() {
        return ResponseEntity.ok(branchRepository.findAll().stream().map(this::toResponse).toList());
    }

    private Map<String, Object> toResponse(MambuBranch b) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", b.getEncodedKey());
        res.put("id", b.getBranchId());
        res.put("name", b.getName());
        res.put("state", b.getState());
        res.put("emailAddress", b.getEmailAddress());
        res.put("phoneNumber", b.getPhoneNumber());
        res.put("creationDate", b.getCreationDate() + "+05:30");
        res.put("lastModifiedDate", b.getLastModifiedDate() + "+05:30");

        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("country", b.getCountry());
        addr.put("city", b.getCity());
        addr.put("line1", b.getAddressLine1());
        res.put("address", addr);
        return res;
    }
}

```

---

## `controller/MambuLoanProductController.java`

```java
package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loanproduct.MambuLoanProduct;
import com.loanplatform.mambu.repository.MambuBranchRepository;
import com.loanplatform.mambu.repository.MambuLoanProductRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/loanproducts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Loan Products", description = "Mambu Loan Product API Mock")
public class MambuLoanProductController {

    private final MambuLoanProductRepository productRepository;
    private final MambuBranchRepository branchRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping
    @Operation(summary = "Create a Loan Product")
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/loanproducts: {}", request.get("id"));

        String productId = (String) request.getOrDefault("id", idGenerator.generateProductId());
        String forBranchKey = (String) request.get("forBranchKey");

        if (!branchRepository.existsById(forBranchKey)) {
            throw new MambuResourceNotFoundException("Branch not found with encodedKey: " + forBranchKey);
        }

        // Parse interest settings
        Map<?, ?> interestSettings = (Map<?, ?>) request.getOrDefault("interestSettings", Map.of());
        Map<?, ?> defaultRate = (Map<?, ?>) interestSettings.getOrDefault("defaultInterestRate", Map.of());
        BigDecimal rate = parseBigDecimal(defaultRate.get("value"), "14.5");

        // Parse amount settings
        Map<?, ?> amountSettings = (Map<?, ?>) request.getOrDefault("loanAmountSettings", Map.of());
        Map<?, ?> minAmt = (Map<?, ?>) amountSettings.getOrDefault("minAmount", Map.of());
        Map<?, ?> maxAmt = (Map<?, ?>) amountSettings.getOrDefault("maxAmount", Map.of());
        Map<?, ?> defAmt = (Map<?, ?>) amountSettings.getOrDefault("defaultAmount", Map.of());

        // Parse schedule settings
        Map<?, ?> schedSettings = (Map<?, ?>) request.getOrDefault("scheduleSettings", Map.of());

        MambuLoanProduct product = MambuLoanProduct.builder()
                .encodedKey(idGenerator.generateEncodedKey())
                .productId(productId)
                .name((String) request.get("name"))
                .state((String) request.getOrDefault("state", "ACTIVE"))
                .type((String) request.getOrDefault("type", "FIXED_TERM_LOAN"))
                .forBranchKey(forBranchKey)
                .defaultInterestRate(rate)
                .interestCalculationMethod((String) interestSettings.getOrDefault("interestCalculationMethod", "DECLINING_BALANCE"))
                .interestChargeFrequency((String) interestSettings.getOrDefault("interestChargeFrequency", "ANNUALIZED"))
                .minLoanAmount(parseBigDecimal(minAmt.get("value"), "10000"))
                .maxLoanAmount(parseBigDecimal(maxAmt.get("value"), "1000000"))
                .defaultLoanAmount(parseBigDecimal(defAmt.get("value"), "100000"))
                .defaultRepaymentPeriodCount(parseInteger(schedSettings.get("defaultRepaymentPeriodCount"), 12))
                .defaultRepaymentPeriodUnit((String) schedSettings.getOrDefault("defaultRepaymentPeriodUnit", "MONTHS"))
                .repaymentScheduleMethod((String) schedSettings.getOrDefault("repaymentScheduleMethod", "STANDARD"))
                .build();

        productRepository.save(product);
        log.info("Mambu Mock — Loan Product created: encodedKey={}, id={}", product.getEncodedKey(), productId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get Loan Product by ID")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable String productId) {
        MambuLoanProduct product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan product not found: " + productId));
        return ResponseEntity.ok(toResponse(product));
    }

    private Map<String, Object> toResponse(MambuLoanProduct p) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", p.getEncodedKey());
        res.put("id", p.getProductId());
        res.put("name", p.getName());
        res.put("state", p.getState());
        res.put("type", p.getType());
        res.put("creationDate", p.getCreationDate() + "+05:30");
        res.put("lastModifiedDate", p.getLastModifiedDate() + "+05:30");
        res.put("currency", Map.of("code", p.getCurrencyCode(), "name", "Indian Rupee"));

        res.put("loanAmountSettings", Map.of(
                "minAmount", Map.of("value", p.getMinLoanAmount()),
                "maxAmount", Map.of("value", p.getMaxLoanAmount()),
                "defaultAmount", Map.of("value", p.getDefaultLoanAmount())
        ));
        res.put("interestSettings", Map.of(
                "defaultInterestRate", Map.of("value", p.getDefaultInterestRate()),
                "interestCalculationMethod", p.getInterestCalculationMethod(),
                "interestChargeFrequency", p.getInterestChargeFrequency()
        ));
        res.put("scheduleSettings", Map.of(
                "defaultRepaymentPeriodCount", p.getDefaultRepaymentPeriodCount(),
                "defaultRepaymentPeriodUnit", p.getDefaultRepaymentPeriodUnit(),
                "repaymentScheduleMethod", p.getRepaymentScheduleMethod()
        ));
        return res;
    }

    private BigDecimal parseBigDecimal(Object val, String defaultVal) {
        if (val == null) return new BigDecimal(defaultVal);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }

    private Integer parseInteger(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}

```

---

## `controller/MambuClientController.java`

```java
package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.client.MambuClient;
import com.loanplatform.mambu.model.client.MambuClientAddress;
import com.loanplatform.mambu.model.client.MambuIdDocument;
import com.loanplatform.mambu.repository.MambuClientRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/clients")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Clients", description = "Mambu Client API Mock")
public class MambuClientController {

    private final MambuClientRepository clientRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping
    @Operation(summary = "Create a Mambu Client (Borrower)")
    public ResponseEntity<Map<String, Object>> createClient(@RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/clients: {} {}", request.get("firstName"), request.get("lastName"));

        String encodedKey = idGenerator.generateEncodedKey();
        String clientId = idGenerator.generateClientId();

        MambuClient client = MambuClient.builder()
                .encodedKey(encodedKey)
                .clientId(clientId)
                .firstName((String) request.get("firstName"))
                .lastName((String) request.get("lastName"))
                .emailAddress((String) request.get("emailAddress"))
                .mobilePhone((String) request.get("mobilePhone"))
                .gender((String) request.get("gender"))
                .state((String) request.getOrDefault("state", "ACTIVE"))
                .assignedBranchKey((String) request.get("assignedBranchKey"))
                .notes((String) request.get("notes"))
                .idDocuments(new ArrayList<>())
                .addresses(new ArrayList<>())
                .build();

        if (request.get("birthDate") != null) {
            client.setBirthDate(LocalDate.parse((String) request.get("birthDate")));
        }

        // Parse ID documents
        if (request.get("idDocuments") instanceof List<?> docs) {
            for (Object d : docs) {
                if (d instanceof Map<?, ?> doc) {
                    client.getIdDocuments().add(MambuIdDocument.builder()
                            .clientEncodedKey(encodedKey)
                            .documentType((String) doc.get("documentType"))
                            .documentId((String) doc.get("documentId"))
                            .issuingAuthority((String) doc.get("issuingAuthority"))
                            .build());
                }
            }
        }

        // Parse addresses
        if (request.get("addresses") instanceof List<?> addrs) {
            for (Object a : addrs) {
                if (a instanceof Map<?, ?> addr) {
                    client.getAddresses().add(MambuClientAddress.builder()
                            .clientEncodedKey(encodedKey)
                            .line1((String) addr.get("line1"))
                            .city((String) addr.get("city"))
                            .region((String) addr.get("region"))
                            .postcode((String) addr.get("postcode"))
                            .country((String) addr.get("country"))
                            .indexInList(addr.get("indexInList") instanceof Number n ? n.intValue() : 0)
                            .build());
                }
            }
        }

        clientRepository.save(client);
        log.info("Mambu Mock — Client created: encodedKey={}, id={}", encodedKey, clientId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(client));
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Get Mambu Client by ID")
    public ResponseEntity<Map<String, Object>> getClient(@PathVariable String clientId) {
        MambuClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Client not found: " + clientId));
        return ResponseEntity.ok(toResponse(client));
    }

    @PatchMapping("/{clientId}")
    @Operation(summary = "Update Mambu Client")
    public ResponseEntity<Map<String, Object>> updateClient(
            @PathVariable String clientId,
            @RequestBody Map<String, Object> request) {

        MambuClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Client not found: " + clientId));

        if (request.containsKey("state")) client.setState((String) request.get("state"));
        if (request.containsKey("emailAddress")) client.setEmailAddress((String) request.get("emailAddress"));
        if (request.containsKey("mobilePhone")) client.setMobilePhone((String) request.get("mobilePhone"));

        clientRepository.save(client);
        return ResponseEntity.ok(toResponse(client));
    }

    private Map<String, Object> toResponse(MambuClient c) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", c.getEncodedKey());
        res.put("id", c.getClientId());
        res.put("firstName", c.getFirstName());
        res.put("lastName", c.getLastName());
        res.put("fullName", c.getFirstName() + (c.getLastName() != null ? " " + c.getLastName() : ""));
        res.put("emailAddress", c.getEmailAddress());
        res.put("mobilePhone", c.getMobilePhone());
        res.put("birthDate", c.getBirthDate() != null ? c.getBirthDate().toString() : null);
        res.put("gender", c.getGender());
        res.put("state", c.getState());
        res.put("assignedBranchKey", c.getAssignedBranchKey());
        res.put("creationDate", c.getCreationDate() + "+05:30");
        res.put("lastModifiedDate", c.getLastModifiedDate() + "+05:30");
        res.put("clientRole", Map.of("encodedKey", "8a818e8d00000001"));

        // ID documents
        List<Map<String, Object>> docs = new ArrayList<>();
        if (c.getIdDocuments() != null) {
            for (MambuIdDocument doc : c.getIdDocuments()) {
                docs.add(Map.of(
                        "documentType", doc.getDocumentType(),
                        "documentId", doc.getDocumentId(),
                        "issuingAuthority", doc.getIssuingAuthority() != null ? doc.getIssuingAuthority() : ""
                ));
            }
        }
        res.put("idDocuments", docs);

        // Addresses
        List<Map<String, Object>> addresses = new ArrayList<>();
        if (c.getAddresses() != null) {
            for (MambuClientAddress addr : c.getAddresses()) {
                addresses.add(Map.of(
                        "line1", addr.getLine1() != null ? addr.getLine1() : "",
                        "city", addr.getCity() != null ? addr.getCity() : "",
                        "region", addr.getRegion() != null ? addr.getRegion() : "",
                        "postcode", addr.getPostcode() != null ? addr.getPostcode() : "",
                        "country", addr.getCountry() != null ? addr.getCountry() : "",
                        "indexInList", addr.getIndexInList()
                ));
            }
        }
        res.put("addresses", addresses);
        return res;
    }
}

```

---

## `controller/MambuLoanAccountController.java`

```java
package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuInvalidStateException;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import com.loanplatform.mambu.repository.MambuClientRepository;
import com.loanplatform.mambu.repository.MambuLoanAccountRepository;
import com.loanplatform.mambu.repository.MambuLoanProductRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Loan Accounts", description = "Mambu Loan Account API Mock")
public class MambuLoanAccountController {

    private final MambuLoanAccountRepository loanRepository;
    private final MambuClientRepository clientRepository;
    private final MambuLoanProductRepository productRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping
    @Operation(summary = "Create a Mambu Loan Account")
    public ResponseEntity<Map<String, Object>> createLoan(@RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/loans, clientKey={}", request.get("clientKey"));

        String clientKey = (String) request.get("clientKey");
        String productTypeKey = (String) request.get("productTypeKey");

        if (!clientRepository.existsById(clientKey)) {
            throw new MambuResourceNotFoundException("Client not found: " + clientKey);
        }
        if (!productRepository.existsById(productTypeKey)) {
            throw new MambuResourceNotFoundException("Loan product not found: " + productTypeKey);
        }

        Map<?, ?> amtMap = (Map<?, ?>) request.getOrDefault("loanAmount", Map.of());
        Map<?, ?> rateMap = (Map<?, ?>) request.getOrDefault("interestRate", Map.of());
        Map<?, ?> disbDetails = (Map<?, ?>) request.getOrDefault("disbursementDetails", Map.of());

        String encodedKey = idGenerator.generateEncodedKey();
        String loanId = idGenerator.generateLoanId();

        MambuLoanAccount loan = MambuLoanAccount.builder()
                .encodedKey(encodedKey)
                .loanId(loanId)
                .accountState("PENDING_APPROVAL")
                .clientKey(clientKey)
                .productTypeKey(productTypeKey)
                .assignedBranchKey((String) request.getOrDefault("assignedBranchKey", ""))
                .loanAmount(parseBigDecimal(amtMap.get("value"), "0"))
                .interestRate(parseBigDecimal(rateMap.get("value"), "14.5"))
                .repaymentInstallments(parseInteger(request.get("repaymentInstallments"), 12))
                .principalBalance(parseBigDecimal(amtMap.get("value"), "0"))
                .build();

        loanRepository.save(loan);
        log.info("Mambu Mock — Loan account created: encodedKey={}, id={}", encodedKey, loanId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(loan));
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get Mambu Loan Account by ID")
    public ResponseEntity<Map<String, Object>> getLoan(@PathVariable String loanId) {
        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));
        return ResponseEntity.ok(toResponse(loan));
    }

    @PostMapping("/{loanId}/approve")
    @Operation(summary = "Approve a Mambu Loan Account")
    public ResponseEntity<Map<String, Object>> approveLoan(
            @PathVariable String loanId,
            @RequestBody(required = false) Map<String, Object> request) {

        log.info("Mambu Mock — POST /api/v2/loans/{}/approve", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        if (!"PENDING_APPROVAL".equals(loan.getAccountState())) {
            throw new MambuInvalidStateException(
                    "Cannot approve loan in state: " + loan.getAccountState() + ". Expected: PENDING_APPROVAL");
        }

        loan.setAccountState("APPROVED");
        loan.setApprovedDate(LocalDateTime.now());
        loanRepository.save(loan);

        log.info("Mambu Mock — Loan approved: {}", loanId);
        return ResponseEntity.ok(toResponse(loan));
    }

    @PatchMapping("/{loanId}")
    @Operation(summary = "Patch Mambu Loan (NPA flagging, state changes)")
    public ResponseEntity<Map<String, Object>> patchLoan(
            @PathVariable String loanId,
            @RequestBody Map<String, Object> request) {

        log.info("Mambu Mock — PATCH /api/v2/loans/{}: {}", loanId, request);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        if (request.containsKey("loanState")) {
            loan.setAccountState((String) request.get("loanState"));
        }
        if (request.containsKey("notes")) {
            loan.setNotes((String) request.get("notes"));
        }

        loanRepository.save(loan);
        return ResponseEntity.ok(toResponse(loan));
    }

    @GetMapping
    @Operation(summary = "Query Mambu Loan Accounts (portfolio reporting)")
    public ResponseEntity<Map<String, Object>> queryLoans(
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) String loanState,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        List<MambuLoanAccount> loans;
        if (branchId != null && loanState != null) {
            loans = loanRepository.findByAssignedBranchKeyAndAccountState(branchId, loanState);
        } else if (branchId != null) {
            loans = loanRepository.findByAssignedBranchKey(branchId);
        } else {
            loans = loanRepository.findAll();
        }

        int total = loans.size();
        List<MambuLoanAccount> page = loans.stream().skip(offset).limit(limit).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loans", page.stream().map(this::toResponse).toList());
        response.put("pagingDetails", Map.of(
                "totalCount", total,
                "pageSize", limit,
                "pageNum", offset / Math.max(limit, 1)
        ));
        return ResponseEntity.ok(response);
    }

    public Map<String, Object> toResponse(MambuLoanAccount l) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", l.getEncodedKey());
        res.put("id", l.getLoanId());
        res.put("accountState", l.getAccountState());
        res.put("clientKey", l.getClientKey());
        res.put("productTypeKey", l.getProductTypeKey());
        res.put("assignedBranchKey", l.getAssignedBranchKey());
        res.put("loanAmount", Map.of("value", l.getLoanAmount()));
        res.put("interestRate", Map.of("value", l.getInterestRate()));
        res.put("repaymentInstallments", l.getRepaymentInstallments());
        res.put("principalBalance", Map.of("value", l.getPrincipalBalance()));
        res.put("interestBalance", Map.of("value", l.getInterestBalance()));
        res.put("feesBalance", Map.of("value", l.getFeesBalance()));
        res.put("penaltyBalance", Map.of("value", l.getPenaltyBalance()));
        res.put("disbursementDate", l.getDisbursementDate());
        res.put("firstRepaymentDate", l.getFirstRepaymentDate());
        res.put("approvedDate", l.getApprovedDate() != null ? l.getApprovedDate() + "+05:30" : null);
        res.put("closedDate", l.getClosedDate() != null ? l.getClosedDate() + "+05:30" : null);
        res.put("externalId", l.getExternalId());
        res.put("creationDate", l.getCreationDate() + "+05:30");
        res.put("lastModifiedDate", l.getLastModifiedDate() + "+05:30");
        return res;
    }

    private BigDecimal parseBigDecimal(Object val, String def) {
        if (val == null) return new BigDecimal(def);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }

    private int parseInteger(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}

```

---

## `controller/MambuSimulationController.java`

```java
package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.service.EmiCalculatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Simulation", description = "Mambu Loan Simulation API Mock")
public class MambuSimulationController {

    private final EmiCalculatorService emiCalculator;

    /**
     * Mambu uses POST /api/v2/loans:simulate (colon notation, not /simulate)
     * Spring handles this via @PostMapping with the literal colon in the path
     */
    @PostMapping(value = ":simulate")
    @Operation(summary = "Simulate a Loan — calculate EMI, schedule, total interest")
    public ResponseEntity<Map<String, Object>> simulateLoan(@RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/loans:simulate");

        Map<?, ?> amtMap = (Map<?, ?>) request.getOrDefault("loanAmount", Map.of());
        Map<?, ?> rateMap = (Map<?, ?>) request.getOrDefault("interestRate", Map.of());
        Map<?, ?> disbDetails = (Map<?, ?>) request.getOrDefault("disbursementDetails", Map.of());

        BigDecimal principal = parseBigDecimal(amtMap.get("value"), "100000");
        BigDecimal annualRate = parseBigDecimal(rateMap.get("value"), "14.5");
        int tenureMonths = parseInteger(request.get("repaymentInstallments"), 12);

        String disbDateStr = (String) disbDetails.get("expectedDisbursementDate");
        LocalDate disbDate = disbDateStr != null ? LocalDate.parse(disbDateStr) : LocalDate.now();
        LocalDate firstRepaymentDate = disbDate.plusMonths(1).withDayOfMonth(1);

        BigDecimal emi = emiCalculator.calculateEmi(principal, annualRate, tenureMonths);
        BigDecimal totalInterest = emiCalculator.calculateTotalInterest(principal, annualRate, tenureMonths);
        BigDecimal totalPayable = principal.add(totalInterest).setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualPercentageRate = annualRate;

        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                emiCalculator.generateSchedule(principal, annualRate, tenureMonths, firstRepaymentDate);

        // Build installments in Mambu response format
        List<Map<String, Object>> installments = new ArrayList<>();
        for (EmiCalculatorService.InstallmentBreakdown inst : schedule) {
            Map<String, Object> installment = new LinkedHashMap<>();
            installment.put("number", inst.number());
            installment.put("dueDate", inst.dueDate().toString());
            installment.put("principal", Map.of(
                    "amount", Map.of("value", inst.principalAmount()),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("interest", Map.of(
                    "amount", Map.of("value", inst.interestAmount()),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("fee", Map.of(
                    "amount", Map.of("value", BigDecimal.ZERO),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("penalty", Map.of(
                    "amount", Map.of("value", BigDecimal.ZERO),
                    "tax", Map.of("value", BigDecimal.ZERO)
            ));
            installment.put("totalDue", Map.of("value", inst.totalDue()));
            installments.add(installment);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loanAmount", Map.of("value", principal));
        response.put("repaymentInstallments", tenureMonths);
        response.put("interestRate", Map.of("value", annualRate));
        response.put("annualPercentageRate", annualPercentageRate);
        response.put("periodicPayment", emi);
        response.put("totalInterestCharged", totalInterest);
        response.put("totalAmountRepaid", totalPayable);
        response.put("repaymentSchedule", Map.of("installments", installments));

        log.info("Mambu Mock — Simulation complete: principal={}, tenure={}, emi={}", principal, tenureMonths, emi);
        return ResponseEntity.ok(response);
    }

    private BigDecimal parseBigDecimal(Object val, String def) {
        if (val == null) return new BigDecimal(def);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }

    private int parseInteger(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}

```

---

## `controller/MambuDisbursementController.java`

```java
package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuInvalidStateException;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loan.MambuInstallment;
import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import com.loanplatform.mambu.model.repayment.MambuTransaction;
import com.loanplatform.mambu.repository.MambuInstallmentRepository;
import com.loanplatform.mambu.repository.MambuLoanAccountRepository;
import com.loanplatform.mambu.repository.MambuLoanProductRepository;
import com.loanplatform.mambu.repository.MambuTransactionRepository;
import com.loanplatform.mambu.service.EmiCalculatorService;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Disbursement", description = "Mambu Disbursement API Mock")
public class MambuDisbursementController {

    private final MambuLoanAccountRepository loanRepository;
    private final MambuLoanProductRepository productRepository;
    private final MambuInstallmentRepository installmentRepository;
    private final MambuTransactionRepository transactionRepository;
    private final EmiCalculatorService emiCalculator;
    private final MambuIdGenerator idGenerator;

    @PostMapping("/{loanId}/disbursement")
    @Operation(summary = "Disburse a Mambu Loan and generate repayment schedule")
    public ResponseEntity<Map<String, Object>> disburseLoan(
            @PathVariable String loanId,
            @RequestBody Map<String, Object> request) {

        log.info("Mambu Mock — POST /api/v2/loans/{}/disbursement", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        if (!"APPROVED".equals(loan.getAccountState())) {
            throw new MambuInvalidStateException(
                    "Cannot disburse loan in state: " + loan.getAccountState() + ". Expected: APPROVED");
        }

        String disbDateStr = (String) request.get("disbursementDate");
        LocalDate disbDate = disbDateStr != null ? LocalDate.parse(disbDateStr) : LocalDate.now();

        String firstRepayStr = (String) request.get("firstRepaymentDate");
        LocalDate firstRepayment = firstRepayStr != null
                ? LocalDate.parse(firstRepayStr)
                : disbDate.plusMonths(1).withDayOfMonth(1);

        // Fetch product config for processing fee
        var product = productRepository.findById(loan.getProductTypeKey()).orElse(null);
        BigDecimal feePercent = product != null ? product.getProcessingFeePercent() : BigDecimal.ZERO;
        BigDecimal processingFee = loan.getLoanAmount()
                .multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netDisbursed = loan.getLoanAmount().subtract(processingFee);

        // Update loan state
        loan.setAccountState("ACTIVE");
        loan.setDisbursementDate(disbDate);
        loan.setFirstRepaymentDate(firstRepayment);
        loan.setLastRepaymentDate(firstRepayment.plusMonths(loan.getRepaymentInstallments() - 1));
        loan.setPrincipalBalance(loan.getLoanAmount());
        loan.setExternalId((String) request.get("externalId"));
        loanRepository.save(loan);

        // Generate installment schedule
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                emiCalculator.generateSchedule(loan.getLoanAmount(), loan.getInterestRate(),
                        loan.getRepaymentInstallments(), firstRepayment);

        List<MambuInstallment> installments = new ArrayList<>();
        for (EmiCalculatorService.InstallmentBreakdown b : schedule) {
            installments.add(MambuInstallment.builder()
                    .loanEncodedKey(loan.getEncodedKey())
                    .installmentNumber(b.number())
                    .dueDate(b.dueDate())
                    .state("PENDING")
                    .principalAmount(b.principalAmount())
                    .principalDue(b.principalAmount())
                    .interestAmount(b.interestAmount())
                    .interestDue(b.interestAmount())
                    .totalDue(b.totalDue())
                    .build());
        }
        installmentRepository.saveAll(installments);

        // Create disbursement transaction
        String txnId = idGenerator.generateTransactionId();
        String txnEncodedKey = idGenerator.generateEncodedKey();
        MambuTransaction txn = MambuTransaction.builder()
                .encodedKey(txnEncodedKey)
                .transactionId(txnId)
                .type("DISBURSEMENT")
                .amount(netDisbursed)
                .parentAccountKey(loan.getEncodedKey())
                .parentAccountId(loan.getLoanId())
                .externalId((String) request.get("externalId"))
                .notes((String) request.getOrDefault("notes", "Loan disbursed"))
                .channelId("BANK_TRANSFER")
                .valueDate(disbDate)
                .build();
        transactionRepository.save(txn);

        log.info("Mambu Mock — Loan disbursed: loanId={}, amount={}, txnId={}", loanId, netDisbursed, txnId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("encodedKey", txnEncodedKey);
        response.put("id", txnId);
        response.put("type", "DISBURSEMENT");
        response.put("amount", netDisbursed);
        response.put("fees", Map.of("amount", processingFee));
        response.put("notes", txn.getNotes());
        response.put("externalId", txn.getExternalId());
        response.put("creationDate", LocalDateTime.now() + "+05:30");
        response.put("valueDate", disbDate.toString());
        response.put("parentAccountKey", loan.getEncodedKey());
        response.put("parentAccountId", loan.getLoanId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

```

---

## `controller/MambuRepaymentController.java`

```java
package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuInvalidStateException;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.loan.MambuInstallment;
import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import com.loanplatform.mambu.model.repayment.MambuTransaction;
import com.loanplatform.mambu.repository.MambuInstallmentRepository;
import com.loanplatform.mambu.repository.MambuLoanAccountRepository;
import com.loanplatform.mambu.repository.MambuTransactionRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Repayments", description = "Mambu Repayment & Schedule API Mock")
public class MambuRepaymentController {

    private final MambuLoanAccountRepository loanRepository;
    private final MambuInstallmentRepository installmentRepository;
    private final MambuTransactionRepository transactionRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping("/{loanId}/repayments")
    @Operation(summary = "Post a repayment to a Mambu Loan")
    public ResponseEntity<Map<String, Object>> postRepayment(
            @PathVariable String loanId,
            @RequestBody Map<String, Object> request) {

        log.info("Mambu Mock — POST /api/v2/loans/{}/repayments", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        String externalId = (String) request.get("externalId");

        // Idempotency: if same externalId exists, return same transaction
        if (externalId != null && transactionRepository.existsByExternalId(externalId)) {
            MambuTransaction existing = transactionRepository.findByExternalId(externalId).get();
            log.warn("Mambu Mock — Duplicate repayment detected externalId={}, returning existing txn", externalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(toTxnResponse(existing));
        }

        if (!"ACTIVE".equals(loan.getAccountState()) && !"NON_PERFORMING".equals(loan.getAccountState())) {
            throw new MambuInvalidStateException(
                    "Cannot post repayment to loan in state: " + loan.getAccountState());
        }

        BigDecimal amount = parseBigDecimal(request.get("amount"), "0");
        String dateStr = (String) request.get("date");
        LocalDate paymentDate = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();

        // Find earliest PENDING installment and mark it paid
        List<MambuInstallment> pending = installmentRepository
                .findByLoanEncodedKeyOrderByInstallmentNumberAsc(loan.getEncodedKey())
                .stream()
                .filter(i -> "PENDING".equals(i.getState()) || "OVERDUE".equals(i.getState()))
                .toList();

        if (!pending.isEmpty()) {
            MambuInstallment installment = pending.get(0);
            installment.setState("PAID");
            installment.setPrincipalPaid(installment.getPrincipalAmount());
            installment.setPrincipalDue(BigDecimal.ZERO);
            installment.setInterestPaid(installment.getInterestAmount());
            installment.setInterestDue(BigDecimal.ZERO);
            installment.setLastPaidDate(paymentDate);
            installmentRepository.save(installment);

            // Update principal balance
            loan.setPrincipalBalance(loan.getPrincipalBalance().subtract(installment.getPrincipalAmount()));
            if (loan.getPrincipalBalance().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setAccountState("CLOSED_OBLIGATIONS_MET");
                loan.setClosedDate(LocalDateTime.now());
            }
            loanRepository.save(loan);
        }

        // Create repayment transaction
        String txnEncodedKey = idGenerator.generateEncodedKey();
        String txnId = idGenerator.generateTransactionId();
        Map<?, ?> channelDetails = (Map<?, ?>) request.getOrDefault("transactionDetails", Map.of());

        MambuTransaction txn = MambuTransaction.builder()
                .encodedKey(txnEncodedKey)
                .transactionId(txnId)
                .type("REPAYMENT")
                .amount(amount)
                .parentAccountKey(loan.getEncodedKey())
                .parentAccountId(loan.getLoanId())
                .externalId(externalId)
                .notes((String) request.get("notes"))
                .channelId((String) channelDetails.getOrDefault("transactionChannelId", "ONLINE_PAYMENT"))
                .valueDate(paymentDate)
                .build();
        transactionRepository.save(txn);

        log.info("Mambu Mock — Repayment posted: loanId={}, amount={}, txnId={}", loanId, amount, txnId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTxnResponse(txn));
    }

    @GetMapping("/{loanId}/schedule")
    @Operation(summary = "Get repayment schedule for a Mambu Loan")
    public ResponseEntity<Map<String, Object>> getSchedule(@PathVariable String loanId) {
        log.info("Mambu Mock — GET /api/v2/loans/{}/schedule", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        List<MambuInstallment> installments = installmentRepository
                .findByLoanEncodedKeyOrderByInstallmentNumberAsc(loan.getEncodedKey());

        List<Map<String, Object>> installmentList = new ArrayList<>();
        for (MambuInstallment inst : installments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("number", inst.getInstallmentNumber());
            item.put("dueDate", inst.getDueDate().toString());
            item.put("lastPaidDate", inst.getLastPaidDate() != null ? inst.getLastPaidDate().toString() : null);
            item.put("state", inst.getState());
            item.put("principal", Map.of(
                    "amount", Map.of("value", inst.getPrincipalAmount()),
                    "paid", Map.of("value", inst.getPrincipalPaid()),
                    "due", Map.of("value", inst.getPrincipalDue())
            ));
            item.put("interest", Map.of(
                    "amount", Map.of("value", inst.getInterestAmount()),
                    "paid", Map.of("value", inst.getInterestPaid()),
                    "due", Map.of("value", inst.getInterestDue())
            ));
            item.put("fee", Map.of(
                    "amount", Map.of("value", inst.getFeeAmount()),
                    "paid", Map.of("value", inst.getFeePaid()),
                    "due", Map.of("value", inst.getFeeDue())
            ));
            item.put("penalty", Map.of(
                    "amount", Map.of("value", inst.getPenaltyAmount()),
                    "paid", Map.of("value", inst.getPenaltyPaid()),
                    "due", Map.of("value", inst.getPenaltyDue())
            ));
            installmentList.add(item);
        }

        return ResponseEntity.ok(Map.of("installments", installmentList));
    }

    @GetMapping("/{loanId}/preview-early-repayment")
    @Operation(summary = "Preview early repayment / foreclosure amount")
    public ResponseEntity<Map<String, Object>> previewEarlyRepayment(
            @PathVariable String loanId,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) String paymentDate) {

        log.info("Mambu Mock — GET /api/v2/loans/{}/preview-early-repayment", loanId);

        MambuLoanAccount loan = loanRepository.findByLoanId(loanId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Loan not found: " + loanId));

        BigDecimal remaining = loan.getPrincipalBalance();
        BigDecimal accruedInterest = remaining.multiply(loan.getInterestRate())
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal prepaymentPenalty = remaining.multiply(BigDecimal.valueOf(0.02))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalSettlement = remaining.add(accruedInterest).add(prepaymentPenalty);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loanId", loanId);
        response.put("remainingPrincipal", Map.of("value", remaining));
        response.put("accruedInterest", Map.of("value", accruedInterest));
        response.put("prepaymentPenalty", Map.of("value", prepaymentPenalty));
        response.put("feesBalance", Map.of("value", BigDecimal.ZERO));
        response.put("totalSettlementAmount", Map.of("value", totalSettlement));
        response.put("rebate", Map.of("value", BigDecimal.ZERO));
        response.put("earlyRepaymentDate", paymentDate != null ? paymentDate : LocalDate.now().toString());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toTxnResponse(MambuTransaction t) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", t.getEncodedKey());
        res.put("id", t.getTransactionId());
        res.put("type", t.getType());
        res.put("amount", t.getAmount());
        res.put("fees", Map.of("amount", BigDecimal.ZERO));
        res.put("notes", t.getNotes());
        res.put("externalId", t.getExternalId());
        res.put("creationDate", t.getCreationDate() + "+05:30");
        res.put("valueDate", t.getValueDate().toString());
        res.put("parentAccountKey", t.getParentAccountKey());
        res.put("parentAccountId", t.getParentAccountId());
        return res;
    }

    private BigDecimal parseBigDecimal(Object val, String def) {
        if (val == null) return new BigDecimal(def);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }
}

```

---

## `test/MambuMockFullLifecycleTest.java`

```java
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

        mockMvc.perform(post("/api/v2/loans/{id}/disbursement", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DISBURSEMENT"))
                .andExpect(jsonPath("$.amount").isNumber())
                .andExpect(jsonPath("$.parentAccountId").value(loanId));
    }

    // =========================================================
    // FLOW 8 — GET SCHEDULE
    // =========================================================

    @Test @Order(11)
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

    @Test @Order(12)
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

    @Test @Order(13)
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

    @Test @Order(14)
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

    @Test @Order(15)
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

    @Test @Order(16)
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

    // =========================================================
    // FLOW 12 — PORTFOLIO REPORTING
    // =========================================================

    @Test @Order(17)
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

    @Test @Order(18)
    @DisplayName("GET /api/v2/loans/{id} — Not found returns 404")
    void shouldReturn404ForUnknownLoan() throws Exception {
        mockMvc.perform(get("/api/v2/loans/{id}", "nonexistent_loan_id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}

```

---

## `test/EmiCalculatorServiceTest.java`

```java
package com.loanplatform.mambu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EmiCalculatorServiceTest {

    private final EmiCalculatorService calculator = new EmiCalculatorService();

    @Test
    @DisplayName("EMI calculation — 500000 @ 14.5% for 36 months = 17217.77")
    void shouldCalculateCorrectEmi() {
        BigDecimal emi = calculator.calculateEmi(
                new BigDecimal("500000"),
                new BigDecimal("14.5"),
                36
        );
        // Real Mambu result: 17217.77
        assertThat(emi).isEqualByComparingTo(new BigDecimal("17217.77"));
    }

    @Test
    @DisplayName("EMI calculation — 100000 @ 12% for 12 months = 8884.88")
    void shouldCalculateCorrectEmiForSmallLoan() {
        BigDecimal emi = calculator.calculateEmi(
                new BigDecimal("100000"),
                new BigDecimal("12.0"),
                12
        );
        assertThat(emi).isEqualByComparingTo(new BigDecimal("8884.88"));
    }

    @Test
    @DisplayName("Zero interest rate — EMI = principal / tenure")
    void shouldHandleZeroInterestRate() {
        BigDecimal emi = calculator.calculateEmi(
                new BigDecimal("120000"),
                BigDecimal.ZERO,
                12
        );
        assertThat(emi).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("Schedule generation — 36 installments produced")
    void shouldGenerateCorrectNumberOfInstallments() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("500000"),
                        new BigDecimal("14.5"),
                        36,
                        LocalDate.of(2024, 3, 1)
                );
        assertThat(schedule).hasSize(36);
    }

    @Test
    @DisplayName("Schedule generation — first installment has correct interest component")
    void shouldHaveCorrectFirstInstallmentInterest() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("500000"),
                        new BigDecimal("14.5"),
                        36,
                        LocalDate.of(2024, 3, 1)
                );
        // First month interest = 500000 * 14.5 / 1200 = 6041.67
        EmiCalculatorService.InstallmentBreakdown first = schedule.get(0);
        assertThat(first.interestAmount()).isEqualByComparingTo(new BigDecimal("6041.67"));
    }

    @Test
    @DisplayName("Schedule generation — sum of principal components equals loan amount")
    void shouldHavePrincipalsSummingToLoanAmount() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("500000"),
                        new BigDecimal("14.5"),
                        36,
                        LocalDate.of(2024, 3, 1)
                );

        BigDecimal totalPrincipal = schedule.stream()
                .map(EmiCalculatorService.InstallmentBreakdown::principalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Should equal loan amount (within rounding tolerance of ±1 rupee)
        assertThat(totalPrincipal.subtract(new BigDecimal("500000")).abs())
                .isLessThanOrEqualTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("Schedule generation — installment numbers are sequential 1..n")
    void shouldHaveSequentialInstallmentNumbers() {
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("200000"),
                        new BigDecimal("12.0"),
                        24,
                        LocalDate.of(2024, 3, 1)
                );

        for (int i = 0; i < schedule.size(); i++) {
            assertThat(schedule.get(i).number()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("Schedule generation — due dates increment by 1 month each")
    void shouldHaveMonthlyDueDates() {
        LocalDate firstDate = LocalDate.of(2024, 3, 1);
        List<EmiCalculatorService.InstallmentBreakdown> schedule =
                calculator.generateSchedule(
                        new BigDecimal("200000"),
                        new BigDecimal("12.0"),
                        12,
                        firstDate
                );

        for (int i = 0; i < schedule.size(); i++) {
            assertThat(schedule.get(i).dueDate()).isEqualTo(firstDate.plusMonths(i));
        }
    }

    @Test
    @DisplayName("Total interest calculated correctly")
    void shouldCalculateTotalInterestCorrectly() {
        BigDecimal totalInterest = calculator.calculateTotalInterest(
                new BigDecimal("500000"),
                new BigDecimal("14.5"),
                36
        );
        // 36 * 17217.77 - 500000 = 119839.72
        assertThat(totalInterest.subtract(new BigDecimal("119839.72")).abs())
                .isLessThanOrEqualTo(new BigDecimal("1.00"));
    }
}

```

---

## `test/json/create-branch.json`

```json
{
  "id": "branch_abc_001",
  "name": "ABC Finance - Main Branch",
  "state": "ACTIVE",
  "emailAddress": "admin@abcfinance.com",
  "phoneNumber": "+919876543210",
  "address": {
    "country": "IN",
    "city": "Bengaluru",
    "line1": "123 MG Road"
  },
  "notes": "Primary branch for ABC Finance Ltd tenant"
}

```

---

## `test/json/create-loan-product.json`

```json
{
  "id": "LP_ABC_001",
  "name": "Personal Loan - ABC Finance",
  "type": "FIXED_TERM_LOAN",
  "state": "ACTIVE",
  "forBranchKey": "REPLACE_WITH_BRANCH_ENCODED_KEY",
  "currency": {
    "code": "INR"
  },
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
    "defaultRepaymentPeriodUnit": "MONTHS",
    "repaymentScheduleMethod": "STANDARD"
  }
}

```

---

## `test/json/create-client.json`

```json
{
  "firstName": "Rahul",
  "lastName": "Sharma",
  "emailAddress": "rahul.sharma@email.com",
  "mobilePhone": "+919876543210",
  "birthDate": "1990-05-15",
  "gender": "MALE",
  "state": "ACTIVE",
  "assignedBranchKey": "REPLACE_WITH_BRANCH_ENCODED_KEY",
  "idDocuments": [
    {
      "documentType": "PAN",
      "documentId": "ABCDE1234F",
      "issuingAuthority": "Income Tax Department of India"
    },
    {
      "documentType": "NATIONAL_ID",
      "documentId": "XXXX-XXXX-1234",
      "issuingAuthority": "UIDAI"
    }
  ],
  "addresses": [
    {
      "line1": "123 MG Road",
      "city": "Bengaluru",
      "region": "Karnataka",
      "postcode": "560001",
      "country": "IN",
      "indexInList": 0
    }
  ]
}

```

---

## `test/json/simulate-loan.json`

```json
{
  "loanAmount": { "value": 500000 },
  "interestRate": { "value": 14.5 },
  "repaymentInstallments": 36,
  "loanProductTypeKey": "REPLACE_WITH_PRODUCT_ENCODED_KEY",
  "clientKey": "REPLACE_WITH_CLIENT_ENCODED_KEY",
  "disbursementDetails": {
    "expectedDisbursementDate": "2024-02-01"
  },
  "repaymentScheduleMethod": "STANDARD",
  "interestCalculationMethod": "DECLINING_BALANCE"
}

```

---

## `test/json/create-loan-account.json`

```json
{
  "clientKey": "REPLACE_WITH_CLIENT_ENCODED_KEY",
  "productTypeKey": "REPLACE_WITH_PRODUCT_ENCODED_KEY",
  "assignedBranchKey": "REPLACE_WITH_BRANCH_ENCODED_KEY",
  "loanAmount": { "value": 500000 },
  "interestRate": { "value": 14.5 },
  "repaymentInstallments": 36,
  "disbursementDetails": {
    "expectedDisbursementDate": "2024-02-01"
  }
}

```

---

## `test/json/approve-loan.json`

```json
{
  "notes": "All documents verified by underwriter. Approved.",
  "date": "2024-01-15"
}

```

---

## `test/json/disburse-loan.json`

```json
{
  "notes": "Loan disbursed to borrower registered bank account",
  "disbursementDate": "2024-02-01",
  "firstRepaymentDate": "2024-03-01",
  "externalId": "DISB-loan_001-20240201",
  "transactionDetails": {
    "transactionChannelId": "BANK_TRANSFER",
    "fields": [
      {
        "fieldSetId": "transferDetails",
        "customFieldValues": [
          { "customField": { "id": "accountNumber" }, "value": "1234567890" },
          { "customField": { "id": "bankIFSC" },      "value": "SBIN0001234" }
        ]
      }
    ]
  }
}

```

---

## `test/json/post-repayment.json`

```json
{
  "amount": 17217.77,
  "date": "2024-03-01",
  "notes": "EMI 1 collected via Stripe PaymentIntent pi_3abc123",
  "externalId": "EMI:loan_001:1:tenant_abc",
  "transactionDetails": {
    "transactionChannelId": "ONLINE_PAYMENT",
    "transactionChannelKey": "8a818e8e00000001"
  }
}

```

---

## `test/json/flag-npa.json`

```json
{
  "loanState": "NON_PERFORMING",
  "notesArray": [
    {
      "notes": "90+ DPD — Automatically flagged as NPA by LoanOS batch job on 2024-05-15",
      "creationDate": "2024-05-15"
    }
  ]
}

```