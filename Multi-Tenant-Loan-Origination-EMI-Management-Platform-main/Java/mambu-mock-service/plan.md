# PLAN.md — Mambu Mock Service Implementation

## Objective
Implement the Mambu Mock Service from the markdown source file so it is reproducible, testable, and ready to run on port `8081` with Swagger at `/swagger-ui.html`.

## Source of Truth
- Primary input: `Mock_service.md`
- Framework target: Spring Boot `3.3.x`
- Java target: `21`
- Database: H2 + Flyway
- API docs: SpringDoc OpenAPI + Swagger UI

## Scope
- Create all files and folders described in `Mock_service.md`.
- Copy code blocks verbatim into their mapped file paths.
- Ensure Flyway migrations exist under `src/main/resources/db/migration/`.
- Use `application.yml` instead of `application.properties`.
- Run compile and test verification.
- Confirm Swagger endpoints are accessible in application context.

## Implementation Plan
1. Parse `Mock_service.md` and enumerate all `## \`filename\`` sections.
2. Create each file path and copy section content exactly.
3. Map shorthand section names to actual package paths under `src/main/java/...` and `src/test/...`.
4. Ensure 5 Flyway scripts are present:
- `V1__create_branches.sql`
- `V2__create_loan_products.sql`
- `V3__create_clients.sql`
- `V4__create_loan_accounts.sql`
- `V5__create_installments_transactions.sql`
5. Ensure `src/main/resources/application.yml` is present and active.
6. Add Maven wrapper if missing (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`) so the requested commands can run.
7. Run verification commands:
- `./mvnw clean install -DskipTests`
- `./mvnw test`
8. If tests fail, fix only the failing file(s), then rerun tests.
9. Validate Swagger paths (`/swagger-ui.html`, `/swagger-ui/index.html`, `/api-docs`) via integration test.

## Acceptance Criteria
- All expected project files exist at exact paths.
- Application config is YAML-based.
- Test suite passes:
- Lifecycle tests: `18`
- EMI tests: `8` or as defined by current test source
- Swagger availability test(s) pass.
- Service boots with Tomcat configured for port `8081`.

## Risks and Constraints
- Offline/sandbox environments may block Maven dependency download.
- Sandbox may block network socket bind, preventing direct browser verification on `localhost:8081`.
- In that case, validate with MockMvc endpoint tests and startup logs.

## Operational Verification
Run locally in a normal environment:
- `./mvnw clean install -DskipTests`
- `./mvnw test`
- `./mvnw spring-boot:run`
Then open:
- `http://localhost:8081/swagger-ui.html`
- `http://localhost:8081/api-docs`
