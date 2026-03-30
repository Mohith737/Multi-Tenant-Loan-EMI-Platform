# API.md — Complete API Reference

## Base URLs

| Service | URL |
|---------|-----|
| Platform API | http://localhost:8080 |
| Mambu Mock Service | http://localhost:8081 |
| Payment Gateway | http://localhost:8082 |

## Auth

Security in current implementation:
```
POST /api/v1/auth/login => Username/password login, returns JWT
POST /api/v1/auth/admin/login => Admin-only username/password login, returns JWT
Use returned JWT in Swagger Authorize (Bearer token) for protected APIs
```

---

## PLATFORM API ENDPOINTS

---

### AUTHENTICATION

#### POST /api/v1/auth/login
Login endpoint for tenant-scoped users (`TENANT_ADMIN`, `BORROWER`), and also supports admin users.

**Request:**
```json
{
  "username": "tenant_admin",
  "password": "tenant_admin_password",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response 200:**
```json
{
  "tokenType": "Bearer",
  "accessToken": "<JWT>",
  "expiresInSeconds": 3600,
  "expiresAt": "2026-03-03T10:30:00Z",
  "username": "tenant_admin",
  "roles": ["TENANT_ADMIN"],
  "tenantId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### POST /api/v1/auth/admin/login
Dedicated login for platform admin.

**Request:**
```json
{
  "username": "platform_admin",
  "password": "platform_admin_password"
}
```

### FLOW 1 — Tenant Onboarding

#### POST /api/v1/tenants
Register new tenant.

**Role:** `PLATFORM_ADMIN (JWT with role claim)`

**Request:**
```json
{
  "name": "ABC Finance Ltd",
  "domain": "abcfinance.com",
  "adminEmail": "admin@abcfinance.com",
  "adminPhone": "+919876543210",
  "planTier": "ENTERPRISE"
}
```

**Response 201:**
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "ABC Finance Ltd",
  "domain": "abcfinance.com",
  "status": "ACTIVE",
  "planTier": "ENTERPRISE",
  "mambuBranchId": "branch_abc_001",
  "mambuBranchKey": "branch_key_001",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### POST /api/v1/loan-products
Create and map a loan product for the tenant resolved from JWT `tenantId` claim.

**Role:** `TENANT_ADMIN (JWT with tenantId claim)`

**Request:**
```json
{
  "productName": "Personal Loan - ABC",
  "minLoanAmount": 10000,
  "maxLoanAmount": 1000000,
  "minTenureMonths": 6,
  "maxTenureMonths": 60,
  "annualInterestRate": 14.5,
  "processingFeePercent": 1.5,
  "prepaymentPenaltyPercent": 2.0,
  "currency": "INR"
}
```

**Response 201:**
```json
{
  "loanProductId": "a6f8fd7b-c468-4a0b-aead-4f296ac251d8",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "mambuProductId": "LP_ABC_001",
  "mambuProductKey": "loan_product_key_001",
  "loanProductConfig": {
    "productName": "Personal Loan - ABC",
    "minLoanAmount": 10000,
    "maxLoanAmount": 1000000,
    "minTenureMonths": 6,
    "maxTenureMonths": 60,
    "annualInterestRate": 14.5,
    "processingFeePercent": 1.5,
    "prepaymentPenaltyPercent": 2.0,
    "currency": "INR"
  },
  "createdAt": "2024-01-15T10:35:00Z"
}
```

#### GET /api/v1/loan-products
List all loan products mapped for tenant from JWT `tenantId` claim.

**Role:** `TENANT_ADMIN (JWT with tenantId claim)`

#### GET /api/v1/tenants/{tenantId}
**Role:** `PLATFORM_ADMIN (JWT with role claim)`

**Response 200:**
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "ABC Finance Ltd",
  "domain": "abcfinance.com",
  "status": "ACTIVE",
  "planTier": "ENTERPRISE",
  "mambuBranchId": "branch_abc_001",
  "mambuBranchKey": "branch_key_001",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### PUT /api/v1/tenants/{tenantId}/activate
**Role:** `PLATFORM_ADMIN (JWT with role claim)`

**Response 200:**
```json
{ "tenantId": "...", "status": "ACTIVE", "activatedAt": "2024-01-15T10:35:00Z" }
```

#### PUT /api/v1/tenants/{tenantId}/suspend
**Role:** `PLATFORM_ADMIN (JWT with role claim)`

**Request:** `{ "reason": "Payment overdue" }`
**Response 200:**
```json
{ "tenantId": "...", "status": "SUSPENDED", "suspendedAt": "2024-01-15T10:35:00Z", "reason": "Payment overdue" }
```

---

### FLOW 2 — Borrower Registration

#### GET /api/v1/tenants/public
**Role:** Public (no JWT)

Returns all `ACTIVE` tenants with loan product comparison data.

**Response 200:**
```json
[
  {
    "tenantId": "550e8400-e29b-41d4-a716-446655440000",
    "tenantName": "ABC Finance Ltd",
    "loanProducts": [
      {
        "productName": "Personal Loan - ABC",
        "interestRate": 14.5,
        "minAmount": 10000,
        "maxAmount": 500000,
        "minTenure": 6,
        "maxTenure": 60
      }
    ]
  }
]
```

#### POST /api/v1/auth/register
**Role:** Public (no JWT)

**Request:**
```json
{
  "firstName": "Rahul",
  "lastName": "Sharma",
  "email": "rahul.sharma@email.com",
  "password": "S3cureP@ss",
  "mobile": "+919876543210",
  "dateOfBirth": "1990-05-15",
  "gender": "MALE",
  "panNumber": "ABCDE1234F",
  "aadhaarNumber": "987654321234",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "address": {
    "line1": "123 MG Road",
    "city": "Bengaluru",
    "state": "Karnataka",
    "pincode": "560001"
  }
}
```

**Response 201:**
```json
{
  "accessToken": "<BORROWER_JWT>"
}
```

#### POST /api/v1/kyc/submit
**Role:** `BORROWER`
**Validation rules:**
- `PAN_CARD` is mandatory.
- At least one of `AADHAAR` or `ADDRESS_PROOF` is mandatory.
- Duplicate `documentType` values are rejected.
- Duplicate `documentNumber` values across submitted documents are rejected.
- `PAN_CARD.documentNumber` must match PAN format (`ABCDE1234F`).
- `AADHAAR.documentNumber` must be 12 digits.

**Request:**
```json
{
  "documents": [
    {
      "documentType": "PAN_CARD",
      "documentNumber": "ABCDE1234F",
      "documentReference": "s3://kyc-docs/borrower-id/pan.pdf"
    },
    {
      "documentType": "AADHAAR",
      "documentNumber": "987654321234",
      "documentReference": "s3://kyc-docs/borrower-id/aadhaar.pdf"
    }
  ]
}
```
**Response 201:**
```json
{
  "submittedDocs": [
    {
      "kycDocumentId": "8c8c4dd4-9dd1-4afe-90d0-1ce84a52658f",
      "borrowerId": "b550e840-e29b-41d4-a716-446655440001",
      "documentType": "PAN_CARD",
      "status": "PENDING",
      "documentReference": "s3://kyc-docs/borrower-id/pan.pdf"
    }
  ]
}
```

#### PUT /api/v1/admin/kyc/{kycDocumentId}/verify
**Role:** `TENANT_ADMIN (JWT with tenantId claim)`
**Validation rules:**
- Only non-archived documents in `PENDING` status can be verified/rejected.

**Request (verified):**
```json
{ "decision": "VERIFIED" }
```
**Request (rejected):**
```json
{ "decision": "REJECTED", "rejectionReason": "Document mismatch" }
```
**Response 200:**
```json
{
  "kycDocumentId": "8c8c4dd4-9dd1-4afe-90d0-1ce84a52658f",
  "status": "VERIFIED",
  "verifiedAt": "2026-03-03T10:10:00Z"
}
```

#### GET /api/v1/admin/kyc/pending
**Role:** `TENANT_ADMIN (JWT with tenantId claim)`

Returns all non-archived KYC documents with status `PENDING` for the admin tenant.

#### POST /api/v1/credit-profile
**Role:** `BORROWER (JWT with tenantId + borrowerId claims)`
**Validation rules:**
- Allowed only when borrower status is `KYC_VERIFIED`.
- Credit profile setup is one-time; repeated submissions return `409 CONFLICT`.
- `employmentType` max length: 40.
- `employerName` max length: 180.

**Request:**
```json
{
  "creditScore": 720,
  "creditBureau": "CIBIL",
  "monthlyIncome": 75000,
  "existingEmiObligations": 5000,
  "employmentType": "SALARIED",
  "employerName": "Infosys Ltd",
  "employmentMonths": 36
}
```
**Response 201:**
```json
{
  "creditProfileId": "f4b0a440-d8d8-4a27-a249-e645f4b6f00b",
  "borrowerId": "b550e840-e29b-41d4-a716-446655440001",
  "creditScore": 720,
  "creditBureau": "CIBIL",
  "dtiRatio": 6.67,
  "eligibilityCategory": "NEAR_PRIME"
}
```

---

### FLOW 3 — Loan Application

#### POST /api/v1/loans/applications
**Role:** `BORROWER`
**Validation rules:**
- `loanProductKey` max length: 100.
- `loanPurpose` max length: 120.
- `requestedDisbursementDate` must be today or a future date when provided.

**Request:**
```json
{
  "borrowerId": "b550e840-e29b-41d4-a716-446655440001",
  "loanProductKey": "LP_ABC_001",
  "requestedAmount": 500000,
  "requestedTenureMonths": 36,
  "loanPurpose": "HOME_RENOVATION",
  "requestedDisbursementDate": "2026-04-01"
}
```
**Response 201:**
```json
{
  "applicationId": "app_001",
  "borrowerId": "b550e840...",
  "status": "APPLICATION_SUBMITTED",
  "loanState": "APPLICATION_SUBMITTED",
  "eligibilityResult": {
    "eligible": true,
    "riskCategory": "LOW",
    "maxApprovedAmount": 600000
  },
  "offers": [
    {
      "offerId": "offer_12m",
      "tenureMonths": 12,
      "requestedAmount": 500000,
      "emiAmount": 45137.29,
      "totalInterest": 41647.48,
      "effectiveAnnualRate": 14.5,
      "processingFee": 7500,
      "totalPayable": 541647.48
    },
    {
      "offerId": "offer_24m",
      "tenureMonths": 24,
      "requestedAmount": 500000,
      "emiAmount": 24197.08,
      "totalInterest": 80729.92,
      "effectiveAnnualRate": 14.5,
      "processingFee": 7500,
      "totalPayable": 580729.92
    },
    {
      "offerId": "offer_36m",
      "tenureMonths": 36,
      "requestedAmount": 500000,
      "emiAmount": 17217.77,
      "totalInterest": 119839.72,
      "effectiveAnnualRate": 14.5,
      "processingFee": 7500,
      "totalPayable": 619839.72
    }
  ],
  "submittedAt": "2024-01-15T12:00:00Z",
  "offersExpiresAt": "2024-01-17T12:00:00Z"
}
```

#### GET /api/v1/loans/applications/{applicationId}/offers
**Role:** `BORROWER`

**Response 200:** Same `offers` array as above.

#### POST /api/v1/loans/applications/{applicationId}/offers/select
**Role:** `BORROWER`

**Request:**
```json
{
  "offerId": "offer_36m"
}
```
**Response 200:**
```json
{
  "applicationId": "app_001",
  "selectedOfferId": "offer_36m",
  "status": "OFFER_SELECTED",
  "loanState": "OFFER_SELECTED",
  "selectedAt": "2024-01-15T12:10:00Z"
}
```

#### GET /api/v1/loans/applications/{applicationId}
**Role:** `BORROWER`, `TENANT_ADMIN`

**Response 200:** Same shape as submit response with latest `status`, `loanState`, and `offers`.

---

### FLOW 4 — Document Submission

#### POST /api/v1/loans/{applicationId}/documents
**Request:**
```json
{
  "documentType": "INCOME_PROOF",
  "documentSubType": "SALARY_SLIP",
  "documentReference": "s3://loan-docs/app_001/salary_slip.pdf",
  "documentMonth": "2023-12"
}
```
**Response 201:**
```json
{
  "documentId": "doc_001",
  "applicationId": "app_001",
  "documentType": "INCOME_PROOF",
  "status": "PENDING",
  "uploadedAt": "2024-01-15T13:00:00Z"
}
```

#### GET /api/v1/underwriter/queue
**Response 200:**
```json
{
  "queue": [
    {
      "applicationId": "app_001",
      "borrowerName": "Rahul Sharma",
      "requestedAmount": 500000,
      "documentsSubmitted": 3,
      "documentsVerified": 1,
      "assignedTo": null,
      "priorityScore": 75
    }
  ],
  "total": 1
}
```

#### POST /api/v1/loans/{applicationId}/decision
**Request:** `{ "decision": "APPROVE", "selectedOfferId": "offer_36m", "remarks": "All documents verified" }`
**Response 200:**
```json
{
  "applicationId": "app_001",
  "decision": "APPROVE",
  "loanState": "APPROVED",
  "mambuLoanAccountId": "loan_mambu_001",
  "approvedAmount": 500000,
  "approvedTenureMonths": 36,
  "decidedAt": "2024-01-15T14:00:00Z"
}
```

---

### FLOW 5 — Disbursement

#### POST /api/v1/loans/{applicationId}/disburse
**Request:**
```json
{
  "disbursementDate": "2024-02-01",
  "firstRepaymentDate": "2024-03-01",
  "notes": "Flow5 disbursement",
  "stripeDestinationAccountId": "acct_1ABCxyz123"
}
```
For local Docker testing, mock transfer mode supports destination ids with prefix `acct_mock_` (example: `acct_mock_test_001`).

**Response 200:**
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "mambuLoanId": "loan_mambu_001",
  "status": "ACTIVE_REPAYMENT",
  "loanState": "ACTIVE_REPAYMENT",
  "disbursementStatus": "COMPLETED",
  "mambuDisbursementTxnId": "txn_disb_001",
  "stripeTransferId": "tr_12345",
  "netDisbursedAmount": 492500.00,
  "processingFeeCharged": 7500.00,
  "disbursedAt": "2024-02-01T09:00:00Z",
  "firstRepaymentDate": "2024-03-01",
  "totalInstallments": 36
}
```

#### GET /api/v1/loans/{applicationId}/disbursement-status
**Response 200:**
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "status": "ACTIVE_REPAYMENT",
  "loanState": "ACTIVE_REPAYMENT",
  "disbursementStatus": "COMPLETED",
  "mambuDisbursementTxnId": "txn_disb_001",
  "stripeTransferId": "tr_12345",
  "netDisbursedAmount": 492500.00,
  "processingFeeCharged": 7500.00,
  "disbursedAt": "2024-02-01T09:00:00Z",
  "firstRepaymentDate": "2024-03-01",
  "scheduleFetchFailed": false,
  "mambuDisburseSyncFailed": false,
  "failureReason": null
}
```

