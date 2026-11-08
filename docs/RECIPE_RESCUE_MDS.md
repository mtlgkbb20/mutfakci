# Recipe Rescue — MDS

**Status:** Implemented baseline / source specification (runtime verification pending)  
**Target branch:** `ai-flow-help-support`  
**Scope:** Mobile recipe recovery flow, multimodal analysis, strict response contracts, iterative recovery, persistence and progression decisions.

---

## 1. Product goal

Recipe Rescue helps a user recover a recipe component that is not matching its intended state while cooking.

The user submits:
- A current photo of the food/component (**required**).
- A short problem description or predefined problem category (**optional**).
- The desired outcome (**optional**).
- The active recipe and current cooking context, when available.

The system analyses the image together with the recipe, component, user description and target state. It either:
1. Requests a clearer/new photo when image trust is below the minimum threshold.
2. Gives one practical, bounded correction.
3. Requests a follow-up photo and a short status description after the correction.
4. Evaluates progress against the target state and decides whether the user can continue, should apply another correction, may attempt a recovery, or should remake the component.

**Core principle:** do not regenerate or rewrite the whole recipe. Recover the current component with the smallest safe intervention. Recipe progression must remain grounded in the actual recipe and its target state.

## 2. Existing branch assessment

The branch already contains a partial recovery foundation:

- `RecoveryController` accepts an image and optional `desiredOutcome`, stores the image and persists an observation/feedback.
- `CookingSession` has a nullable `recipeId` foreign key to `recipe(id)`.
- `CookingObservation` stores an image object key, observed state JSON, target comparison JSON and confidence.
- `CookingFeedback` stores a proposed action and user-facing message.
- `CookingAction` exists for recording user-confirmed actions.
- The current recovery controller still writes hard-coded `CAKE_BATTER`, `UNCERTAIN` and `0.0` values and always asks for a clearer image. It does not perform real image analysis or iterative progress evaluation.
- The Runware adapter currently makes text-only chat-completion requests. Its configured default model in `application.yml` is `deepseek:v4.1@flash`; the adapter's Java fallback is `openai:gpt@5.4-mini`. The code does not currently send image content.
- The Runware adapter already uses JSON response schemas in prompts and validates recipe-generation outputs in application code. Recipe Rescue should follow the same strict-boundary philosophy, but have its own contracts and validators.

### Vision model decision

Reuse the existing Runware provider integration and credentials where possible, but **do not assume the currently configured recipe-generation model supports image input**. Before enabling recovery:
1. Verify which currently available Runware model(s) support vision/image input through the exact OpenAI-compatible endpoint used by this project.
2. Select a verified vision-capable model via a separate configuration property, e.g. `app.ai.runware.vision-model` / `RUNWARE_VISION_MODEL`.
3. Keep recipe generation and vision model configuration separate. Do not silently change the recipe-generation model.
4. Implement and test the actual multimodal message payload (image URL or supported data URL/base64 format) for the selected model.
5. If no verified vision model is configured, return a controlled `VISION_PROVIDER_UNAVAILABLE` response. Do not fake image analysis or fall back to text-only analysis while claiming the photo was inspected.

No provider key or credential may be returned to the mobile client or written to logs.

## 3. User experience

### 3.1 Initial recovery request

Required:
- Current photo.

Optional:
- Problem category, e.g. `TOO_HARD`, `TOO_SOFT`, `TOO_STICKY`, `NOT_RISING`, `NOT_HOLDING_SHAPE`, `SPLITTING`, `CURDLED`, `OVERCOOKED`, `UNDERCOOKED`, `OTHER`.
- Free-text description of the problem.
- Desired outcome / target texture.
- Current component/stage selection if it cannot be inferred safely from the cooking session.

The UI must not force the user to fill in every optional field. If the session is linked to a recipe, send its recipe/component/stage context automatically.

### 3.2 After a correction

After showing a correction, the app must explicitly ask the user to:
- Apply the suggested action.
- Upload a **new photo after applying it** (required to evaluate the result).
- Enter a short status description (required), e.g. “Biraz toparlandı ama hâlâ elime yapışıyor.”

Do not treat the original image as the follow-up image. Do not mark an action as completed just because it was suggested. The user must confirm the action was applied.

The follow-up request is tied to the previous recovery attempt and the same recipe component. It creates a new recovery-attempt record; it must not overwrite the previous attempt.

### 3.3 Recovery loop

