# Smart Grocery Assistant — Technical Specification

**Task:** KAI-012 / Notion task ID 11  
**Branch:** `feature/KAI-012-ai-personalized-shopping`  
**Base:** `main`  
**GitHub issue:** #23  
**Status:** In progress

## 1. Goal

Provide a persistent, editable shopping-list workflow where AI analyzes the user's cooking journal and current Smart Pantry, recommends future catalog meals that fit demonstrated preferences, and builds one consolidated list of missing ingredients. Weekly meal plans are not an input to personalized shopping. Existing manual recipe/meal-plan creation remains backward-compatible.

## 2. Scope

### Included
- Create a list from one or more curated recipe IDs with per-recipe servings.
- Create a personalized list from cooking-journal history, ratings, notes, and current pantry stock using the configured Runware AI pipeline.
- AI selects only existing catalog recipes and provides a short reason for each recommendation; backend resolves exact ingredients and default servings.
- Persist the selected meal recommendations with the shopping list so they remain visible when reopened.
- Keep weekly-plan and explicit-recipe list creation available as separate legacy entry points; personalized flow does not depend on a weekly plan.
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

Use deterministic category assignment based on curated ingredient/category mappings, with `OTHER` fallback. Categories are presentation/grouping metadata, not a claim about product taxonomy. User may override a category. AI may rank catalog meals and explain preference fit, but it cannot author ingredient quantities, invent catalog IDs, or override pantry calculations.

## 9. Price behavior

No trusted product-price feed is currently configured. Return `costEstimateAvailable=false`, null total, and an explicit warning. Never estimate from fabricated or stale hard-coded prices.

## 10. Migration sequencing

Main already contains migrations through V19. This AI personalization extension adds V20 for persisted recommendation metadata. Never edit historical migrations.

## 11. Test strategy

- Unit: normalization, category mapping, quantity scaling, unit conversion, aggregation, pantry subtraction, purchased quantity validation.
- Service/persistence: list creation from recipe and plan, edit lifecycle, list ownership, completion transaction, rollback, idempotent replay, concurrent completion.
- Controller/HTTP: JWT ownership, validation, 404 cross-user behavior, serialization.
- Regression: existing meal-plan shopping-list response remains unchanged; pantry CRUD and movements remain compatible.
- Manual cases: documented in `test/manuel/cases/smart-grocery-assistant.md`; all remain NOT RUN until executed.

## 12. Delivery phases

1. Extend the existing shopping-list endpoint with a personalized AI mode.
2. Connect cooking-journal history and Smart Pantry stock to the Runware recommendation pipeline.
3. Persist recommended meal names/reasons and derive quantities exclusively from the curated recipe catalog.
4. Update the mobile flow to create a history-based list without selecting a weekly plan.
5. Run backend tests, frontend build/tests, mobile typecheck, and CI.
