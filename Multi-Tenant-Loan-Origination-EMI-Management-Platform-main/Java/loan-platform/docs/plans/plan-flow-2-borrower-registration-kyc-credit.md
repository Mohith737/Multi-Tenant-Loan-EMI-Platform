# Gate 1 Plan - Flow 2: Borrower Registration, KYC Submission, Credit Profile Setup

## 1. Feature Summary
Implement public tenant discovery, borrower self-registration, borrower-driven KYC submission, tenant-admin KYC verification, and credit profile setup with mandatory KYC gate enforcement. Persist borrower PAN in encrypted form, store only Aadhaar last 4 digits, and defer Mambu client creation until credit profile completion with background retry for failed syncs.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.borrower.model`
- `com.loanplatform.loan_platform.domain.borrower.repository`
- `com.loanplatform.loan_platform.domain.borrower.service`
- `com.loanplatform.loan_platform.domain.tenant.service`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.notification`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.loan_platform.security`
- `com.loanplatform.loan_platform.util`

## 3. New Files To Create
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/BorrowerStatus.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/KycDocumentType.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/CreditBureau.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/EligibilityCategory.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/service/KycService.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/service/CreditProfileService.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/service/MambuSyncService.java`
- `src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/PublicTenantController.java`
- `src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/BorrowerAuthController.java`
- `src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/KycController.java`
- `src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/AdminKycController.java`
- `src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/CreditProfileController.java`
- `src/main/java/com/loanplatform/loan_platform/adapter/outbound/notification/BorrowerNotificationAdapter.java`
- `src/main/java/com/loanplatform/loan_platform/port/outbound/BorrowerNotificationPort.java`
- `src/main/java/com/loanplatform/loan_platform/util/EncryptionUtil.java`
- `src/main/java/com/loanplatform/loan_platform/dto/request/KycDocumentInput.java`
- `src/main/java/com/loanplatform/loan_platform/dto/response/BorrowerRegistrationResponse.java`
- `src/main/java/com/loanplatform/loan_platform/dto/response/KycSubmissionResponse.java`
- `src/main/java/com/loanplatform/loan_platform/dto/response/PublicTenantBrowseResponse.java`
- `src/main/java/com/loanplatform/loan_platform/dto/response/PublicTenantProductResponse.java`
- `src/main/java/com/loanplatform/loan_platform/exception/DuplicatePanException.java`
- `src/main/java/com/loanplatform/loan_platform/exception/KycNotVerifiedException.java`
- `src/main/java/com/loanplatform/loan_platform/exception/TenantIsolationViolationException.java`
- `src/test/java/com/loanplatform/loan_platform/unit/borrower/KycServiceTest.java`
- `src/test/java/com/loanplatform/loan_platform/unit/borrower/CreditProfileServiceTest.java`

## 4. Files To Modify
- `src/main/resources/db/migration/V2__create_borrower_tables.sql`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/Borrower.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/KycDocument.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/CreditProfile.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/model/BorrowerAddress.java`
- `src/main/java/com/loanplatform/loan_platform/domain/borrower/repository/*`
- `src/main/java/com/loanplatform/loan_platform/domain/tenant/service/TenantService.java`
- `src/main/java/com/loanplatform/loan_platform/domain/tenant/repository/*`
- `src/main/java/com/loanplatform/loan_platform/dto/request/*Flow2*`
- `src/main/java/com/loanplatform/loan_platform/dto/response/*Flow2*`
- `src/main/java/com/loanplatform/loan_platform/mapper/BorrowerMapper.java`
- `src/main/java/com/loanplatform/loan_platform/mapper/TenantMapper.java`
- `src/main/java/com/loanplatform/loan_platform/security/SecurityConfig.java`
- `src/main/java/com/loanplatform/loan_platform/security/JwtTokenService.java`
- `src/main/java/com/loanplatform/loan_platform/security/TenantContextResolver.java`
- `src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java`
- `src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java`
- `src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java`
- `src/main/resources/application.properties`
- `src/test/java/com/loanplatform/loan_platform/unit/borrower/BorrowerServiceTest.java`
- `src/test/java/com/loanplatform/loan_platform/integration/borrower/BorrowerFlowIntegrationTest.java`
- `API.md`
- `AGENTS.md`

## 5. Database Migrations
- `borrowers`: encrypted PAN, Aadhaar last four only, borrower status lifecycle, Mambu sync columns.
- `kyc_documents`: document status, rejection reason, archive flag for re-submission.
- `credit_profiles`: DTI ratio and eligibility category.
- Added tenant/status and sync-focused indexes.

## 6. API Endpoints
- `GET /api/v1/tenants/public` (public tenant/product browse)
- `POST /api/v1/auth/register` (public borrower registration)
- `POST /api/v1/kyc/submit` (borrower submits KYC array)
- `PUT /api/v1/admin/kyc/{kycDocumentId}/verify` (tenant admin verification)
- `GET /api/v1/admin/kyc/pending` (tenant admin pending queue)
- `POST /api/v1/credit-profile` (borrower credit profile setup)

## 7. External Calls
- Mambu call only after credit profile completion: `POST /api/v2/clients`.
- Failed Mambu sync retried by scheduled job every 5 minutes.

## 8. Messaging
- As requested, Flow 2 uses synchronous normal service calls (`BorrowerNotificationPort`) instead of Kafka.

## 9. Redis Keys
- Not used in this Flow 2 implementation.

## 10. Stripe Integration
- Not applicable.

## 11. State Machine
- Not applicable to Flow 2.

## 12. Security and Tenant Isolation
- Public endpoints explicitly permit-all.
- Borrower endpoints require `ROLE_BORROWER` and use JWT `borrowerId` + `tenantId` claims.
- Admin KYC endpoints require `ROLE_TENANT_ADMIN` and enforce strict tenant isolation with explicit `403` on mismatch.

## 13. Risks
- PII handling risk reduced via PAN encryption and Aadhaar truncation.
- Mambu sync eventual consistency handled via retry scheduler.
- Integration test execution depends on sandbox socket permissions for WireMock.
