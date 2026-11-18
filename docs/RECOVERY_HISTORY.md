# Recipe recovery history and diagnostics

## What is persisted

- Every accepted image/text recovery request creates a `recipe_recovery_attempt` row, including provider-unavailable and incomplete-analysis outcomes.
- Uploaded JPEG/PNG images are sanitized and stored under `STORAGE_LOCAL_PATH/<user-id>/<uuid>.<ext>`; the database stores only the object key.
- `recovery_ai_execution` stores the logical Runware execution outcome, model, request metadata, parsed response, error details and duration. It never stores API keys or authorization headers.
- The existing strict schema validation remains authoritative. Normalization is formatting-only; it cannot fill missing facts or scores.

## API

- `GET /api/v1/recovery-history` — list the authenticated user's recovery attempts.
- `GET /api/v1/recovery-history/{attemptId}` — retrieve a previous attempt and its AI execution records.
- `GET /api/v1/recovery-history/{attemptId}/image` — retrieve the stored image after ownership validation.
- `POST /api/v1/recovery-history/{attemptId}/reuse` — retrieve a previous result as a reference. It does not silently apply an old correction to a new cooking session.

## Storage

Local development defaults to `./data/uploads`. For a persistent deployment, mount a persistent volume at the configured `STORAGE_LOCAL_PATH`. The current implementation is filesystem-backed; it is not yet an S3 client. For multi-instance production deployment, migrate this storage adapter to private S3-compatible object storage before running multiple application instances.

## Vision diagnostics

The vision output budget is 4096 tokens with low thinking effort to leave sufficient room for the compact contract response. Logs include the provider finish reason and token usage. A `length` finish reason is treated as truncated output and bypasses normalization; the controller then invokes its existing text-only fallback rather than trying to reconstruct incomplete JSON. A successful JSON normalization is not considered proof of a complete recovery decision: the existing completeness and safety policies still reject missing evidence.

## Manual test checklist

1. Start the backend and allow Flyway migration V6 to run.
2. Upload a JPEG or PNG through the recovery UI. Verify an attempt row, image file and execution row are created.
3. Trigger text-only help. Verify a separate attempt and execution row are created.
4. Open **Yardım geçmişim**, select each attempt, and verify its image/advice and status.
5. Call the image endpoint as a different user; it must return 404.
6. Send an intentionally unclear image. Verify the attempt is retained as rejected/incomplete and no invented correction is shown.
7. Inspect logs for `finishReason` and compare it with the execution status.

Run the backend test suite and frontend build locally before merging; the GitHub connector cannot execute the project's Maven/Vite builds.

## Unified recovery request

The user-facing recovery flow collects exactly three required inputs: a JPEG/PNG photo, product/recipe name, and problem description. The vision model receives the photo and both text fields in one request. If vision is unavailable, returns malformed/incomplete output, rejects the photo as unclear, or cannot safely derive a decision, the backend automatically invokes text-only advice using the product name and problem description. This is a fallback within the same attempt; the user is not asked to submit a second request. Both model executions are recorded against the same attempt. The standalone text-fallback endpoint is no longer exposed.