#### GET /api/v1/loans/{applicationId}/schedule
**Response 409:** when borrower tries before loan reaches `ACTIVE_REPAYMENT` with successful disbursement completion.
**Response 200:**
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "mambuLoanId": "loan_mambu_001",
  "totalInstallments": 36,
  "firstDueDate": "2024-03-01",
  "lastDueDate": "2027-02-01",
  "installments": [
    {
      "installmentNumber": 1,
      "dueDate": "2024-03-01",
      "principalAmount": 12551.10,
      "interestAmount": 4666.67,
      "totalDue": 17217.77,
      "status": "PENDING",
      "mambuInstallmentState": "PENDING"
    }
  ]
}
```

#### GET /api/v1/loans/{applicationId}/disbursement-details
**Response 200:**
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "mambuLoanId": "loan_mambu_001",
  "status": "ACTIVE_REPAYMENT",
  "loanState": "ACTIVE_REPAYMENT",
  "disbursementStatus": "COMPLETED",
  "mambuDisbursementTxnId": "txn_disb_001",
  "stripeTransferId": "tr_12345",
  "netDisbursedAmount": 492500.00,
  "processingFeeCharged": 7500.00,
  "disbursedAt": "2024-02-01T09:00:00Z",
  "firstRepaymentDate": "2024-03-01",
  "totalInstallments": 36
}
```

