# Gate 1 Plan — Flow 1: Tenant Onboarding, Loan Product Configuration, Activation

## 1. Feature Summary
Flow 1 will onboard a new tenant by registering tenant metadata (`name`, `domain`, `planTier`), provisioning a Mambu branch, provisioning a Mambu loan product, and activating the tenant in a single orchestrated flow. The tenant record will persist both `mambuBranchId` and `mambuLoanProductId` after successful Mambu calls.  
This plan follows the `platform-api` package style from `plan.md`, adapted into the existing `loan-platform` module and existing root package `com.loanplatform.loan_platform` without renaming main folders.

## 2. Affected Modules (Package Paths)
- `com.loanplatform.loan_platform.config`
- `com.loanplatform.loan_platform.domain.tenant.model`
- `com.loanplatform.loan_platform.domain.tenant.repository`
- `com.loanplatform.loan_platform.domain.tenant.service`
- `com.loanplatform.loan_platform.port.inbound`
- `com.loanplatform.loan_platform.port.outbound`
- `com.loanplatform.loan_platform.adapter.inbound.rest`
- `com.loanplatform.loan_platform.adapter.outbound.mambu`
- `com.loanplatform.loan_platform.adapter.outbound.kafka`
- `com.loanplatform.loan_platform.dto.request`
- `com.loanplatform.loan_platform.dto.response`
- `com.loanplatform.loan_platform.mapper`
- `com.loanplatform.loan_platform.exception`

## 3. New Files To Create (Full Path + Purpose)
| File Path | Purpose |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/config/SecurityConfig.java` | Define RBAC; restrict onboarding endpoints to `ROLE_PLATFORM_ADMIN`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/tenant/model/Tenant.java` | Tenant aggregate storing onboarding + Mambu references. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/tenant/model/TenantStatus.java` | Enum for onboarding lifecycle statuses. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/tenant/model/PlanTier.java` | Enum for platform plan tiers. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/tenant/repository/TenantRepository.java` | Persistence for tenant aggregate. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/domain/tenant/service/TenantOnboardingService.java` | Orchestrates register -> Mambu branch -> Mambu loan product -> activate. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/inbound/TenantUseCase.java` | Inbound contract for onboarding operations. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/port/outbound/MambuPort.java` | Outbound abstraction for Mambu provisioning calls. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/inbound/rest/TenantController.java` | Platform-admin REST endpoints for onboarding and tenant retrieval. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuClient.java` | Feign client for `/api/v2/branches` and `/api/v2/loanproducts`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/MambuAdapter.java` | Maps domain requests/responses to Mambu client DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuBranchCreateRequest.java` | Request payload model for Mambu branch create API. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuBranchResponse.java` | Response payload model for Mambu branch API. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanProductCreateRequest.java` | Request payload model for Mambu loan product create API. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/mambu/dto/MambuLoanProductResponse.java` | Response payload model for Mambu loan product API. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/adapter/outbound/kafka/KafkaEventAdapter.java` | Publish tenant onboarding lifecycle events. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/request/TenantOnboardingRequest.java` | API input: tenant identity + plan + default loan product settings. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/TenantOnboardingResponse.java` | API output including status, `branchId`, `loanProductId`. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/dto/response/TenantResponse.java` | Read model for tenant retrieval endpoint. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/mapper/TenantMapper.java` | MapStruct mapper for entity <-> response DTOs. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/GlobalExceptionHandler.java` | Standardized error responses for validation, conflict, integration failures. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/MambuIntegrationException.java` | Error wrapper for Mambu API failures/timeouts. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/exception/TenantConflictException.java` | Duplicate domain/name conflict handling. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/db/migration/V1__create_tenant_tables.sql` | Initial tenant schema for onboarding flow. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/unit/TenantOnboardingServiceTest.java` | Unit tests for orchestration and failure handling. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/integration/TenantOnboardingIntegrationTest.java` | Integration tests for DB + mock Mambu + security. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/test/java/com/loanplatform/loan_platform/smoke/TenantOnboardingApiSmokeTest.java` | Endpoint smoke tests for happy/error paths. |

## 4. Files To Modify (Full Path + What Changes)
| File Path | Planned Change |
|---|---|
| `/home/admin123/Desktop/Project/Java/loan-platform/pom.xml` | Add OpenFeign, SpringDoc, MapStruct, Kafka, and test libs needed by Flow 1 architecture. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/java/com/loanplatform/loan_platform/LoanPlatformApplication.java` | Enable Feign clients and component scanning for new packages. |
| `/home/admin123/Desktop/Project/Java/loan-platform/src/main/resources/application.properties` | Add datasource, Flyway, Redis, Kafka, and Mambu base URL/timeouts + security properties. |
| `/home/admin123/Desktop/Project/Java/loan-platform/AGENTS.md` | Update Flow 1 status from `NOT STARTED` to `IN PROGRESS` during implementation phase. |

## 5. Database Migrations (Table, Columns, Indexes)
### Migration File
`V1__create_tenant_tables.sql`

