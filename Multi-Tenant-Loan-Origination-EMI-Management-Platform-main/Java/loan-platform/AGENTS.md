# AGENTS.md — Multi-Tenant Loan Origination & EMI Management Platform

> This file governs how AI agents (Claude, Cursor, Copilot, etc.) must behave.
> Every feature implementation MUST pass through all 3 gates sequentially. No exceptions.

---

## PROJECT IDENTITY

- **Name:** LoanOS — Multi-Tenant Loan Origination & EMI Management Platform
- **Stack:** Java 21 · Spring Boot 3.3 · Spring Security · Spring State Machine · Spring AI · PostgreSQL · Redis · Kafka · Stripe · Mambu Mock Service
- **Architecture:** Multi-tenant SaaS · Hexagonal (Ports & Adapters) · Event-Driven · Domain-Driven Design
- **Total Flows:** 11

---

## AGENT GROUND RULES

1. Never skip a gate. Gate 1 → Gate 2 → Gate 3 is mandatory for every feature.
2. Never hardcode secrets. All credentials go in `application-{env}.yml` or env vars.
3. Always be tenant-aware. Every query, service call, API response must be scoped to `tenantId`.
4. Mock Mambu = Real Mambu contract. Fake Mambu must mirror real Mambu REST response shapes exactly.
5. Idempotency is non-negotiable. All payment/disbursement endpoints must carry idempotency keys in Redis.
6. State machine is source of truth. Loan lifecycle state changes happen ONLY through Spring State Machine.
7. Write tests before marking a feature done.
8. Generate SESSION.md after every Gate 3. Mandatory audit trail.

---

## GATE 1 — PLAN PHASE

### Trigger
Any new feature request, flow implementation, or change request.

### Agent Must Do

1. Read PLAN.md and identify the target flow (Flow 1–11).
2. Read folder structure in PLAN.md for that flow's modules.
3. Generate: `/docs/plans/plan-flow-{N}-{feature-slug}.md`
4. Local plan MUST contain:
    - a. Feature summary (2–3 sentences)
    - b. Affected modules (package paths)
    - c. New files to create (full path + purpose)
    - d. Files to modify (full path + what changes)
    - e. Database migrations (table, columns, indexes)
    - f. API endpoints (method + path + purpose)
    - g. Mambu Mock API calls required
    - h. Kafka topics (produce/consume)
    - i. Redis keys (pattern + TTL)
    - j. Stripe integration points (if payment-related)
    - k. State machine transitions triggered
    - l. Security (roles, tenant isolation)
    - m. Risk flags
5. Present plan for human approval. Do NOT write application code in Gate 1.

### Gate 1 Exit Criteria
- [ ] Local plan file created
- [ ] All sections filled
- [ ] Human approved

---

## GATE 2 — IMPLEMENTATION PHASE

### Trigger
Human approval of Gate 1 plan.

### Implementation Order (always follow this)

1. Domain entities & value objects
2. Flyway DB migration SQL
3. Repository layer (Spring Data JPA)
4. Domain services & business logic
5. Port interfaces (inbound + outbound)
6. Adapters (REST controllers, Kafka listeners, Feign clients)
7. Mambu Mock Service endpoints (new Mambu APIs)
8. Stripe integration (payment flows)
9. Spring State Machine transitions (lifecycle flows)
10. Spring AI tools/RAG updates (chatbot flow)
11. DTOs, MapStruct mappers, validators
12. Exception handlers & error codes
13. Swagger/OpenAPI annotations

### Coding Standards

- `@Validated` + Bean Validation on all request DTOs
- `@Transactional` with explicit propagation on all service methods
- Lombok (`@Builder`, `@Data`, `@Slf4j`) everywhere
- MapStruct for entity↔DTO mapping (no manual mapping)
- `@TenantAware` custom annotation on all service methods
- Redis idempotency check before any payment/disbursement
- All Mambu calls via `MambuClient` (Feign) — never raw RestTemplate
- All Stripe calls via `StripeGatewayService`
- Log entry + exit at DEBUG, errors at ERROR with tenantId + loanId + requestId
- Every new Mambu Mock endpoint: add to mambu-mock-service, match real schema, add 200ms delay simulation

### Gate 2 Exit Criteria
- [ ] All plan files created
- [ ] `./mvnw clean compile` — zero errors
- [ ] No TODO/FIXME in new code
- [ ] Swagger UI shows new endpoints
- [ ] Mambu Mock returns correct shapes