---

### FLOW 6 — EMI Collection

#### GET /api/v1/loans/{applicationId}/installments
**Response 200:**
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "borrowerId": "550e8400-e29b-41d4-a716-446655440111",
  "mambuLoanId": "loan_mambu_001",
  "totalInstallments": 36,
  "installments": [
    {
      "installmentId": "550e8400-e29b-41d4-a716-446655440121",
      "installmentNumber": 1,
      "dueDate": "2024-03-01",
      "principalAmount": 12551.10,
      "interestAmount": 4666.67,
      "totalDue": 17217.77,
      "paymentStatus": "PAID",
      "stripePaymentStatus": "succeeded",
      "stripePaymentIntentId": "pi_3abc123",
      "paidDate": "2024-03-01",
      "retryCount": 0
    }
  ]
}
```

#### GET /api/v1/loans/{applicationId}/payment-history
**Response 200:**
```json
{
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "borrowerId": "550e8400-e29b-41d4-a716-446655440111",
  "payments": [
    {
      "installmentId": "550e8400-e29b-41d4-a716-446655440121",
      "installmentNumber": 1,
      "paidAmount": 17217.77,
      "paidDate": "2024-03-01",
      "stripePaymentIntentId": "pi_3abc123",
      "stripeStatus": "succeeded",
      "mambuTransactionId": "txn_rep_001",
      "mambuTransactionKey": "8a8abf1b",
      "status": "PAID",
      "attemptedAt": "2024-03-01T09:00:03Z"
    }
  ]
}
```

#### GET /api/v1/admin/emi/due-today
**Query:** `?paymentStatus=PENDING&page=0&size=20`
**Response 200:**
```json
{
  "page": 0,
  "size": 20,
  "total": 42,
  "installments": [
    {
      "installmentId": "550e8400-e29b-41d4-a716-446655440121",
      "installmentNumber": 3,
      "dueDate": "2024-03-01",
      "principalAmount": 12551.10,
      "interestAmount": 4666.67,
      "totalDue": 17217.77,
      "paymentStatus": "PENDING",
      "stripePaymentStatus": null,
      "stripePaymentIntentId": null,
      "paidDate": null,
      "retryCount": 0
    }
  ]
}
```

#### GET /api/v1/admin/emi/failed
**Response 200:** same pagination wrapper with installments in `RETRY_SCHEDULED`.

#### GET /api/v1/admin/emi/overdue
**Response 200:** same pagination wrapper with installments in `OVERDUE`.

#### POST /api/v1/admin/emi/{installmentId}/waive
**Request:**
```json
{ "reason": "Medical emergency waiver approved by tenant admin" }
```
**Response 200:** empty body.

#### GET /api/v1/admin/reconciliation/{date}
`{date}` format: `YYYY-MM-DD`
**Response 200:**
```json
{
  "reportDate": "2024-03-01",
  "totalDueCount": 42,
  "totalCollectedCount": 36,
  "totalFailedCount": 6,
  "totalAmountDue": 723145.10,
  "totalAmountCollected": 619839.72,
  "mambuTotalPosted": 619839.72,
  "discrepancyAmount": 0.00,
  "reconciled": true,
  "reviewedBy": null,
  "reviewedAt": null
}
```

#### Scheduler/Async Behavior (Flow 6)
- 09:00 IST: collection dispatch
- 18:00 IST: retry dispatch
- 23:00 IST: tenant reconciliation report generation
- every 5 minutes: Mambu sync retry for collected-but-not-posted installments

#### Idempotency Key
`idempotency:EMI:{mambuLoanId}:{emiNumber}:{tenantId}`

Used in:
- Redis pre-check lock
- Stripe `Idempotency-Key` during PaymentIntent creation
- Mambu repayment `externalId`

#### POST /api/v1/webhooks/stripe
Stripe webhook receiver for Flow 5 + Flow 6.

**Flow 6 handled events:**
- `payment_intent.succeeded`
- `payment_intent.payment_failed`
- `payment_intent.requires_action`
- `payment_intent.canceled`

**Headers:** `Stripe-Signature: t=xxx,v1=xxx`  
**Behavior:** verifies signature; returns `200` quickly; processes Flow 6 payload asynchronously.  
**Response 200:** `{ "received": true }`

**Flow 5 backward compatibility:** also handles `transfer.failed` / `payout.failed`.
```json
{
  "received": true
}
```

**Webhook Rejection 400:**
```json
{
  "received": false,
  "error": "INVALID_SIGNATURE"
    }