### Table: `tenants`
| Column | Type | Constraints |
|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` |
| `name` | `VARCHAR(150)` | `NOT NULL` |
| `domain` | `VARCHAR(180)` | `NOT NULL`, `UNIQUE` |
| `plan_tier` | `VARCHAR(30)` | `NOT NULL` |
| `status` | `VARCHAR(40)` | `NOT NULL`, default `REGISTERED` |
| `mambu_branch_id` | `VARCHAR(100)` | nullable, populated after branch creation |
| `mambu_branch_encoded_key` | `VARCHAR(64)` | nullable, internal Mambu reference |
| `mambu_loan_product_id` | `VARCHAR(100)` | nullable, populated after product creation |
| `mambu_loan_product_encoded_key` | `VARCHAR(64)` | nullable, internal Mambu reference |
| `activation_ts` | `TIMESTAMP` | nullable, set when status becomes `ACTIVE` |
| `created_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | `NOT NULL`, default `CURRENT_TIMESTAMP` |

### Indexes
- `uk_tenants_domain` on `domain` (unique)
- `idx_tenants_status` on `status`
- `idx_tenants_plan_tier` on `plan_tier`
- `idx_tenants_mambu_branch_id` on `mambu_branch_id`
- `idx_tenants_mambu_loan_product_id` on `mambu_loan_product_id`

## 6. API Endpoints (Method + Path + Purpose)
| Method | Path | Purpose | Role |
|---|---|---|---|
| `POST` | `/api/v1/platform/tenants/onboarding` | Register tenant, create Mambu branch, create Mambu loan product, activate tenant, return full onboarding result. | `PLATFORM_ADMIN` only |
| `GET` | `/api/v1/platform/tenants/{tenantId}` | Fetch tenant onboarding status and stored Mambu IDs. | `PLATFORM_ADMIN` only |
| `POST` | `/api/v1/platform/tenants/{tenantId}/onboarding/retry` | Retry failed provisioning from current tenant status. | `PLATFORM_ADMIN` only |

## 7. Mambu Mock API Calls Required
| Step | Method + Path | Request Mapping | Data Persisted |
|---|---|---|---|
| 1 | `POST /api/v2/branches` | Map tenant input to Mambu branch (`id`, `name`, `emailAddress`, `phoneNumber`, `address`, `notes`). | `mambu_branch_id` from response `id`, plus encoded key. |
| 2 | `POST /api/v2/loanproducts` | Use response branch `encodedKey` as `forBranchKey`; send product settings (`loanAmountSettings`, `interestSettings`, `scheduleSettings`). | `mambu_loan_product_id` from response `id`, plus encoded key. |

## 8. Kafka Topics (Produce/Consume)
| Topic | Direction | Event |
|---|---|---|
| `tenant.onboarding.v1` | Produce | `TENANT_REGISTERED`, `MAMBU_BRANCH_CREATED`, `LOAN_PRODUCT_CONFIGURED`, `TENANT_ACTIVATED`, `TENANT_ONBOARDING_FAILED`. |
| `tenant.onboarding.retry.v1` | Consume | Optional retry trigger for failed onboarding cases. |

## 9. Redis Usage (Pattern + TTL)
| Key Pattern | TTL | Purpose |
|---|---|---|
| `tenant:onboarding:idempotency:{idempotencyKey}` | `24h` | Prevent duplicate onboarding on repeated client submissions. |
| `tenant:onboarding:lock:{domain}` | `300s` | Short-lived distributed lock to prevent concurrent onboarding for same domain. |
| `tenant:onboarding:status:{tenantId}` | `10m` | Cache read response for status endpoint. |

## 10. Stripe Integration Points
No Stripe interaction in Flow 1. Marked `N/A` for this flow.

## 11. State Machine Transitions Triggered
- Loan state machine transitions: `None` (Flow 1 is tenant provisioning, not loan lifecycle).
- Tenant domain status transitions (service-managed):  
`REGISTERED` -> `MAMBU_BRANCH_CREATED` -> `LOAN_PRODUCT_CONFIGURED` -> `ACTIVE`  
Failure path at any provisioning step: `*_FAILED` with retry support.

## 12. Security (Roles, Tenant Isolation)
- All Flow 1 endpoints protected with `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`.
- Platform onboarding endpoints do not trust tenant header from request; tenant is created from platform-admin payload only.
- Enforce unique `domain` and reject duplicates with conflict response.
- Log `requestId`, actor (`sub` from JWT), and tenant ID for audit.
- Outbound Mambu base URL and credentials loaded from environment/application config (no hardcoded secrets).

## 13. Risk Flags
| Risk | Why It Matters | Mitigation |
|---|---|---|
| Partial provisioning in external system | Branch may be created while loan product fails, causing inconsistent state. | Persist intermediate statuses and enable retry endpoint from latest successful checkpoint. |
| Duplicate onboarding requests | Can create duplicate tenants or duplicate external Mambu artifacts. | Redis idempotency key + domain lock + DB unique constraint on `domain`. |
| Mambu API timeout/contract drift | Failed activation or malformed persistence mapping. | Feign timeouts, retries with backoff, strict DTO validation, integration tests against mock service. |
| Unauthorized onboarding | High-impact platform control surface. | Restrict to `PLATFORM_ADMIN` and reject all other roles. |
| Event publication failure | Audit/analytics consumers may miss lifecycle events. | Use transactional outbox pattern in implementation phase if direct publish is not reliable. |

## 14. Gate 1 Exit Checklist
- [x] Local plan file created at `docs/plans/plan-flow-1-tenant-onboarding.md`
- [x] All required sections from AGENTS Gate 1 filled
- [ ] Human approval pending