The loop repeats until one of the terminal/continue outcomes is reached:

`INITIAL_OBSERVATION → IMAGE_GATE → DIAGNOSIS → CORRECTION_PROPOSAL → USER_APPLIES → FOLLOW_UP_IMAGE_AND_TEXT → PROGRESS_EVALUATION → CONTINUE | NEXT_CORRECTION | REMAKE_COMPONENT`

A new correction proposal always requires another follow-up image and status text before progress can be recalculated.

## 4. Scores and decision policy

Two different scores must be kept separate. They answer different questions and must never be conflated.

### 4.1 Image trust score

`imageTrustScore` is 0–100 and answers: “Is this image sufficiently clear, relevant and interpretable for this analysis?”

- **Below 60:** reject the analysis and request a new photo.
- **60–100:** image may proceed to food-state analysis, subject to other validation.
- If the model cannot confidently identify the relevant food/component, it must request clarification/new photo even if generic image quality appears acceptable.
- A low-trust result must not produce a food diagnosis, recovery instruction or target-progress percentage.
- The user-facing response should explain what is wrong with the image (blur, poor lighting, subject too far away, component not visible) where the model can reliably identify it.

Threshold is strict: `imageTrustScore < 60` means re-upload. A score of exactly 60 passes the image gate.

### 4.2 Target proximity score

`targetProximityScore` is 0–100 and answers: “How close is the current component to the target state defined by this recipe/component?”

It is evaluated only after the image passes the trust gate. It should use:
- Recipe ingredient quantities and relevant component instructions.
- Component target state / completion cues.
- Initial and latest observations.
- User's initial problem description and desired outcome.
- The latest follow-up status text.
- Corrections previously proposed and confirmed as applied.

Decision bands (boundaries are inclusive as written):

| Score | Status | Product behaviour |
|---|---|---|
| 90–100 | `READY_TO_CONTINUE` | Tell the user the component is close enough to target and they can continue. No further correction is needed. |
| 75–89 | `MINOR_CORRECTION` | Explain the small remaining gap and give one targeted correction. Ask for another follow-up photo + status text afterwards. |
| 50–74 | `RECOVERABLE_BUT_MAJOR_GAP` | Explain that remaking may produce a better result, but recovery can still be attempted. Offer two explicit choices: attempt recovery or remake this component. If recovery is chosen, give one conservative correction and require another follow-up. |
| 0–49 | `REMAKE_COMPONENT` | State that this component is not reasonably recoverable through the supported corrections and recommend remaking this component. Do not claim the entire recipe is lost. |

A score of 90 is ready; 89 is a minor correction. A score of 75 is minor correction; 74 is recoverable-but-major-gap. A score of 50 is recoverable-but-major-gap; 49 is remake.

### 4.3 Meaning and limitations of percentages

The percentage is an AI-estimated target-state proximity, not a laboratory measurement, objective probability of success, or guarantee. It must be based on explicit target criteria for the component, not a generic “looks good” impression.

The analysis should internally compare observable criteria (e.g. shape retention, surface smoothness, volume, browning, consistency cues) and produce a bounded score. If the target state cannot be inferred from the recipe or image, request clarification instead of inventing a score.

Store the model's score, confidence, target criteria and short rationale for auditability. Do not present excessive precision; display an integer percentage.

### 4.4 Independent confidence guard

Image trust and target proximity are not the same as model confidence in the diagnosis. The response contract should also include `analysisConfidence` (0–1 or 0–100, choose one representation consistently). If diagnosis confidence is too low to support a safe correction, return `NEEDS_CLARIFICATION` rather than a speculative instruction. Define and test the operational threshold in implementation; do not substitute target proximity for diagnostic confidence.

## 5. Analysis pipeline

Use a dedicated orchestration service, not controller-level provider calls.

1. **Load context:** authenticate owner; load cooking session; load linked recipe; identify active component/stage and target completion cue.
2. **Validate upload:** MIME allowlist, non-empty file, maximum size, decode image, enforce image dimension/pixel limits, strip EXIF/location metadata where practical, and store under a generated object key.
3. **Vision trust gate:** send image plus concise context to the configured vision model. Obtain image trust score and relevant visual observations.
4. **Gate:** if trust < 60, persist the rejected attempt and return `REQUEST_NEW_IMAGE`. No target score or correction is allowed.
5. **State analysis:** compare observations with the recipe component's target state and the user's description.
6. **Decision:** calculate target proximity and apply the fixed decision bands.
7. **Correction generation:** only for the applicable bands, produce a single correction action. Validate the response and enforce allowed action types/limits.
8. **Persist:** save the attempt, observations, scores, action and response in the recovery-attempt table.
9. **Follow-up:** after user confirmation and new photo + status text, repeat from step 2 with previous attempt history.
10. **Progress:** allow the cooking session to advance only when the result is `READY_TO_CONTINUE` or the user explicitly chooses a supported alternative flow. A `REMAKE_COMPONENT` result must not silently advance the recipe.