```

---

### FLOW 7 — Prepayment

#### POST /api/v1/loans/{loanAccountId}/prepayment/simulate
**Role:** `BORROWER`

**Request (partial):**
```json
{
  "prepaymentType": "PARTIAL",
  "amount": 100000,
  "requestedDate": "2026-03-04"
}
```

**Request (full foreclosure):**
```json
{
  "prepaymentType": "FULL",
  "requestedDate": "2026-03-04"
}
```

**Response 200:**
```json
{
  "quoteId": "550e8400-e29b-41d4-a716-446655440901",
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "loanAccountId": "LN_ABC_001",
  "prepaymentType": "PARTIAL",
  "principalOutstanding": 400000.00,
  "accruedInterest": 0.00,
  "prepaymentPenalty": 0.00,
  "totalPayable": 100000.00,
  "quoteGeneratedAt": "2026-03-04T15:40:00Z",
  "quoteExpiresAt": "2026-03-04T15:55:00Z",
  "options": [
    {
      "option": "REDUCE_TENURE_KEEP_EMI",
      "revisedOutstanding": 300000.00,
      "estimatedEmi": 17000.00,
      "estimatedTenureMonths": 18,
      "note": "Keep current EMI and reduce remaining tenure"
    },
    {
      "option": "REDUCE_EMI_KEEP_TENURE",
      "revisedOutstanding": 300000.00,
      "estimatedEmi": 12500.00,
      "estimatedTenureMonths": 24,
      "note": "Keep current tenure and reduce monthly EMI"
    }
  ]
}
```

#### POST /api/v1/loans/{loanAccountId}/prepayment/execute
**Role:** `BORROWER`

**Request:**
```json
{
  "prepaymentType": "FULL",
  "requestedAmount": 412100.00,
  "quoteId": "550e8400-e29b-41d4-a716-446655440901",
  "idempotencyKey": "FORECLOSE:LN_ABC_001:tenant_001"
}
```

**Response 200:**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440902",
  "applicationId": "550e8400-e29b-41d4-a716-446655440101",
  "loanAccountId": "LN_ABC_001",
  "prepaymentType": "FULL",
  "prepaymentOption": null,
  "requestedAmount": 412100.00,
  "stripePaymentIntentId": "pi_3abc123",
  "stripeStatus": "requires_confirmation",
  "mambuTransactionId": null,
  "status": "PAYMENT_PENDING",
  "loanState": "ACTIVE_REPAYMENT",
  "nocAvailable": false,
  "message": "Payment initiated; awaiting Stripe confirmation",
  "createdAt": "2026-03-04T15:41:00Z",
  "updatedAt": "2026-03-04T15:41:00Z"
}
```

