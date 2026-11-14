# Smart Grocery Assistant — Technical Specification

**Task:** KAI-012 / Notion task ID 11  
**Branch:** `feature/KAI-012-smart-grocery-assistant`  
**Base:** `main`  
**GitHub issue:** #23  
**Status:** In progress

## 1. Goal

Provide a persistent, editable shopping-list workflow from a single curated recipe, multiple recipes, or a saved weekly meal plan. Aggregate compatible ingredient requirements, subtract usable Smart Pantry stock, organize items by grocery category, and add purchased quantities to the existing pantry only after explicit confirmation.

## 2. Scope

### Included
- Create a list from one or more curated recipe IDs with per-recipe servings.
- Create a list from a saved MealPlan, preserving its source relationship.
- Consolidate exact normalized ingredient names with dimension-safe unit conversion.
- Scale catalog ingredient amounts using BigDecimal.
- Subtract unexpired pantry quantities; never consume pantry stock during list generation.
- Persist list and item rows so the user can edit them after generation.
- Add, edit, remove, and mark items as purchased / not purchased.
- Group items into stable grocery categories; allow manual category correction.
- Complete shopping with explicit confirmation of purchased quantities.
- Transactionally add purchased amounts to existing Smart Pantry inventory and record ADD movements.
- Idempotent completion and authenticated ownership.
- Cost estimate is explicitly unavailable until a trusted price source exists.

### Excluded
- Barcode scanning, OCR/receipt parsing, online supermarket integrations, automatic checkout.
- Price scraping or AI-generated prices.
- Semantic ingredient substitutions and density-based mass/volume conversions.
- Automatically changing pantry stock when a list is created or edited.

## 3. Existing contracts and reuse

- Recipe source: `RecipeCatalogService`; store catalog IDs and resolve ingredient data server-side.
- Meal plan source: `MealPlanService`; validate plan ownership and resolve saved entries server-side.
- Pantry source of truth: existing `PantryItem`, `PantryMovement`, and `PantryOperation` (V13). Do not create a second inventory.
- Existing planner endpoint `GET /api/v1/meal-plans/{planId}/shopping-list` remains backward-compatible. The new persisted-list workflow is additive.
- Reuse JWT principal UUID for all ownership checks.

## 4. Proposed domain model

### ShoppingList
- UUID, owner UUID, title, source type (`RECIPE`, `MEAL_PLAN`, `MIXED`, `MANUAL`), optional source meal-plan UUID.
- Status (`ACTIVE`, `COMPLETED`), currency (TRY default), created/updated timestamps, optimistic version.
- Completion timestamp and operation key for idempotency.

### ShoppingListItem
- UUID, parent list UUID, display name and normalized name, category.
- Required quantity, pantry-covered quantity, to-buy quantity, purchased quantity, canonical/display unit.
- Checked/purchased flag, timestamps, optimistic version.
- Preserve quantity provenance by retaining the generated requirement; user edits apply to the to-buy amount.

A list is always owner-scoped through its parent. Child IDs alone are never sufficient authorization.

## 5. Quantity and pantry rules

1. Resolve each recipe ID against the server catalog; reject unknown IDs.
2. Scale ingredient quantities by selected servings / catalog default servings using BigDecimal.
3. Normalize names conservatively (diacritic-insensitive, whitespace/punctuation normalization); do not merge semantic synonyms.
4. Convert only supported same-dimension units: kg/g, l/ml, and exact count aliases. Keep unsupported/custom units separate.
5. Aggregate same normalized name + canonical unit.
6. Subtract only active, unexpired pantry stock with the same normalized name and compatible unit. Cap coverage at the required quantity.
7. Do not infer density, ingredient equivalence, or missing quantities.
8. A list preview or edit never mutates pantry stock.

## 6. Purchase completion

- User supplies a unique operation key and purchased quantities for selected items.
- Reject negative quantities, quantities above the current list item's to-buy amount (unless explicitly edited first), incompatible units, and cross-user IDs.
- Lock the list and relevant pantry rows.
- In one database transaction: validate every line; create/update pantry items; create matching `ADD` movements; record the pantry operation; mark list completed and persist completion key/time.
- A replay with the same key returns the existing completion result without adding stock again.
- A completed list cannot be completed again with a different key.
- A failure on any line rolls back every pantry and list change.

## 7. API surface (proposed)

Base: `/api/v1/shopping-lists`

- `POST /` — create from recipe IDs or mealPlanId.
- `GET /` — list current user's lists.
- `GET /{listId}` — detail with items and cost availability.
- `PATCH /{listId}` — update title / list metadata.
- `POST /{listId}/items` — add manual item.
- `PATCH /{listId}/items/{itemId}` — edit quantity, unit, category, purchased amount, checked state.
- `DELETE /{listId}/items/{itemId}` — remove item.
- `POST /{listId}/complete` — explicit purchase confirmation and pantry transfer.

All routes authenticated. Never accept owner UUID from clients.

## 8. Grocery categories

Use deterministic category assignment based on curated ingredient/category mappings, with `OTHER` fallback. Categories are presentation/grouping metadata, not a claim about product taxonomy. User may override a category. Do not call an AI model for quantity/category correctness.

## 9. Price behavior

No trusted product-price feed is currently configured. Return `costEstimateAvailable=false`, null total, and an explicit warning. Never estimate from fabricated or stale hard-coded prices.

## 10. Migration sequencing

The current `main` head is `c29e41e` and contains migrations V13–V15. KAI-011 PR #22 contains V16 but is still open and not on main. This feature requires V17. Therefore:
- Keep this branch based on main for isolated development.
- V17 is the next schema version after the pending KAI-011 V16.
- Merge ordering must be KAI-011 V16 first, then KAI-012 V17. Do not merge this PR ahead of #22.
- Never edit historical migrations.

## 11. Test strategy

- Unit: normalization, category mapping, quantity scaling, unit conversion, aggregation, pantry subtraction, purchased quantity validation.
- Service/persistence: list creation from recipe and plan, edit lifecycle, list ownership, completion transaction, rollback, idempotent replay, concurrent completion.
- Controller/HTTP: JWT ownership, validation, 404 cross-user behavior, serialization.
- Regression: existing meal-plan shopping-list response remains unchanged; pantry CRUD and movements remain compatible.
- Manual cases: documented in `test/manuel/cases/smart-grocery-assistant.md`; all remain NOT RUN until executed.

## 12. Delivery phases

1. Document API/domain design and manual acceptance matrix.
2. Implement V17 schema and backend domain/service/controller with automated tests.
3. Add mobile list creation, editing, category grouping, purchased check-off, and confirmation UI.
4. Run backend tests, frontend build/tests, mobile tests/typecheck, and CI.
5. Open PR targeting main with explicit dependency note: merge KAI-011 #22 first.
