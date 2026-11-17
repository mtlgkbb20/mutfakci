# Supabase integration test cases

These cases define the validation gate for the Supabase database rollout. Do not run them against production.

| ID | Scenario | Expected result | Status |
| --- | --- | --- | --- |
| SUP-001 | Start with no Supabase variables and local DATABASE_URL | Local PostgreSQL fallback is used | Pending runtime |
| SUP-002 | Set SUPABASE_DB_JDBC_URL and DATABASE_URL | Supabase JDBC URL takes precedence | Pending runtime |
| SUP-003 | Connect using TLS-enabled Supabase JDBC URL and valid credentials | Spring context starts; Flyway validates/applies pending migrations | Requires dev project |
| SUP-004 | Use invalid database credentials | Startup fails without logging credentials | Pending runtime |
| SUP-005 | Use unreachable database host | Startup fails with actionable connectivity error; no secret leakage | Pending runtime |
| SUP-006 | Start against an empty disposable database | Flyway creates the complete schema and JPA validation succeeds | Requires dev project |
| SUP-007 | Start against a database with existing Flyway history | Existing history is validated; no destructive baseline/repair occurs | Requires schema audit |
| SUP-008 | Authenticated user A requests user B's recipe/journal/pantry/meal-plan/recovery data | Request is denied or resource is returned as not found | Existing regression suite + Supabase DB |
| SUP-009 | Register/login and refresh JWT after database cutover | Existing auth contract remains unchanged | Pending runtime |
| SUP-010 | Generate/recover recipe and consume quota | Existing quota semantics remain unchanged | Pending runtime |
| SUP-011 | Process iyzico subscription and webhook event | Payment event idempotency and membership state remain unchanged | Pending runtime |
| SUP-012 | Search repository and frontend/mobile build outputs for service-role/database secrets | No secrets are present | Pending scan |

## Execution notes

- Existing automated backend tests use H2 and disable Flyway, so they do not prove Supabase/PostgreSQL compatibility.
- Add a disposable PostgreSQL integration-test job before production cutover; it should run the actual Flyway chain and JPA validation.
- Run the full backend, frontend, and mobile CI jobs for every implementation PR.
- Record the exact Supabase Postgres version, connection mode (direct/session/transaction pooler), and TLS mode in the deployment runbook, never the credentials.
