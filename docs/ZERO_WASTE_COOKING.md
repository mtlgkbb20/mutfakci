# KAI-011 — Zero Waste Cooking (Sıfır İsraf Mutfak Asistanı)

**Notion task:** [Zero Waste Cooking — ID 12](https://app.notion.com/p/413d941988aa816e8840e7c9b190bbc9)  
**GitHub issue:** [#21](https://github.com/mtlgkbb20/mutfakci/issues/21)  
**Branch:** feature/KAI-011-zero-waste-cooking  
**Base:** main (PRs #17, #18, and #20 merged; V13–V15)  
**Next migration:** V16

## 1. Goal

Turn recipe leftovers into explicit, quantity-aware domain data. Before cooking, explain expected ingredient use and deterministic by-products. After cooking, let the user confirm the actual remainder, find compatible recipes, transfer it to Smart Pantry, or mark it consumed/discarded.

Zero Waste is an optimization objective shared by recipes and planning, not an AI-generated badge or a free-form suggestion.

## 2. Existing codebase findings

- RecipeCatalogService loads structured catalog recipes with ingredient name, quantity, unit, preparation, servings, and steps. It now accepts optional, versioned `byProducts` rules; the current 9 catalog recipes still define none.
- User-created recipes store ingredients as JSON in Recipe; these payloads must be parsed and validated before use.
- Smart Pantry owns the authoritative stock quantity in pantry_item; movements and idempotency are recorded in pantry_movement and pantry_operation.
- Pantry matching is conservative: normalized exact ingredient names and supported unit conversions. It does not infer synonyms or density.
- Meal Planner stores catalog recipe IDs and planned servings. Its current shopping-list logic is read-only.
- Cooking Journal links a completed cooking session to an immutable recipe snapshot.
- Smart Grocery Assistant is a separate roadmap task and is not yet implemented. Zero Waste must not depend on a nonexistent shopping-list API.

## 3. Proposed first-release flow

### 3.1 Preview before cooking

Input: a catalog recipe ID or an owned user recipe ID, plus servings.

Output:
- scaled recipe ingredient requirements;
- explicit by-products/remainders from structured, versioned recipe metadata;
- amount/unit certainty (EXACT, ESTIMATED, UNKNOWN);
- ingredients that cannot be converted safely;
- complementary catalog recipe candidates, when available.

The preview is advisory. It must not create stock, reserve inventory, or deduct pantry quantities.

### 3.2 Confirm after cooking

The user reviews the preview and confirms the actual amount remaining. The backend persists a user-owned remainder with:
- ingredient name and normalized name;
- positive quantity and canonical/declared unit;
- source recipe (catalog or user recipe) and optional cooking session/journal entry;
- certainty/source (USER_CONFIRMED, RULE_DERIVED, UNKNOWN);
- optional user-provided use-by date;
- status and optimistic version.

A planned by-product is never treated as real inventory until the user confirms it. If the user changes the preview quantity, the confirmed value wins.

### 3.3 Complementary recipe matching

Candidate recipes must come from the curated catalog (or an owned saved recipe if explicitly supported). A candidate is eligible only when:
- it uses the same conservatively normalized ingredient;
- unit compatibility is known;
- its scaled requirement does not exceed the available remainder, unless partial-use is explicitly supported;
- its own known by-products are included in the waste score.

Rank deterministically by: remainder quantity consumed, pantry coverage of other ingredients, additional shopping need, then new remainder generated. An AI provider may optionally rank the already validated candidate IDs, but may not invent recipes, quantities, substitutions, shelf life, or safety advice.

Selecting a suggestion does not consume the remainder. Consumption happens only after the user confirms actual use, ideally from the completed cooking session flow.

### 3.4 Pantry transfer and consumption

Remainders remain a separate ledger until the user explicitly acts:
- **Add to Pantry:** atomically create or adjust the matching owned pantry item, write a pantry movement, and transition the remainder to TRANSFERRED.
- **Mark consumed:** reduce the remainder quantity or transition it to CONSUMED after explicit confirmation.
- **Dismiss/discard:** record the user's explicit decision; do not silently delete history.

Use a single transaction and an idempotency key for each state-changing operation. Lock the remainder and matching pantry item, validate ownership/status/version, and prevent negative quantities or duplicate movement records. Never create a second stock source that can be counted alongside the transferred remainder.

## 4. Domain and persistence proposal

Implemented in V16: `ingredient_remainder` and `ingredient_remainder_operation`.

Suggested remainder fields:
- id, user_id;
- optional source_catalog_recipe_id, source_recipe_id, cooking_session_id, journal_entry_id;
- ingredient_name, normalized_name, quantity, unit;
- certainty, status, optional use_by_date;
- version, created_at, updated_at.

Implemented states: AVAILABLE, CONSUMED, TRANSFERRED, DISCARDED. Partial consumption retains an AVAILABLE remainder with reduced quantity; zero quantity transitions to CONSUMED.

The exact table layout should preserve an audit trail for every state/quantity change and enforce idempotency with a unique (user, operation key) constraint. Do not mutate V13–V15.

## 5. Deterministic quantity and by-product rules

- Scale catalog quantities by requestedServings / defaultServings using BigDecimal and a documented rounding policy.
- Do not convert volume to mass without ingredient-specific density.
- Do not infer that one unit of an ingredient yields another ingredient unless a versioned rule explicitly defines the ratio.
- Example rule: a recipe explicitly requiring 4 egg yolks can expose 4 egg whites as a potential by-product only if the recipe metadata declares a 1:1 count relationship. The user still confirms the actual remainder after cooking.
- Unknown initial stock or ambiguous units produce UNKNOWN, not a fabricated quantity.
- Expiry/use-by dates are optional user input. Do not invent shelf-life or food-safety claims.

## 6. API proposal

All endpoints require authentication; owner identity comes only from the JWT principal.

| Method | Route | Purpose |
|---|---|---|
| POST | /api/v1/zero-waste/preview | Preview scaled ingredients, by-products, and matching recipes |
| POST | /api/v1/zero-waste/remainders | Confirm and persist an actual remainder |
| GET | /api/v1/zero-waste/remainders | List available remainders |
| GET | /api/v1/zero-waste/remainders/{id} | Read one owned remainder |
| PATCH | /api/v1/zero-waste/remainders/{id} | Correct quantity, unit, or user-provided use-by date |
| POST | /api/v1/zero-waste/remainders/{id}/consume | Confirm full/partial consumption |
| POST | /api/v1/zero-waste/remainders/{id}/transfer-to-pantry | Transfer remainder into Smart Pantry |
| POST | /api/v1/zero-waste/remainders/{id}/dismiss | Explicitly dismiss/discard |
| GET | /api/v1/zero-waste/remainders/{id}/suggestions | Find compatible catalog recipes |

Payloads must carry an idempotency key for state-changing operations. No endpoint accepts a user ID from the client.

## 7. Security and integrity

- Enforce ownership for remainder, recipe, session, journal entry, and pantry item references.
- Cross-user resources return 404.
- Validate all quantities as positive and within database precision.
- Reject unsupported/incompatible unit conversions rather than guessing.
- Use optimistic version checks and/or row locks for concurrent remainder updates.
- Ensure pantry transfer and remainder transition commit or roll back together.
- Do not let a suggestion reserve or consume inventory implicitly.
- Keep personal remainder history private.

## 8. Testing strategy

Backend unit tests: serving scaling, by-product rule matching, unit conversion, candidate scoring, partial consumption, and state transitions.

Integration/controller tests: ownership, request validation, transaction rollback, pantry transfer, idempotency, duplicate requests, and concurrent updates.

Mobile tests: preview/confirm state, edit quantity, suggestions, transfer/consume confirmation, and API error states. Keep calculations outside screen components.

Manual acceptance cases: test/manuel/cases/zero-waste-cooking.md.

## 9. Dependencies and sequencing

- Smart Pantry PR #17 (V13): authoritative inventory and stock movements.
- AI Meal Planner PR #18 (V14): future cross-day complementary recipe integration.
- Cooking Journal PR #20 (V15): source cooking session and immutable recipe snapshot.
- Smart Grocery Assistant (Notion ID 11): later integration; not a blocking dependency for this first slice.

This branch is based on `main`; V13–V15 are already merged. V16 is the next additive migration.

## 10. Definition of Done

- [ ] Deterministic recipe by-product metadata and preview.
- [ ] User-confirmed remainder persistence and lifecycle.
- [ ] Verified catalog-only complementary recipe suggestions.
- [ ] Explicit, atomic, idempotent pantry transfer/consumption.
- [ ] Backend automated tests for calculations, ownership, state, and concurrency.
- [ ] Mobile flow and automated tests.
- [ ] Manual acceptance plan executed and evidence recorded.
- [ ] CI passes on the final commit.
- [ ] Notion and GitHub tracking links/statuses updated.


## 11. Catalog gap identified during implementation analysis

The current catalog contains 9 recipes. It has no tiramisu, meringue, pavlova, or recipe whose ingredient list uses egg whites; it also has no recipe by-product metadata. Therefore the yolk-to-white example cannot be demonstrated end-to-end with the current catalog alone.

The implementation must include a small, curated and reviewed catalog expansion:
- a recipe with an explicit egg-yolk ingredient and a declared 1:1 egg-white by-product rule;
- at least one verified egg-white recipe (for example, meringue) with exact ingredient quantities, servings, steps, and source/provenance;
- tests proving that preview quantities scale by servings and that suggestion matching uses only the verified egg-white ingredient.

Do not add recipes with guessed quantities or fabricate provenance. If a suitable verified recipe cannot be sourced in this task, keep the rule-engine/API behavior testable with explicit fixtures and report the catalog content as blocked rather than presenting an invented suggestion.


## 12. Implementation status — 2026-11-13

### Implemented on `feature/KAI-011-zero-waste-cooking`

- V16 creates `ingredient_remainder` and `ingredient_remainder_operation`, including owner/history indexes, operation-key uniqueness, quantity/status consistency, and references to recipe/session/journal records.
- `IngredientRemainder` supports AVAILABLE, CONSUMED, TRANSFERRED, and DISCARDED. Partial consumption reduces the amount; full consumption stores zero and transitions to CONSUMED.
- `ZeroWasteQuantityRules` scales with BigDecimal to 3 decimal places (HALF_UP), labels rounding as ESTIMATED, and supports only explicit mass/volume conversions plus equivalent count units. No mass-volume density inference.
- Recipe catalog metadata now supports optional `byProducts` entries with source ingredient/unit, output ingredient/unit, numerator/denominator, and a required rule version. The calculator propagates uncertainty.
- Authenticated API routes are implemented for preview, confirm, list/detail, correction, partial/full consume, transfer-to-pantry, dismiss, and catalog suggestions. Owner IDs are taken from the JWT principal.
- Pantry transfer uses the existing Smart Pantry item/movement/operation model and executes in the same Spring transaction as the remainder transition. The transfer operation key is namespaced to avoid collisions.
- Suggestions are currently exact normalized ingredient matches against curated catalog recipes and require enough remainder after serving scaling. Advanced scoring for pantry coverage and candidate-generated by-products remains pending.

### Automated verification on current implementation checkpoint

- Commit: `09172a35459df867a88abf7da4fabbbdb9f7ff30`
- Backend: `mvn --batch-mode test` — 57 tests, 0 failures, 0 errors.
- Frontend: build passed.
- Mobile: TypeScript check passed.
- CI run: [434271?](https://github.com/mtlgkbb20/mutfakci/actions) — inspect the commit checks for the exact job records.
- Unit coverage added for serving scaling, safe unit conversion, by-product ratio/uncertainty, preview, user confirmation, partial/full consumption, transfer invocation, and cross-user read isolation.

### Remaining work

- Curate and review real catalog recipes with source/provenance; do not fabricate egg-white recipes or quantities. Until then, current catalog previews correctly return no by-products.
- Add integration/controller tests for database constraints, JWT ownership, idempotency under concurrent requests, and transaction rollback during Pantry transfer.
- Improve suggestion ranking to include pantry coverage and each candidate's by-products.
- Implement mobile preview/confirm/remainder/suggestion/transfer flows and automated UI/state tests.
- Execute the manual acceptance cases. All manual cases remain NOT RUN until a person exercises them.