#### GET /api/v1/loans/{loanAccountId}/prepayment/{requestId}
**Role:** `BORROWER`

Returns latest status for a borrower-initiated prepayment request.

#### GET /api/v1/admin/prepayments
**Role:** `TENANT_ADMIN`

Read-only prepayment/foreclosure activity list for portfolio dashboard.

#### GET /api/v1/loans/{loanAccountId}/noc
**Role:** `BORROWER` or `TENANT_ADMIN`

Returns NOC metadata only after successful foreclosure (`CLOSED_OBLIGATIONS_MET`).

---

### FLOW 8 — NPA

#### GET /api/v1/admin/npa
**Role:** `TENANT_ADMIN`
**Query:** `?page=0&size=20`
**Response 200:**
```json
{
  "npaloans": [
    {
      "loanAccountId": "laccount_002",
      "borrowerName": "Amit Kumar",
      "outstandingAmount": 380000,
      "daysPassedDue": 95,
      "npaFlaggedAt": "2024-05-15T00:00:00Z",
      "recoveryStage": "DAY_30_LEGAL_ESCALATION",
      "mambuState": "NON_PERFORMING"
    }
  ],
  "total": 1
}
```

#### POST /api/v1/admin/npa/{loanAccountId}/override
**Role:** `TENANT_ADMIN`
**Request:** `{ "overrideReason": "Settlement agreed", "adminNote": "Borrower paid 50% lump sum" }`
**Response 200:**
```json
{ "loanAccountId": "laccount_002", "npaLockCleared": true, "overriddenAt": "2024-06-01T10:00:00Z" }
```

**Behavior Notes:**
- Loans are auto-flagged when any unpaid installment reaches `90+ DPD`.
- Day `1/7/30` internal escalation stages are tracked as `DAY_1_REMINDER`, `DAY_7_ESCALATION`, `DAY_30_LEGAL_ESCALATION`.
- Day 30 escalation updates internal state to legal escalation and keeps Mambu account state as `NON_PERFORMING`.

---

### FLOW 9 — Analytics

#### GET /api/v1/admin/analytics/portfolio
**Query:** `?period=MONTHLY&year=2024&month=5`
**Response 200:**
```json
{
  "tenantId": "550e8400...",
  "period": "2024-05",
  "metrics": {
    "totalLoansActive": 142,
    "totalDisbursedAmount": 71500000,
    "totalOutstandingAmount": 54200000,
    "totalCollectedThisMonth": 3850000,
    "npaCount": 4,
    "npaRatio": 2.82,
    "onTimePaymentRate": 94.5,
    "averageLoanSize": 503521
  },
  "dpdBuckets": {
    "current": 132,
    "dpd1to30": 6,
    "dpd31to60": 2,
    "dpd61to90": 2,
    "dpd90plus": 4
  },
  "generatedAt": "2024-06-01T10:00:00Z",
  "cachedUntil": "2024-06-01T10:15:00Z"
}
```

