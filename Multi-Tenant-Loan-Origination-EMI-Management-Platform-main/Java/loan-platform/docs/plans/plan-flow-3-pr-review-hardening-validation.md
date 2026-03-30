# Gate 1 Plan - Flow 3 + Flow 2: PR Review Hardening & Validation Fixes

## 1. Feature Summary
This change request hardens existing Flow 2 and Flow 3 behavior after PR review by closing validation and lifecycle gaps that currently allow invalid KYC payload combinations and inconsistent borrower state progression. It also strengthens API-level input validation and adds targeted tests for the new edge cases so regressions are caught automatically.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.domain.borrower.service`
- `com.loanplatform.loan_platform.domain.borrower.model`
- `com.loanplatform.loan_platform.domain.borrower.repository`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.exception`
- `com.loanplatform.loan_platform.integration.borrower`
- `com.loanplatform.loan_platform.unit.borrower`
- `com.loanplatform.loan_platform.unit.loan`

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/docs/plans/plan-flow-3-pr-review-hardening-validation.md` | Gate 1 plan for PR hardening tasks and auditability. |
| `/home/admin123/Desktop/Project/Java/loan-platform/docs/sessions/session-flow-3-20260304T-pr-hardening.md` | Gate 3 implementation and test audit trail for this PR fix set. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/service/KycService.java` | Add KYC submission state guards, strict document validation (type format, duplicates), and verification idempotency/state guard. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/borrower/service/CreditProfileService.java` | Enforce one-time credit profile setup by rejecting repeat profile creation attempts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/KycDocumentInput.java` | Add bean validation constraints for document number/reference length. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/CreditProfileRequest.java` | Add field length constraints to prevent DB-level truncation/data-integrity failures. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/LoanApplicationRequest.java` | Add `@FutureOrPresent` and length bounds to prevent invalid disbursement dates and oversized values. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/borrower/KycServiceTest.java` | Add unit tests for duplicate KYC docs and invalid status transitions. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/borrower/CreditProfileServiceTest.java` | Add unit test proving repeat credit profile setup is blocked. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/borrower/BorrowerFlowIntegrationTest.java` | Add integration test for Aadhaar/PAN duplicate-number payload rejection and repeat credit-profile rejection. |
| `/home/admin123/Desktop/Project/Java/loan-platform/API.md` | Update endpoint contract notes for new validation/conflict responses. |

## 5. Database Migrations (Table, Columns, Indexes)
- No schema change required for this hardening patch.
- Existing tables used: `borrowers`, `kyc_documents`, `credit_profiles`, `loan_applications`.

## 6. API Endpoints (Method + Path + Purpose)
- `POST /api/v1/kyc/submit` — now rejects duplicate KYC document numbers and invalid PAN/Aadhaar format combinations.
- `PUT /api/v1/admin/kyc/{kycDocumentId}/verify` — now rejects verification of archived/non-pending documents.
- `POST /api/v1/credit-profile` — now rejects duplicate setup attempts once profile already exists.
- `POST /api/v1/loans/applications` — stricter DTO validation on disbursement date and field sizes.

## 7. Mambu Mock API Calls Required
- No new Mambu Mock endpoints.
- Existing calls remain unchanged:
  - `POST /api/v2/clients`
  - `POST /api/v2/loans:simulate`

## 8. Kafka Topics (Produce/Consume)
- No new topics.
- Existing events remain: `loan.application.submitted`, `loan.offer.selected`, `loan.offer.expired`.

## 9. Redis Keys (Pattern + TTL)
- No Redis key changes in this patch.

## 10. Stripe Integration Points
- Not applicable.

## 11. State Machine Transitions Triggered
- No transition model changes.
- Existing Flow 3 transitions remain as-is (`SUBMIT`, `SELECT_OFFER`, `EXPIRE_OFFERS`).

## 12. Security (Roles, Tenant Isolation)
- No role changes.
- Existing tenant isolation remains enforced through tenant-scoped repository access and JWT tenant context.
- Additional lifecycle guards reduce risk of state tampering via repeated KYC/credit-profile actions.

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Stricter validation may break permissive existing clients | Clients sending invalid KYC payloads may now get 400/409 | Update API.md and tests with explicit error expectations |
| Credit profile immutability may block legitimate correction flows | Borrower cannot re-post profile through same endpoint | Intentional for current flow contract; future correction flow can be separate endpoint with audit controls |
| KYC lifecycle guard can reject retries in non-rejected states | Borrowers may need support handling | Keep only `KYC_REJECTED` as re-submit path; clear error message |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created
- [x] All required sections filled
- [x] Human approved via explicit request to review and fix PR issues in this session
