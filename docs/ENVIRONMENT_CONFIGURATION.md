# Environment configuration

This repository uses Spring Boot's `application.yml` as the active configuration file. Spring Boot reads it automatically from `src/main/resources`; a separate `application.properties` file is not required.

## Local setup

1. Copy the repository-root `.env.example` to `.env`.
2. Fill in the required values for the services you use.
3. Start the backend from the repository root so `optional:file:.env[.properties]` resolves the expected file.
4. Keep `.env` out of Git. The repository's `.gitignore` excludes it.

Spring Boot loads the local `.env` through `spring.config.import`. In deployed environments, configure the same variables in the platform's environment/secret settings; do not deploy a committed `.env` file.

## Database selection

The datasource URL is resolved in this order:

1. `SUPABASE_DB_JDBC_URL` — preferred when connecting to Supabase PostgreSQL.
2. `DATABASE_URL` — local/legacy fallback.
3. `jdbc:postgresql://localhost:5432/kitchenai` — development default.

For Supabase, copy the **JDBC** connection string from Supabase Project Settings → Database → Connection string. Confirm that it contains `sslmode=require`. Keep `DATABASE_USERNAME` and `DATABASE_PASSWORD` separate from the URL.

Supabase is used as managed PostgreSQL in this integration slice. Spring Boot remains the API and authorization boundary; JPA and Flyway remain responsible for persistence and schema migrations. Expo does not connect directly to Supabase. Supabase Auth and Supabase Storage are not enabled by this configuration.

## Variable reference

| Variable | Purpose | Required when |
| --- | --- | --- |
| `PORT` | Backend HTTP port | Optional; defaults to 8080 |
| `SERVER_ADDRESS` | Bind address | Optional; defaults to 0.0.0.0 |
| `SPRING_PROFILES_ACTIVE` | Spring profile selection | Optional; defaults to runware |
| `SUPABASE_DB_JDBC_URL` | Supabase PostgreSQL JDBC URL | Supabase database is selected |
| `DATABASE_URL` | Local/legacy PostgreSQL JDBC URL | Supabase URL is not set |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Database credentials | For the selected database |
| `JWT_SECRET` | JWT signing secret (use at least 32 random characters) | All non-test environments |
| `JWT_EXPIRATION_MINUTES` | JWT lifetime in minutes | Optional; defaults to 60 |
| `FRONTEND_URL` | Allowed frontend origin/configuration | Set per environment |
| `RUNWARE_API_KEY` | Runware API credential | AI features are used |
| `RUNWARE_MODEL` | Text model | Optional; defaults to the documented model |
| `RUNWARE_VISION_MODEL` | Vision model | Optional; defaults to the documented model |
| `RUNWARE_TEXT_FALLBACK_MODEL` | Text fallback model | Optional; defaults to the documented model |
| `RUNWARE_NORMALIZER_MODEL` | Output normalizer model | Optional; defaults to the documented model |
| `MAX_IMAGE_SIZE` | Per-file upload limit | Optional; defaults to 10MB |
| `MAX_IMAGE_REQUEST_SIZE` | Multipart request limit | Optional; defaults to 12MB |
| `STORAGE_LOCAL_PATH` | Local upload directory | Optional; defaults to ./data/uploads |

`STORAGE_PROVIDER` and the `S3_*` values remain placeholders for a future storage-adapter change; they do not switch this integration to Supabase Storage or S3.

## Security and rollout notes

- Never commit `.env`, database passwords, JWT secrets, Runware keys, or S3 credentials.
- Use deployment secret management for staging/production. Do not rely on the development fallback JWT secret in production.
- Do not expose backend secrets through `EXPO_PUBLIC_*` variables or mobile application bundles.
- Do not run Flyway baseline/repair or production migrations as part of environment setup. Audit the target schema and Flyway history, and verify backups/restore procedures first.
- The mobile app has its own public API base URL configuration; it is not part of this backend `.env` template.
