# Claude Code — KitchenAI Engineering Guide

This file defines the required development, testing, and delivery workflow for every KitchenAI task. Follow it for feature work, bug fixes, refactors, and documentation changes that affect implementation.

## 1. Repository structure

- Backend: Spring Boot 3, Java 21, Maven; source under `src/main/java`, tests under `src/test/java`.
- Web frontend: React, TypeScript, Vite; under `frontend/`.
- Native mobile: Expo / React Native, TypeScript; under `mobile/`.
- Database migrations: Flyway under `src/main/resources/db/migration`.
- Manual test cases: `test/manuel/cases/`.
- CI workflow: `.github/workflows/ci.yml`.

Read the relevant implementation, existing tests, API contracts, migrations, and project documentation before changing code. Follow existing conventions unless there is a concrete reason to improve them.

## 2. Mandatory task workflow

For every implementation task:

1. **Understand**
   - Read the task requirements and acceptance criteria.
   - Inspect the affected backend, frontend, mobile, database, and test code.
   - Identify backward-compatibility, security, data-integrity, and migration risks.
   - Ask for clarification only when a blocking requirement is genuinely ambiguous.

2. **Plan**
   - Break the task into implementation and verification steps.
   - Identify the test layers needed for each changed behavior.
   - Define expected outcomes, including invalid-input and failure cases.

3. **Implement**
   - Keep changes scoped to the task.
   - Preserve existing API contracts unless the task explicitly changes them.
   - Never commit credentials, tokens, private keys, or real environment files.
   - Add a new Flyway migration for schema changes; do not edit migrations that may already have run in another environment.

4. **Test**
   - Add or update automated tests in the same task as the implementation.
   - Run the relevant tests locally; do not rely only on compilation or CI.
   - Run regression checks for affected modules.
   - Add manual test cases for user-visible flows, device-dependent behavior, or scenarios that cannot be reliably automated.

5. **Review**
   - Inspect the final diff for accidental changes, secrets, missing tests, and incomplete error handling.
   - Verify authorization and ownership boundaries for user data.
   - Verify logs are useful and do not expose credentials or sensitive data.

6. **Deliver**
   - Report exactly which tests ran and their actual outcomes.
   - Report any test that was skipped, blocked, or not run.
   - Include migration/deployment notes and manual verification instructions when relevant.
   - Do not mark a task complete or claim a test passed merely because a test file was added.

## 3. Testing policy — required for every task

Every task that changes executable behavior must include automated tests for the changed behavior. Test depth depends on the layer and risk; do not add meaningless tests just to increase test count.

### Backend (Java / Spring Boot)

- Unit-test business logic, calculations, normalization, validation, and branching behavior.
- Use JUnit 5 and Mockito for isolated unit tests where appropriate.
- Use Spring Boot tests / MockMvc for controller, serialization, validation, authentication, and HTTP behavior.
- Use the H2 test dependency or the project's configured test database for persistence/integration tests where appropriate.
- Test success paths, boundary values, invalid inputs, expected failures, and regression cases.
- For database writes, verify transactionality, constraints, and audit/history side effects where applicable.
- For user-owned data, test unauthenticated access and cross-user isolation.
- For concurrency or idempotency requirements, add tests that exercise duplicate and concurrent requests when practical.

**Backend command:**

```bash
mvn --batch-mode test
```

### Web frontend (React / TypeScript / Vite)

- Add unit/component tests for changed UI logic, state transitions, validation, formatting, and user interactions.
- Use the existing Vitest setup and `frontend/package.json` test script.
- Mock API boundaries; do not make unit tests depend on live external services.
- Cover loading, success, empty, error, and invalid-input states when relevant.
- Keep accessibility and keyboard interaction in mind for interactive components.

**Commands:**

```bash
cd frontend
npm ci
npm test
npm run build
```

The current repository has a Vitest test script. Add actual test files for changed behavior; a successful build alone is not a substitute for frontend unit tests.

### Native mobile (Expo / React Native)