---

### FLOW 11 — Chatbot

#### POST /api/v1/chatbot/message
**Request:**
```json
{
  "sessionId": "sess_abc123",
  "message": "What is my EMI due date and outstanding amount?",
  "loanAccountId": "laccount_001"
}
```
**Response 200:**
```json
{
  "sessionId": "sess_abc123",
  "response": "Your next EMI of ₹17,217.77 is due on March 1, 2024. Your current outstanding principal is ₹4,87,448.90.",
  "toolsUsed": ["getRepaymentSchedule"],
  "conversationTurn": 3,
  "sessionExpiresAt": "2024-01-15T12:30:00Z"
}
```

---

## MAMBU MOCK SERVICE ENDPOINTS

Base URL: http://localhost:8081/api/v2

---

### Branches

#### POST /api/v2/branches
**Request:**
```json
{
  "name": "ABC Finance - Main Branch",
  "id": "branch_abc_001",
  "state": "ACTIVE",
  "address": { "country": "IN", "city": "Bengaluru" },
  "emailAddress": "admin@abcfinance.com",
  "phoneNumber": "+919876543210",
  "notes": "Tenant: ABC Finance Ltd"
}
```
**Response 201:**
```json
{
  "encodedKey": "8a818e8a7f2b3c4d5e6f7a8b",
  "id": "branch_abc_001",
  "name": "ABC Finance - Main Branch",
  "state": "ACTIVE",
  "creationDate": "2024-01-15T10:30:00+05:30",
  "lastModifiedDate": "2024-01-15T10:30:00+05:30",
  "address": { "country": "IN", "city": "Bengaluru" },
  "emailAddress": "admin@abcfinance.com",
  "phoneNumber": "+919876543210"
}
```

---

### Loan Products

#### POST /api/v2/loanproducts
**Request:**
```json
{
  "name": "Personal Loan - ABC",
  "id": "LP_ABC_001",
  "type": "FIXED_TERM_LOAN",
  "loanAmountSettings": {
    "defaultAmount": { "value": 100000 },
    "minAmount": { "value": 10000 },
    "maxAmount": { "value": 1000000 }
  },
  "scheduleSettings": {
    "defaultRepaymentPeriodCount": 12,
    "defaultRepaymentPeriodUnit": "MONTHS",
    "repaymentScheduleMethod": "STANDARD"
  },
  "interestSettings": {
    "defaultInterestRate": { "value": 14.5 },
    "interestChargeFrequency": "ANNUALIZED",
    "interestCalculationMethod": "DECLINING_BALANCE"
  },
  "currency": { "code": "INR" },
  "forBranchKey": "8a818e8a7f2b3c4d5e6f7a8b",
  "state": "ACTIVE"
}
```
**Response 201:**
```json
{
  "encodedKey": "8a818e8b7f2b3c4d5e6f7a9c",
  "id": "LP_ABC_001",
  "name": "Personal Loan - ABC",
  "state": "ACTIVE",
  "type": "FIXED_TERM_LOAN",
  "creationDate": "2024-01-15T10:31:00+05:30",
  "lastModifiedDate": "2024-01-15T10:31:00+05:30",
  "currency": { "code": "INR", "name": "Indian Rupee" },
  "loanAmountSettings": { "minAmount": { "value": 10000 }, "maxAmount": { "value": 1000000 }, "defaultAmount": { "value": 100000 } },
  "interestSettings": { "defaultInterestRate": { "value": 14.5 }, "interestCalculationMethod": "DECLINING_BALANCE", "interestChargeFrequency": "ANNUALIZED" },
  "scheduleSettings": { "defaultRepaymentPeriodCount": 12, "defaultRepaymentPeriodUnit": "MONTHS" }
}
```

---

### Clients (Borrowers)

#### POST /api/v2/clients
**Request:**
```json
{
  "firstName": "Rahul",
  "lastName": "Sharma",
  "emailAddress": "rahul.sharma@email.com",
  "mobilePhone": "+919876543210",
  "birthDate": "1990-05-15",
  "gender": "MALE",
  "assignedBranchKey": "8a818e8a7f2b3c4d5e6f7a8b",
  "state": "ACTIVE",
  "idDocuments": [
    { "documentType": "PAN", "documentId": "ABCDE1234F", "issuingAuthority": "Income Tax Dept India" },
    { "documentType": "NATIONAL_ID", "documentId": "XXXX-XXXX-1234" }
  ],
  "addresses": [
    { "line1": "123 MG Road", "city": "Bengaluru", "region": "Karnataka", "postcode": "560001", "country": "IN", "indexInList": 0 }
  ]
}
```
**Response 201:**
```json
{
  "encodedKey": "8a818e8c7f2b3c4d5e6f7aad",
  "id": "cli_rahul_001",
  "firstName": "Rahul",
  "lastName": "Sharma",
  "fullName": "Rahul Sharma",
  "emailAddress": "rahul.sharma@email.com",
  "mobilePhone": "+919876543210",
  "birthDate": "1990-05-15",
  "gender": "MALE",
  "state": "ACTIVE",
  "clientRole": { "encodedKey": "8a818e8d00000001" },
  "creationDate": "2024-01-15T11:00:00+05:30",
  "lastModifiedDate": "2024-01-15T11:00:00+05:30",
  "assignedBranchKey": "8a818e8a7f2b3c4d5e6f7a8b",
  "idDocuments": [ /* echo back */ ],
  "addresses": [ /* echo back */ ]
}
```