## 6. Strict response contracts

Provider output is untrusted. Use typed DTOs plus strict JSON Schema (or equivalent explicit Jackson validation) and application-level semantic validation. Reject:
- Missing required fields.
- Unknown enum values.
- Extra properties.
- Out-of-range scores.
- Negative/implausible quantities or durations.
- Multiple contradictory actions.
- Action types not allowed for the component.
- A correction that references ingredients not present in the recipe unless the user explicitly confirms they have them and the action is permitted.
- A target score on an image-rejected response.
- A correction on `REQUEST_NEW_IMAGE`, `NEEDS_CLARIFICATION` or `REMAKE_COMPONENT`.
- A `READY_TO_CONTINUE` result with a correction action.

### 6.1 Suggested public response shape

Use one stable discriminated response contract. Fields not applicable to a status should be null or omitted according to the schema; do not return ambiguous partial objects.

```json
{
  "status": "CORRECTION_PROPOSED",
  "imageTrustScore": 87,
  "analysisConfidence": 0.91,
  "targetProximityScore": 68,
  "problem": {
    "category": "TOO_STICKY",
    "summary": "Hamur hedeflenenden daha yapışkan görünüyor."
  },
  "assessment": {
    "targetState": "Elastik, toparlanan ve şekil verilebilen hamur",
    "gapSummary": "Hamur henüz yeterince toparlanmamış.",
    "reason": "Tarifin hedef kıvamına göre hâlâ fazla yapışkan."
  },
  "decision": "RECOVERABLE_BUT_MAJOR_GAP",
  "action": {
    "type": "REST",
    "instruction": "Hamuru üzerini kapatarak 5 dakika dinlendir.",
    "quantity": null,
    "unit": null,
    "durationSeconds": 300
  },
  "followUp": {
    "required": true,
    "requestPhoto": true,
    "requestStatusText": true,
    "prompt": "Uyguladıktan sonra yeni bir fotoğraf gönder ve kıvamın nasıl değiştiğini kısaca anlat."
  }
}
```

### 6.2 Status enum

At minimum:
- `REQUEST_NEW_IMAGE`
- `NEEDS_CLARIFICATION`
- `CORRECTION_PROPOSED`
- `OFFER_RECOVERY_OR_REMAKE`
- `READY_TO_CONTINUE`
- `REMAKE_COMPONENT`
- `VISION_PROVIDER_UNAVAILABLE`

### 6.3 Action enum

Start with a conservative allowlist, for example:
- `REST`
- `MIX`
- `KNEAD`
- `ADD_INGREDIENT`
- `ADJUST_TEMPERATURE`
- `DRAIN`
- `CHILL`
- `REFORM`
- `CONTINUE_COOKING`
- `STOP_COOKING`
- `REMAKE_COMPONENT`
- `NONE`

The allowlist and action parameter rules should be component-aware. For example, `ADD_INGREDIENT` must include a positive bounded quantity and a unit; it must not invent a pantry ingredient. Prefer small incremental adjustments and require re-evaluation after each one.

## 7. Persistence model

A dedicated table is required for recovery attempts. Do not overload `cooking_observation` or `cooking_feedback` to represent the entire iterative recovery lifecycle.

### 7.1 Proposed table: `recipe_recovery_attempt`

Each submitted image and corresponding analysis is a distinct attempt.

