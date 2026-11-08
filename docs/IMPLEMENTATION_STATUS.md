# Implementation status

Updated: 2026-11-08

## Phase 1 — Project foundation

Status: implemented; dependency-based verification remains environment-blocked.

- Spring Boot 3.3 / Java 21 Maven backend, PostgreSQL/Flyway, JWT authentication and ownership-aware recipe/session persistence.
- Vite React/TypeScript frontend and Expo/React Native mobile client.

## Phase 2 — Recipe generation

Status: functional local development path with Runware recipe generation/review integration. Recipe generation continues to use its own configured text model (RUNWARE_MODEL). A shared schema-directed output normalizer is now integrated into decomposition, component generation and review; it runs only after parse/contract failure and is followed by the original deterministic validators.

## Phase 3 — Visual recovery engine

Status: multimodal recovery implementation committed; provider execution still requires an integration run with a funded Runware key.

- Added the dedicated recipe_recovery_attempt table, recipe/session/parent foreign keys, attempt numbering and score constraints.
- Added Runware native textInference vision adapter using a separate RUNWARE_VISION_MODEL setting. Default is google:gemini@3.1-pro; Runware documents this model as image-to-text/multimodal.
- Added strict response-field and score/action validation, separate image-trust / diagnosis-confidence / target-proximity values, and the 60-point image gate.
- Added a shared Runware-backed AiOutputNormalizer. Recovery vision and text fallback use it only after their first response fails validation; normalized output is validated again. Missing decision-critical evidence remains null and produces ANALYSIS_INCOMPLETE instead of guessed scores or actions.
- Added the inclusive 0–49 / 50–74 / 75–89 / 90–100 decision policy and boundary unit tests.
- Added JPEG/PNG decoding, size/dimension checks, metadata-stripping re-encoding, generated user-scoped object keys, owner-only local file permissions and a 20-attempt/hour/session guard.
- Provider failure or output that remains malformed after one normalization call is returned as a controlled incomplete/unavailable result; no text-only fallback pretends to inspect a photo. No blind retry or normalization loop is used.

## Phase 4 — Closed-loop cooking

Status: iterative API and mobile flow implemented.

- Initial photo, optional problem category/description and desired outcome.
- Explicit correction confirmation.
- Required new photo plus required status text for every follow-up.
- Each follow-up creates a new row linked to the parent attempt and same component.
- Recovery/remake choice for the 50–74 band.
- Mobile states for image rejection, clarification, correction, follow-up, recovery/remake and ready-to-continue.

## Phase 5 — History & gamification

Status: partial. Existing session history, progress and XP flows remain. Dedicated recovery-attempt history UI and analytics are not included in this increment.

## Phase 6 — Quality & delivery

Status: not fully verified. The implementation was committed through GitHub; this environment could not clone the repository or resolve external dependencies, so Maven and Expo/TypeScript builds were not run here. Run the verification commands below locally before release:

- Backend: ./mvnw test
- Mobile: cd mobile && npm install && npx tsc --noEmit
- Expo: cd mobile && npx expo start -c

## Runtime configuration

With Runware enabled, set:

- RUNWARE_API_KEY
- SPRING_PROFILES_ACTIVE=runware
- Optional RUNWARE_MODEL for recipe generation.
- Optional RUNWARE_VISION_MODEL for recovery vision; defaults to google:gemini@3.1-pro.
- Optional RUNWARE_TEXT_FALLBACK_MODEL for text-only recovery; defaults to deepseek:v4.1@flash.
- Optional RUNWARE_NORMALIZER_MODEL for schema normalization; defaults to deepseek:v4.1@flash.

The vision model is deliberately separate from recipe generation. The native Runware text-inference endpoint receives the uploaded photo as a data URI. Do not change this model to a text-only model.

## Remaining production hardening

- Run end-to-end tests against the actual Runware account/model and confirm latency/cost under real photos.
- Add global persistent per-user rate limiting and image retention/deletion jobs (a per-session guard is already in place).
- Consider object storage (private S3-compatible bucket) instead of local disk for multi-instance deployment.
- Add recovery-attempt history UI and analytics.
- Add component-specific action allowlists and food-safety policy review before expanding supported foods/actions.
- Ensure recipe-stage advancement is explicitly connected to the recovery state machine when stage-level progression is introduced.