---

### Loans (Simulation)

#### POST /api/v2/loans:simulate
**Request:**
```json
{
  "loanAmount": { "value": 500000 },
  "interestRate": { "value": 14.5 },
  "repaymentInstallments": 36,
  "loanProductTypeKey": "8a818e8b7f2b3c4d5e6f7a9c",
  "clientKey": "8a818e8c7f2b3c4d5e6f7aad",
  "disbursementDetails": { "expectedDisbursementDate": "2024-02-01" },
  "repaymentScheduleMethod": "STANDARD",
  "interestCalculationMethod": "DECLINING_BALANCE"
}
```
**Response 200:**
```json
{
  "loanAmount": { "value": 500000 },
  "repaymentInstallments": 36,
  "interestRate": { "value": 14.5 },
  "annualPercentageRate": 14.5,
  "periodicPayment": 17217.77,
  "totalInterestCharged": 119839.72,
  "totalAmountRepaid": 619839.72,
  "repaymentSchedule": {
    "installments": [
      {
        "number": 1,
        "dueDate": "2024-03-01",
        "principal": { "amount": { "value": 12551.10 }, "tax": { "value": 0 } },
        "interest": { "amount": { "value": 4666.67 }, "tax": { "value": 0 } },
        "fee": { "amount": { "value": 0 }, "tax": { "value": 0 } },
        "penalty": { "amount": { "value": 0 }, "tax": { "value": 0 } },
        "totalDue": { "value": 17217.77 }
      }
    ]
  }
}
```

### Loan Account Creation

#### POST /api/v2/loans
**Request:**
```json
{
  "clientKey": "8a818e8c7f2b3c4d5e6f7aad",
  "productTypeKey": "8a818e8b7f2b3c4d5e6f7a9c",
  "loanAmount": { "value": 500000 },
  "interestRate": { "value": 14.5 },
  "repaymentInstallments": 36,
  "disbursementDetails": { "expectedDisbursementDate": "2024-02-01" },
  "assignedBranchKey": "8a818e8a7f2b3c4d5e6f7a8b"
}
```
**Response 201:**
```json
{
  "encodedKey": "8a818e9a7f2b3c4d5e6f7abe",
  "id": "loan_mambu_001",
  "accountState": "PENDING_APPROVAL",
  "loanAmount": { "value": 500000 },
  "interestRate": { "value": 14.5 },
  "repaymentInstallments": 36,
  "clientKey": "8a818e8c7f2b3c4d5e6f7aad",
  "productTypeKey": "8a818e8b7f2b3c4d5e6f7a9c",
  "creationDate": "2024-01-15T14:00:00+05:30",
  "lastModifiedDate": "2024-01-15T14:00:00+05:30"
}
```

### Loan Approval

#### POST /api/v2/loans/{loanId}/approve
**Request:** `{ "notes": "All documents verified by underwriter", "date": "2024-01-15" }`
**Response 200:**
```json
{
  "encodedKey": "8a818e9a7f2b3c4d5e6f7abe",
  "id": "loan_mambu_001",
  "accountState": "APPROVED",
  "approvedDate": "2024-01-15T14:01:00+05:30",
  "lastModifiedDate": "2024-01-15T14:01:00+05:30"
}
```

### Loan Disbursement

#### POST /api/v2/loans/{loanId}/disbursement
**Request:**
```json
{
  "notes": "Loan disbursed to borrower account",
  "firstRepaymentDate": "2024-03-01",
  "disbursementDate": "2024-02-01",
  "externalId": "DISB-laccount_001-20240201",
  "transactionDetails": {
    "transactionChannelId": "BANK_TRANSFER",
    "fields": [
      { "fieldSetId": "transferDetails", "customFieldValues": [
          { "customField": { "id": "accountNumber" }, "value": "1234567890" },
          { "customField": { "id": "bankIFSC" }, "value": "SBIN0001234" }
        ]
      }
    ]
  }
}
```
**Response 201:**
```json
{
  "encodedKey": "8a818e9b7f2b3c4d5e6f7abf",
  "id": "txn_disb_001",
  "type": "DISBURSEMENT",
  "amount": 492500,
  "fees": { "amount": 7500 },
  "notes": "Loan disbursed to borrower account",
  "creationDate": "2024-02-01T09:00:00+05:30",
  "valueDate": "2024-02-01",
  "externalId": "DISB-laccount_001-20240201",
  "parentAccountKey": "8a818e9a7f2b3c4d5e6f7abe",
  "parentAccountId": "loan_mambu_001"
}
```

### Repayment Schedule

#### GET /api/v2/loans/{loanId}/schedule
**Response 200:**
```json
{
  "installments": [
    {
      "number": 1,
      "dueDate": "2024-03-01",
      "lastPaidDate": null,
      "state": "PENDING",
      "principal": { "amount": { "value": 12551.10 }, "paid": { "value": 0 }, "due": { "value": 12551.10 } },
      "interest": { "amount": { "value": 4666.67 }, "paid": { "value": 0 }, "due": { "value": 4666.67 } },
      "fee": { "amount": { "value": 0 }, "paid": { "value": 0 }, "due": { "value": 0 } },
      "penalty": { "amount": { "value": 0 }, "paid": { "value": 0 }, "due": { "value": 0 } }
    }
  ]
}
```

