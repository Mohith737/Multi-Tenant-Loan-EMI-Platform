# Gate 1 Plan - Flow 2: Borrower Registration and KYC

## 1. Feature Summary
Flow 2 will onboard a borrower under an existing tenant, capture KYC documents, verify KYC status transitions, and store credit profile details used by downstream loan eligibility flows. During borrower registration, the platform will create a corresponding Mambu Client via `POST /api/v2/clients` and persist `mambuClientId` on the borrower record.

This plan also includes a mock KYC verification component: when a KYC verification request contains all required valid fields, the mock verifier returns `true` and the document can move from `PENDING` to `VERIFIED`; otherwise it returns `false` and the document is marked `REJECTED`.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.borrower.model`
- `com.loanplatform.loan_platform.domain.borrower.repository`
- `com.loanplatform.loan_platform.domain.borrower.service`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.kyc`
- `com.loanplatform.loan_platform.adapter.outbound.kafka`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.loan_platform.multitenancy`

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V2__create_borrower_tables.sql` | Create borrower, KYC, and credit-profile tables with tenant-aware indexes and constraints. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/model/Borrower.java` | Borrower aggregate with tenant-scoped PII and Mambu client linkage. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/model/BorrowerAddress.java` | Embedded borrower address value object. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/model/KycDocument.java` | KYC submission entity with document metadata and status. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/model/KycStatus.java` | Enum: `PENDING`, `VERIFIED`, `REJECTED`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/model/CreditProfile.java` | Borrower credit profile entity. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/model/KycDocumentType.java` | Enum: `AADHAAR`, `PAN`, `PASSPORT`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/repository/BorrowerRepository.java` | JPA repository with tenant-aware lookup methods. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/repository/KycDocumentRepository.java` | KYC document repository. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/repository/CreditProfileRepository.java` | Credit profile repository. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/service/BorrowerService.java` | Flow orchestration: borrower register, KYC submit/verify, credit profile create. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/BorrowerUseCase.java` | Inbound contract for Flow 2 APIs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/KycVerificationPort.java` | Outbound contract for mock KYC verification service. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/kyc/MockKycVerificationAdapter.java` | Implements mock verification rule and returns boolean. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/BorrowerController.java` | Flow 2 REST endpoints for register, KYC submit/verify, and credit profile. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuClientCreateRequest.java` | Request DTO for Mambu `POST /api/v2/clients`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuClientResponse.java` | Response DTO for Mambu client creation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/BorrowerRegistrationRequest.java` | Borrower create API request schema and validation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/KycSubmissionRequest.java` | KYC submit request schema and validation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/KycVerificationRequest.java` | KYC verify request schema and validation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/CreditProfileRequest.java` | Credit profile request schema and validation. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/BorrowerResponse.java` | Borrower response with masked Aadhaar and KYC state. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/KycDocumentResponse.java` | KYC submission/verification response payload. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/CreditProfileResponse.java` | Credit profile response payload. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/BorrowerMapper.java` | MapStruct mappings for borrower and related DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/BorrowerNotFoundException.java` | Not-found error type for borrower resources. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/KycVerificationFailedException.java` | Error type for failed/invalid KYC verification attempts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/borrower/BorrowerServiceTest.java` | Unit tests for Flow 2 domain/service behavior. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/borrower/BorrowerRegistrationIntegrationTest.java` | Integration tests for Flow 2 APIs, tenant isolation, and Mambu stubs. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Add Feign method for `POST /api/v2/clients`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Add borrower-to-Mambu client mapping logic via `MambuClient`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Add `createClient(...)` contract. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Add borrower/KYC-specific exception mappings and validation responses. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add configurable mock KYC behavior flags and validation/security props if required. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Add or align Flow 2 endpoint contracts and examples with implemented request/response shapes. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 2 status to `IN PROGRESS` during Gate 2 and `COMPLETE` after Gate 3. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V2__create_borrower_tables.sql`

### Table: `borrowers`
- `id` UUID PK
- `tenant_id` UUID NOT NULL
- `first_name` VARCHAR(100) NOT NULL
- `last_name` VARCHAR(100) NOT NULL
- `date_of_birth` DATE NOT NULL
- `gender` VARCHAR(20) NOT NULL
- `pan_number` VARCHAR(10) NOT NULL
- `aadhaar_number_encrypted` VARCHAR(512) NOT NULL
- `aadhaar_last_four` VARCHAR(4) NOT NULL
- `email` VARCHAR(180) NOT NULL
- `mobile_number` VARCHAR(20) NOT NULL
- `employment_type` VARCHAR(40) NOT NULL
- `employer_name` VARCHAR(180)
- `monthly_income` NUMERIC(19,2) NOT NULL
- `address_line1` VARCHAR(255) NOT NULL
- `address_city` VARCHAR(100) NOT NULL
- `address_state` VARCHAR(100) NOT NULL
- `address_pincode` VARCHAR(15) NOT NULL
- `address_country` VARCHAR(3) NOT NULL
- `kyc_status` VARCHAR(30) NOT NULL DEFAULT `PENDING`
- `mambu_client_id` VARCHAR(100)
- `mambu_client_encoded_key` VARCHAR(100)
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Indexes and constraints:
- `uk_borrowers_tenant_pan` unique (`tenant_id`, `pan_number`)
- `uk_borrowers_tenant_mobile` unique (`tenant_id`, `mobile_number`)
- `uk_borrowers_tenant_email` unique (`tenant_id`, `email`)
- `idx_borrowers_tenant_id` (`tenant_id`)
- `idx_borrowers_kyc_status` (`tenant_id`, `kyc_status`)
- `idx_borrowers_mambu_client_id` (`mambu_client_id`)

### Table: `kyc_documents`
- `id` UUID PK
- `tenant_id` UUID NOT NULL
- `borrower_id` UUID NOT NULL FK -> `borrowers.id`
- `document_type` VARCHAR(30) NOT NULL (`AADHAAR`, `PAN`, `PASSPORT`)
- `document_number` VARCHAR(64) NOT NULL
- `document_reference` VARCHAR(500) NOT NULL
- `issued_date` DATE
- `expiry_date` DATE
- `status` VARCHAR(20) NOT NULL DEFAULT `PENDING`
- `verified_by` VARCHAR(180)
- `verified_at` TIMESTAMP
- `verification_remarks` VARCHAR(500)
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Indexes:
- `idx_kyc_documents_tenant_borrower` (`tenant_id`, `borrower_id`)
- `idx_kyc_documents_status` (`tenant_id`, `status`)

### Table: `credit_profiles`
- `id` UUID PK
- `tenant_id` UUID NOT NULL
- `borrower_id` UUID NOT NULL FK -> `borrowers.id`
- `credit_score` INTEGER NOT NULL
- `credit_bureau` VARCHAR(50) NOT NULL
- `monthly_income` NUMERIC(19,2) NOT NULL
- `existing_emi_obligations` NUMERIC(19,2) NOT NULL
- `employment_type` VARCHAR(40) NOT NULL
- `employment_months` INTEGER NOT NULL
- `report_fetched_at` TIMESTAMP NOT NULL
- `dti_ratio` NUMERIC(10,2)
- `eligibility_category` VARCHAR(40)
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Indexes:
- `uk_credit_profiles_tenant_borrower` unique (`tenant_id`, `borrower_id`)
- `idx_credit_profiles_tenant_id` (`tenant_id`)

## 6. API Endpoints (Method + Path + Purpose)
| Method | Path | Purpose | Roles |
|---|---|---|---|
| `POST` | `/api/v1/borrowers` | Register borrower under tenant, call Mambu client creation, persist borrower + `mambuClientId`. | `TENANT_ADMIN` |
| `POST` | `/api/v1/borrowers/{borrowerId}/kyc` | Submit KYC document metadata/reference; mark status `PENDING`. | `TENANT_ADMIN`, `BORROWER` (self only) |
| `PUT` | `/api/v1/borrowers/{borrowerId}/kyc/verify` | Verify KYC using mock verification service; transition `PENDING` to `VERIFIED` or `REJECTED`. | `TENANT_ADMIN` |
| `POST` | `/api/v1/borrowers/{borrowerId}/credit-profile` | Create or update borrower credit profile details. | `TENANT_ADMIN` |
| `GET` | `/api/v1/borrowers/{borrowerId}` | Fetch borrower details (PII-safe response). | `TENANT_ADMIN`, `BORROWER` (self only) |

## 7. Mambu Mock API Calls Required
### Outbound Call
`POST /api/v2/clients`

### Request Shape (contract to mirror in adapter)
```json
{
  "firstName": "Rahul",
  "lastName": "Sharma",
  "emailAddress": "rahul.sharma@email.com",
  "mobilePhone": "+919876543210",
  "birthDate": "1990-05-15",
  "gender": "MALE",
  "assignedBranchKey": "<tenant_mambu_branch_encoded_key>",
  "state": "ACTIVE",
  "idDocuments": [
    { "documentType": "PAN", "documentId": "ABCDE1234F", "issuingAuthority": "Income Tax Dept India" },
    { "documentType": "NATIONAL_ID", "documentId": "XXXXXXXX1234" }
  ],
  "addresses": [
    { "line1": "123 MG Road", "city": "Bengaluru", "region": "Karnataka", "postcode": "560001", "country": "IN", "indexInList": 0 }
  ]
}
```

### Expected Response Fields Used
- `id` -> persisted as `mambu_client_id`
- `encodedKey` -> persisted as `mambu_client_encoded_key`
- `state`, `creationDate` -> optional audit logging

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Event |
|---|---|---|
| `borrower.registered.v1` | Produce | Borrower successfully registered and linked with Mambu client. |
| `borrower.kyc.submitted.v1` | Produce | KYC document submitted with `PENDING` status. |
| `borrower.kyc.verified.v1` | Produce | KYC decision emitted (`VERIFIED` or `REJECTED`). |
| `borrower.credit-profile.created.v1` | Produce | Credit profile captured or updated. |
| `borrower.kyc.verify.request.v1` | Consume (optional) | Async trigger for KYC verification if moved to event-based processing later. |

## 9. Redis Keys (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `borrower:create:idempotency:{tenantId}:{requestHash}` | `24h` | Prevent duplicate borrower registration submissions. |
| `kyc:verify:result:{tenantId}:{borrowerId}:{kycDocumentId}` | `30m` | Cache recent KYC verification outcomes for quick re-check and audit correlation. |
| `borrower:read:{tenantId}:{borrowerId}` | `10m` | Cache borrower read model without exposing raw Aadhaar. |

## 10. Stripe Integration Points
Not applicable for Flow 2.

## 11. State Machine Transitions Triggered
- Loan lifecycle state machine transitions: none in Flow 2.
- Borrower/KYC status transitions (domain-managed in this flow):
  - `KYC: PENDING -> VERIFIED`
  - `KYC: PENDING -> REJECTED`

## 12. Security (Roles, Tenant Isolation)
### Roles and authorization
- `POST /api/v1/borrowers`: `TENANT_ADMIN` only.
- `POST /api/v1/borrowers/{borrowerId}/kyc`: `TENANT_ADMIN` or `BORROWER` when `borrowerId` belongs to authenticated borrower.
- `PUT /api/v1/borrowers/{borrowerId}/kyc/verify`: `TENANT_ADMIN` only.
- `POST /api/v1/borrowers/{borrowerId}/credit-profile`: `TENANT_ADMIN` only.
- `GET /api/v1/borrowers/{borrowerId}`: `TENANT_ADMIN` or same `BORROWER`.

### Validation rules
- PAN regex: `^[A-Z]{5}[0-9]{4}[A-Z]$`
- Aadhaar accepted input regex: `^[0-9]{12}$`
- Aadhaar masked output/logging format: `XXXX-XXXX-1234` (only last four visible)
- Mobile regex: `^\\+?[1-9][0-9]{9,14}$`
- Email: standard Bean Validation `@Email`
- DOB must be in past and borrower age >= 18
- Credit score range: 300 to 900

### Aadhaar logging and storage controls
- Never log full Aadhaar in INFO/DEBUG/ERROR logs.
- Log only masked Aadhaar and request correlation IDs.
- Persist encrypted/full Aadhaar in secure DB field plus separate last-four field for display.

### Tenant isolation proof (to be validated in Gate 3 tests)
- Repository queries include `tenantId` in all borrower/KYC/credit-profile lookups.
- API layer derives tenant context from auth/request context; never trusts caller-provided borrower tenant field.
- Integration tests must prove tenant A cannot read/update tenant B borrower/KYC/credit profile (expected `404` or `403`).

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Cross-tenant data leakage | Exposes borrower PII across tenants. | Enforce tenant-scoped queries and role checks; add integration isolation tests for all Flow 2 endpoints. |
| PAN/Aadhaar data exposure in logs | Compliance and legal violation. | Central masking utility + structured logging policy; reject unmasked fields in logs. |
| Duplicate borrower creation | Inconsistent borrower identity and Mambu client duplication. | Tenant-scoped unique constraints + Redis idempotency key + conflict handling. |
| KYC false positives | Invalid users marked verified. | Mock verifier returns `true` only if required fields are valid and consistent with stored document context. |
| External contract drift with Mambu client API | Runtime failures in onboarding borrower to Mambu. | Strict DTO contract matching API.md and integration tests with WireMock/Mambu mock schemas. |

## 14. Mock KYC Service Requirement (Explicit)
- The Flow 2 implementation will include a mock KYC verification service adapter.
- Verification request must include valid required fields: `borrowerId`, `kycDocumentId`, `decision`, and non-blank `remarks`.
- Mock verification result behavior:
  - returns `true` only when request fields are complete and valid for the target document;
  - returns `false` for missing/invalid fields or inconsistent document mapping.
- Service output drives status transition:
  - `true` + `decision=VERIFIED` -> `VERIFIED`
  - `false` or `decision=REJECTED` -> `REJECTED`

## 15. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-2-borrower-registration.md`
- [x] All required Gate 1 sections (a-m) filled
- [ ] Human approval pending