---

## GATE 3 — TEST, INTEGRATE & SESSION PHASE

### Trigger
Gate 2 completion.

### Part A — Unit Tests

- JUnit 5 + Mockito + AssertJ
- Service layer: 90%+ line coverage
- Domain logic: 100% line coverage
- State machine: all happy + all error paths
- Mock all externals (Mambu, Stripe, Kafka, Redis)
- Naming: `methodName_scenario_expectedResult()`

### Part B — Integration Tests

- `@SpringBootTest` + Testcontainers (PostgreSQL + Redis)
- WireMock for Mambu Mock and Stripe stubs
- End-to-end happy path per flow
- 2+ error/edge-case paths
- Tenant isolation: prove tenant A cannot access tenant B data
- Idempotency: duplicate payment must not double-post

### Part C — API Smoke Test

- Run app locally
- Execute all new endpoint cURLs
- Verify response codes + payload shapes match API.md

### Part D — SESSION.md Generation

After all tests pass, generate `/docs/sessions/session-flow-{N}-{timestamp}.md`:
```
## Session Summary
- Flow: [Flow N — Name]
- Date: [ISO date]
- Agent: [model name]
- Status: COMPLETED / PARTIALLY_COMPLETED / BLOCKED

## What Was Planned
[copy from plan file]

## What Was Implemented
[every file created/modified with one-line description]

## Test Results
- Unit tests: X passed / Y failed
- Integration tests: X passed / Y failed
- Coverage: X%

## Deviations from Plan
[changes vs plan with reason]

## Known Issues / Tech Debt

## Next Steps
```

### Gate 3 Exit Criteria
- [ ] `./mvnw test` — zero failures
- [ ] Integration tests pass
- [ ] SESSION.md generated
- [ ] PR references flow number and session file

---

## PAYMENT FLOW SPECIAL RULES (Flow 6)

- Stripe PaymentIntent created BEFORE Mambu repayment posting
- Stripe webhook `payment_intent.succeeded` → post to Mambu Repayment API
- Stripe webhook `payment_intent.payment_failed` → mark EMI FAILED, trigger retry
- Idempotency key: `EMI:{loanId}:{emiNumber}:{tenantId}`
- Redis lock TTL: 300 seconds
- Never post to Mambu if Redis key already exists

---

## AI CHATBOT SPECIAL RULES (Flow 11)

- System prompt must include tenant context + borrower role
- RAG docs: loan product terms, repayment policies, NPA policies (per tenant)
- Allowed tools: getLoanStatus, getRepaymentSchedule, simulatePrepayment, getOverdueStatus, getNPAPortfolioSummary
- Never return raw Mambu API responses to borrowers — sanitize first
- Conversation history in Redis, TTL 30 minutes

---

## AGENT PRE-COMMIT CHECKLIST

- [ ] Does this break tenant isolation?
- [ ] Is anything hardcoded (tenantId, secret, URL)?
- [ ] Does every endpoint have `@PreAuthorize`?
- [ ] Is there a Flyway migration for every schema change?
- [ ] Did I update API.md for new/changed endpoints?
- [ ] Did I update flow status in AGENTS.md?
- [ ] Is SESSION.md generated?

---

## FLOW STATUS TRACKER

| Flow | Name | Status |
|------|------|--------|
| 1 | Tenant Onboarding | ✅ COMPLETE |
| 2 | Borrower Registration & KYC | ✅ COMPLETE |
| 3 | Loan Application & Offer Generation | ✅ COMPLETE |
| 4 | Document Submission & Approval | ⬜ NOT STARTED |
| 5 | Loan Disbursement & Schedule | ✅ COMPLETE |
| 6 | EMI Collection & Reconciliation | 🔵 IN PROGRESS |
| 7 | Prepayment & Foreclosure | 🔵 IN PROGRESS |
| 8 | NPA Flagging & Recovery | ✅ COMPLETE |
| 9 | Tenant Admin Dashboard & Analytics | ⬜ NOT STARTED |
| 10 | Loan Lifecycle State Machine | ⬜ NOT STARTED |
| 11 | AI Chatbot Advisory | ⬜ NOT STARTED |

Status: ⬜ NOT STARTED → 🔵 IN PROGRESS → ✅ COMPLETE → 🔴 BLOCKED