### Repayment Posting

#### POST /api/v2/loans/{loanId}/repayments
**Request:**
```json
{
  "amount": 17217.77,
  "date": "2024-03-01",
  "notes": "EMI 1 collected via Stripe",
  "externalId": "EMI:laccount_001:1:tenant_abc",
  "transactionDetails": {
    "transactionChannelId": "ONLINE_PAYMENT",
    "transactionChannelKey": "8a818e8e00000001"
  }
}
```
**Response 201:**
```json
{
  "encodedKey": "8a818e9c7f2b3c4d5e6f7ac0",
  "id": "txn_rep_001",
  "type": "REPAYMENT",
  "amount": 17217.77,
  "fees": { "amount": 0 },
  "notes": "EMI 1 collected via Stripe",
  "externalId": "EMI:laccount_001:1:tenant_abc",
  "creationDate": "2024-03-01T09:00:00+05:30",
  "valueDate": "2024-03-01",
  "parentAccountKey": "8a818e9a7f2b3c4d5e6f7abe",
  "parentAccountId": "loan_mambu_001"
}
```

### Early Repayment Preview

#### GET /api/v2/loans/{loanId}/preview-early-repayment
**Query:** `?amount=224000&paymentDate=2024-06-01`
**Response 200:**
```json
{
  "loanId": "loan_mambu_001",
  "remainingPrincipal": { "value": 420000 },
  "accruedInterest": { "value": 1800 },
  "prepaymentPenalty": { "value": 4000 },
  "feesBalance": { "value": 0 },
  "totalSettlementAmount": { "value": 225800 },
  "rebate": { "value": 0 },
  "earlyRepaymentDate": "2024-06-01"
}
```

### NPA Flagging

#### PATCH /api/v2/loans/{loanId}
**Request:**
```json
{
  "loanState": "NON_PERFORMING",
  "notesArray": [
    { "notes": "90+ DPD — Flagged as NPA by system on 2024-05-15", "creationDate": "2024-05-15" }
  ]
}
```
**Response 200:**
```json
{
  "encodedKey": "8a818e9a7f2b3c4d5e6f7abe",
  "id": "loan_mambu_001",
  "accountState": "NON_PERFORMING",
  "lastModifiedDate": "2024-05-15T00:00:00+05:30"
}
```

### Portfolio Reporting

#### GET /api/v2/loans
**Query:** `?branchId=branch_abc_001&loanState=ACTIVE&limit=100&offset=0`
**Response 200:**
```json
{
  "loans": [
    {
      "encodedKey": "...",
      "id": "loan_mambu_001",
      "accountState": "ACTIVE",
      "loanAmount": { "value": 500000 },
      "principalBalance": { "value": 487448.90 },
      "interestBalance": { "value": 0 },
      "feesBalance": { "value": 0 },
      "penaltyBalance": { "value": 0 },
      "clientKey": "8a818e8c7f2b3c4d5e6f7aad"
    }
  ],
  "pagingDetails": {
    "totalCount": 142,
    "pageSize": 100,
    "pageNum": 0
  }
}
```

---

## PAYMENT GATEWAY SERVICE ENDPOINTS

Base URL: http://localhost:8082

### POST /api/v1/payment-intents
**Request:**
```json
{
  "amount": 1721777,
  "currency": "inr",
  "customerId": "cus_stripe_001",
  "metadata": {
    "loanAccountId": "laccount_001",
    "emiNumber": "1",
    "tenantId": "550e8400...",
    "idempotencyKey": "EMI:laccount_001:1:tenant_abc"
  },
  "confirmationMethod": "automatic",
  "confirm": false
}
```
**Response 201:**
```json
{
  "id": "pi_3abc123xyz",
  "object": "payment_intent",
  "amount": 1721777,
  "currency": "inr",
  "status": "requires_payment_method",
  "clientSecret": "pi_3abc123xyz_secret_def456",
  "metadata": { /* echo back */ },
  "created": 1706793600
}
```

### POST /api/v1/webhooks/simulate
Simulate Stripe webhook for testing.
**Request:**
```json
{
  "eventType": "payment_intent.succeeded",
  "paymentIntentId": "pi_3abc123xyz",
  "loanAccountId": "laccount_001",
  "emiNumber": 1,
  "tenantId": "550e8400..."
}
```
**Response 200:** `{ "webhookDelivered": true, "platformResponse": 200 }`

---

## ERROR RESPONSE FORMAT

All APIs return errors in this format:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "errorCode": "LOAN_NOT_FOUND",
  "message": "Loan account laccount_999 not found for tenant 550e8400",
  "path": "/api/v1/loans/laccount_999/schedule",
  "requestId": "req_abc123",
  "tenantId": "550e8400..."
}
```

## Standard HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request / Validation Error |
| 401 | Unauthorized (invalid/missing JWT) |
| 403 | Forbidden (insufficient role) |
| 404 | Resource not found |
| 409 | Conflict (duplicate, idempotency violation) |
| 422 | Unprocessable Entity (business rule violation) |
| 429 | Rate limit exceeded |
| 500 | Internal Server Error |
| 502 | Mambu/External service error |