- Add tests for changed business logic, validation, state transitions, and user interactions.
- Keep business logic testable outside screen components where practical.
- Mock network, storage, permissions, and device APIs at the boundary.
- Verify loading, empty, success, error, and offline/failure states where relevant.
- The current mobile CI check is TypeScript type-checking; type-checking is not a unit test. If a task changes mobile behavior, add/extend a suitable automated test setup and wire its test command into CI rather than treating `tsc` as test coverage.

**Current commands:**

```bash
cd mobile
npm ci
npx tsc --noEmit
```

### API, database, and security

When relevant, include tests for:

- Authentication and authorization.
- User/tenant ownership isolation.
- Request validation and error responses.
- Database constraints and migration compatibility.
- Duplicate requests and idempotency.
- Transaction rollback and partial-failure prevention.
- Concurrent updates and race conditions.
- Pagination, filtering, sorting, and boundary conditions.

Never trust a client-supplied user ID as the owner of a resource; derive ownership from the authenticated principal.

### Manual acceptance tests

For meaningful user-facing features, add a Markdown test plan under:

```
test/manuel/cases/<feature-name>.md
```

Each case must include:

- Stable case ID.
- Purpose / scenario.
- Preconditions and test data.
- Numbered execution steps.
- Expected result.
- Result status when execution is performed: `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN`.
- Evidence or notes for failures.

Do not pre-mark unexecuted manual cases as passing. Include happy paths, validation, boundary conditions, error states, permissions, and data-integrity scenarios as applicable.

## 4. Current GitHub Actions pipeline

Workflow: `.github/workflows/ci.yml`

It runs for pull requests targeting `main` and pushes to `main`. Current jobs:

| Job | Current checks |
|---|---|
| Backend | Java 21 setup, Maven dependency cache, `mvn --batch-mode test` |
| Frontend | Node 22, `npm ci`, `npm run build` |
| Mobile | Node 22, `npm ci`, `npx tsc --noEmit` |

Important distinction: the current workflow runs backend tests, but the frontend job currently builds without executing the available Vitest test script; the mobile job type-checks but does not run unit tests.

**Pipeline maintenance rule:** When adding or enabling a test suite for a module, update `.github/workflows/ci.yml` in the same task so CI executes that suite. Do not silently leave new tests running only on a developer machine. Ensure the workflow fails when an actual test fails. Do not use pass-with-no-tests flags to disguise missing test coverage.

Expected direction for the pipeline as suites are established:

- Backend: `mvn --batch-mode test`.
- Frontend: `npm ci`, `npm test`, `npm run build`.
- Mobile: `npm ci`, the configured mobile unit-test command, and `npx tsc --noEmit`.

If a suite has not yet been established, state that limitation explicitly; do not claim that CI covers it.

## 5. Pull request and branch rules

- Create feature/fix branches from the latest `main`; do not implement task work directly on `main`.
- Keep each PR focused on one task or a tightly related set of changes.
- Include a concise summary, implementation notes, migration/configuration notes, and test evidence in the PR description.
- List exact commands and results, including checks not run.
- Link the manual test plan when one is added.
- Wait for required CI checks to finish and investigate failures.
- Do not merge a PR unless the user explicitly asks.
- Do not report a PR as mergeable or CI-green without checking its current state.

## 6. Definition of Done

A task is ready for review only when all applicable items are satisfied:

- [ ] Acceptance criteria are implemented.
- [ ] Automated tests were added or updated for changed behavior.
- [ ] Relevant automated tests were executed and passed.
- [ ] Regression checks for affected modules were executed.
- [ ] Frontend build and/or mobile type-check passed when those modules changed.
- [ ] Manual test plan was added for relevant user-facing behavior.
- [ ] Security, ownership, validation, and data-integrity cases were considered.
- [ ] Database migration is additive and compatible with existing environments.
- [ ] Diff contains no secrets or unrelated changes.
- [ ] PR description reports accurate validation results and outstanding risks.

A task may be delivered with a documented blocker, but it must not be described as fully verified until the blocker is resolved.

## 7. Communication standard

Use concise, evidence-based status updates. Separate these statements clearly:

- **Implemented:** code exists.
- **Automated-tested:** named automated tests were executed and passed.
- **CI-verified:** the relevant GitHub Actions run completed successfully for the current commit.
- **Manually-tested:** a person executed the documented manual case and recorded evidence.

Never use these terms interchangeably.
