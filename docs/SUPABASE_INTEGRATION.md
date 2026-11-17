# Supabase integration

## Architecture decision (2026-11-15)

Use Supabase as the managed PostgreSQL provider first; do not replace the Spring Boot application or its authentication model.

- Spring Boot remains the only application API and owns business authorization.
- Spring Data JPA remains the persistence abstraction.
- Flyway remains the sole schema migration authority. Do not edit or replay existing migrations to adapt them to Supabase.
- Supabase Auth is not enabled in this phase. Existing BCrypt credentials and application JWTs remain authoritative.
- Supabase Storage is not enabled in this phase. The current local media implementation remains the development default until a storage adapter, private bucket policies, and ownership tests are implemented.
- The Expo app continues to call the Spring Boot API. No Supabase secret or service-role key belongs in the mobile bundle.

## Database connection

Set `SUPABASE_DB_JDBC_URL` to a PostgreSQL JDBC URL for the selected Supabase connection method. Prefer the Supabase session pooler or direct connection as appropriate for the deployment network. Require TLS with `sslmode=require`. The application accepts this variable before the existing `DATABASE_URL` fallback.

Keep credentials in deployment secrets, not in this repository. The username/password are supplied separately via `DATABASE_USERNAME` and `DATABASE_PASSWORD`. Do not paste a Supabase connection string containing credentials into issues, PR descriptions, logs, or documentation.

## Migration and data safety

1. Provision a separate Supabase project for development and production; never point local tests at production.
2. Take and verify a backup before any production migration.
3. Compare the live schema and Flyway history with the repository migrations before applying anything. The repository has historical migrations through V15; do not baseline or repair Flyway history without an explicit schema/history audit.
4. Run Flyway against a disposable development project first, then validate entity mappings and application flows.
5. Existing user IDs, recipes, sessions, membership/quota rows, payment events, journal records, pantry items, and meal plans must retain their UUIDs and relationships during any data transfer.
6. Define a tested rollback/restore procedure before cutover. A schema rollback is not a substitute for restoring data after destructive changes.

## Security boundaries

- The backend connects with a dedicated database role with only the privileges required by the application and migrations. Use a separate migration credential where operationally possible.
- Never expose the database password, JWT signing secret, or Supabase service-role key to Expo or the web frontend.
- Keep user ownership checks in Spring services/controllers. If direct client access is introduced later, enable RLS on every exposed table and test cross-user reads and writes explicitly.
- Supabase service-role access bypasses RLS; it must only be used server-side and must not be treated as a replacement for application-level ownership checks.
- Use private Storage buckets and short-lived signed URLs if/when Storage is adopted.

## Test and rollout matrix

- Configuration: Supabase JDBC URL overrides the local PostgreSQL fallback; missing Supabase settings preserve local development behavior.
- Connectivity: TLS-enabled connection succeeds with valid credentials; invalid credentials and unreachable hosts fail startup clearly.
- Migrations: clean development database migrates to the expected schema; a database with an existing Flyway history is inspected before migration.
- Authorization: user A cannot read, update, or delete user B's recipes, journal media, pantry items, meal plans, recovery attempts, or membership data.
- Regression: registration/login, recipe generation/recovery, quota enforcement, iyzico subscription/webhook handling, journal, pantry, and meal planner.
- Secrets: repository and built frontend/mobile artifacts contain no database password or service-role key.
- Storage (future phase): private bucket, path ownership, upload MIME/size validation, signed download, replacement/deletion, and orphan cleanup.

## Current implementation boundary

This first slice only adds a dedicated Supabase JDBC configuration entry and this decision/runbook. It does not provision Supabase projects, move production data, enable Auth/Storage, or claim a successful connection. Those operations require project credentials and a controlled environment supplied through deployment secrets.