Required columns (names may follow the project's snake_case conventions):

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID PK | Attempt identity |
| `recipe_id` | UUID FK → `recipe(id)` | Explicit recipe relationship requested for reporting and retrieval |
| `session_id` | UUID FK → `cooking_session(id)` | Cooking session context |
| `parent_attempt_id` | UUID nullable FK → `recipe_recovery_attempt(id)` | Links follow-up attempt to the prior correction |
| `component_key` | VARCHAR | Component being recovered |
| `stage_order` | INT nullable | Current recipe stage, when applicable |
| `attempt_number` | INT | Sequential attempt within the session/component |
| `image_object_key` | VARCHAR | Private stored image key |
| `user_problem_category` | VARCHAR nullable | Optional selected problem |
| `user_status_text` | TEXT nullable | User's description for this attempt; required on follow-up |
| `desired_outcome` | TEXT nullable | Optional target description |
| `image_trust_score` | SMALLINT nullable | 0–100; null if analysis unavailable |
| `analysis_confidence` | NUMERIC nullable | Diagnosis confidence |
| `target_proximity_score` | SMALLINT nullable | 0–100; null when image gate fails |
| `observed_state_json` | JSONB/TEXT | Validated visual observations |
| `target_state_json` | JSONB/TEXT | Target criteria and recipe basis |
| `assessment_json` | JSONB/TEXT | Gap, rationale and decision |
| `action_json` | JSONB/TEXT nullable | Proposed action |
| `status` | VARCHAR | Attempt lifecycle status |
| `user_action_confirmed_at` | TIMESTAMPTZ nullable | When user confirmed applying correction |
| `created_at` | TIMESTAMPTZ | Creation timestamp |

Indexes:
- `(recipe_id, created_at DESC)`
- `(session_id, component_key, attempt_number)`
- `(parent_attempt_id)`

Constraints:
- `image_trust_score` and `target_proximity_score` must be null or between 0 and 100.
- `attempt_number` must be positive.
- Recipe and session ownership must be consistent. Enforce this in the service and, if practical, with a composite relational constraint or a transactional consistency check.
- For a session with a non-null `recipe_id`, the attempt's `recipe_id` must match `cooking_session.recipe_id`.
- If a session is not linked to a saved recipe, decide explicitly whether recovery is allowed. Recommended: support ad-hoc sessions with nullable `recipe_id` only if product requirements demand it; the requested recipe FK remains nullable in that case. For recipe-linked recovery, it must be non-null.

Use a Flyway migration (next available version) and update/add JPA entity, repository and indexes. Keep the existing observation/feedback tables if they remain useful, but define a single source of truth for each attempt. Avoid duplicating mutable status across unrelated tables.

### 7.2 Attempt lifecycle

Suggested lifecycle values:
- `IMAGE_REJECTED`
- `ANALYSIS_REJECTED`
- `CORRECTION_PROPOSED`
- `AWAITING_USER_ACTION`
- `AWAITING_FOLLOW_UP`
- `READY_TO_CONTINUE`
- `RECOVERY_OFFERED`
- `REMAKE_RECOMMENDED`
- `COMPLETED`

Persist every attempt, including rejected images, for diagnostics and product-quality metrics. Never store raw provider chain-of-thought. Persist concise structured observations and rationale only.

## 8. API behaviour

Keep the existing cooking-session route family where practical.

### Initial attempt
`POST /api/v1/cooking-sessions/{sessionId}/recovery-attempts`

Multipart:
- `image` — required.
- `problemCategory` — optional.
- `problemDescription` — optional.
- `desiredOutcome` — optional.
- `componentKey` — optional only if reliably inferable from session state.

### Confirm correction
`POST /api/v1/cooking-sessions/{sessionId}/recovery-attempts/{attemptId}/confirm-action`

Body:
- `confirmed` — boolean.
- Optional user note.

### Follow-up attempt
`POST /api/v1/cooking-sessions/{sessionId}/recovery-attempts/{attemptId}/follow-up`

Multipart:
- `image` — required.
- `statusText` — required and non-blank.
- The server derives recipe, component and parent attempt from the previous attempt; do not trust client-supplied recipe/session linkage.

### Choose recovery or remake
`POST /api/v1/cooking-sessions/{sessionId}/recovery-attempts/{attemptId}/decision`

Body:
- `choice`: `TRY_RECOVERY` or `REMAKE_COMPONENT`.

Every route must enforce authenticated ownership of the session, recipe and attempt. Use transactional state transitions and reject out-of-order calls.

## 9. Safety, privacy and reliability

- A photo is evidence of visible state, not proof of food safety. Do not declare food safe based on appearance.
- For raw meat, seafood, eggs and other safety-sensitive foods, temperature/time guidance must follow reliable food-safety rules; visual recovery scores cannot override safety constraints.
- Never recommend tasting a potentially unsafe/undercooked item as a diagnostic step.
- Do not suggest adding ingredients that are absent from the recipe without asking the user first.
- Do not make large irreversible adjustments as the first intervention.
- Do not expose local filesystem paths or image object keys to the client.
- Apply upload size, MIME, decode, pixel-count and rate limits.
- Store images privately; define retention/deletion policy.
- Do not log image bytes, base64 payloads, secrets or sensitive user text.
- Provider failure, malformed JSON, timeout and unsupported image format must return controlled API errors/statuses and must not create fake successful analysis.

## 10. Mobile UI requirements

The mobile flow should be a state-driven UI, not a single upload form:
1. Initial photo + optional problem + optional desired outcome.
2. Loading/analysis state.
3. New-photo request state (image trust < 60).
4. Clarification state.
5. Correction proposal with action and reason.
6. Explicit “I applied this” confirmation.
7. Follow-up photo + required status text.
8. Progress result with percentage and next decision.
9. Recovery-vs-remake choice for 50–74.
10. Continue recipe or remake component for terminal outcomes.

Show the percentage as “hedef kıvama yakınlık” (target-state proximity), not “AI confidence”. Show image trust separately only if it helps the user; do not confuse users with internal confidence metrics.

## 11. Observability and analytics

Record:
- Image rejection rate (<60).
- Vision provider/model and latency (never credentials).
- Distribution of target proximity scores.
- Correction type and component type.
- Number of attempts until ready/remake.
- User-selected recovery vs remake.
- User-reported success after correction.
- Validator rejection rate and provider error rate.

These metrics should help calibrate the score bands over time. Do not tune the score thresholds automatically from unreviewed user feedback.

## 12. Acceptance criteria

### Image gate
- [ ] Image trust 59 returns `REQUEST_NEW_IMAGE`; no diagnosis, action or target score is returned.
- [ ] Image trust 60 proceeds to state analysis.
- [ ] Blurry, dark, irrelevant or unrecognisable images request a new image.
- [ ] Vision provider missing/unsupported returns a controlled unavailable status; no text-only fake analysis.

### Progress bands
- [ ] 90 and 100 return `READY_TO_CONTINUE`.
- [ ] 75 and 89 return `MINOR_CORRECTION` and require follow-up.
- [ ] 50 and 74 return `OFFER_RECOVERY_OR_REMAKE`.
- [ ] 0 and 49 return `REMAKE_COMPONENT`.
- [ ] Target proximity is never computed when image trust < 60.

### Follow-up loop
- [ ] A correction proposal does not advance the recipe.
- [ ] User must confirm applying the correction.
- [ ] Follow-up requires a new image and non-blank status text.
- [ ] Follow-up is linked to its parent attempt and the same recipe/component.
- [ ] Every follow-up creates a new row; earlier attempts remain unchanged.
- [ ] Re-analysis considers previous observations and confirmed actions.

### Contract and persistence
- [ ] Invalid JSON, unknown enums, extra properties and invalid ranges are rejected.
- [ ] Inconsistent status/action combinations are rejected.
- [ ] Every attempt has a recipe FK when associated with a saved recipe.
- [ ] Session/recipe/attempt ownership is enforced.
- [ ] Flyway migration works against an existing database with Hibernate `ddl-auto=validate`.

### Regression
- [ ] Existing recipe generation and review flows continue to work.
- [ ] Existing cooking session endpoints remain compatible or are versioned deliberately.
- [ ] Backend tests and mobile TypeScript/build checks pass.

## 13. Suggested implementation sequence

1. Confirm a working Runware vision model and multimodal request format; add separate vision model configuration.
2. Define recovery DTOs, JSON schema, enums and semantic validator.
3. Add recovery orchestration/provider adapter and image-trust gate.
4. Add Flyway migration and `recipe_recovery_attempt` entity/repository.
5. Implement initial-attempt, action-confirmation and follow-up endpoints with transactional state transitions.
6. Update mobile UI to support all recovery states and the follow-up loop.
7. Add boundary, malformed-output, provider-failure, ownership and persistence tests.
8. Run backend tests and mobile build; document actual provider/model tested.

---

## 14. Non-goals for the first implementation

- Automatically rewriting the complete recipe.
- Guaranteeing a perfect result from a photo.
- Claiming the percentage is an objective physical measurement.
- Learning or changing score thresholds autonomously.
- Supporting every food category and every possible correction action from day one.

The first release should support a deliberately limited set of component types and correction actions with high-quality validation. Extensibility comes from generic contracts and component-aware rules, not from allowing the model to invent arbitrary instructions.
