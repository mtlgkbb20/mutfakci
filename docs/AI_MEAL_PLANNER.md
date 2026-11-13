# AI Meal Planner — Technical Analysis

**Task:** Notion ID 7 — AI Meal Planner (Haftalık Yemek Planlayıcı)  
**Branch:** `feature/ai-meal-planner`  
**Base:** `feature/smart-pantry` (Smart Pantry PR #17 is not merged yet)  
**Status:** Analysis complete; implementation follows this document.

## 1. Goal and acceptance criteria

Create and persist a seven-day meal plan based on user goals, schedule, budget, servings, dietary preferences, preparation-time limits, and Smart Pantry stock.

The implementation must satisfy the Notion acceptance criteria:

- A user can create and save a weekly plan.
- A plan can be edited; replacing one meal preserves every other entry.
- Servings and dietary preferences affect recipe selection and quantities.
- A consolidated, deduplicated shopping list can be generated from the whole plan.
- Existing pantry ingredients are prioritized and excluded from the shopping list to the extent that they cover the required amount.

Nutrition values are out of scope unless a trusted nutrition source is available. No AI-estimated calories/macros will be presented as verified facts.

## 2. Existing codebase findings

### Recipe domain

- `RecipeCatalogService` loads the curated catalog from `src/main/resources/recipe-catalog/recipes.json`.
- Catalog recipes have stable IDs, titles, aliases, categories, default servings, estimated total time, ingredient quantities/units, and ordered steps.
- `RecipeController` supports recipe generation and persists user-generated recipes, but its generation flow is not a weekly planning API.
- Catalog entries are currently the only reliable, stable recipe references suitable for a plan. AI-generated recipes are persisted as user-owned `Recipe` rows but do not yet have a stable catalog-level ingredient taxonomy.

### Smart Pantry

- Pantry data is user-owned and stored in `pantry_item`.
- Active stock can be read through `PantryItemRepository`; it includes normalized names, quantity, unit, and expiry date.
- The existing pantry controller has unit compatibility/conversion logic and a curated-recipe matching endpoint.
- Stock deduction is explicit, transactional, and idempotent through `pantry_operation`; planning must never deduct stock automatically.
- Existing pantry matching is exact normalized-name matching. It does not safely infer that semantically related ingredients are interchangeable.

### AI provider

- The current AI abstraction is recipe-generation-specific (`RecipeGenerationService`) and generates recipe content, not catalog recipe selections.
- Provider calls are billable and the existing adapter deliberately avoids automatic retries after a response.
- The planner must not accept arbitrary recipe names or instructions from a model. Its structured output may only refer to IDs supplied by the backend.

### Mobile

- The Expo Router mobile app uses a shared authenticated `api()` helper in `mobile/src/api.ts`.
- Existing screens are route-based under `mobile/app`; Smart Pantry is exposed at `mobile/app/pantry.tsx`.
- Mobile package scripts currently provide Expo start/platform commands; there is no dedicated mobile unit-test command in package scripts.

## 3. Architectural decisions

### 3.1 Source of truth and recommendation strategy

Use the curated recipe catalog as the source of truth for the first version. The backend builds a bounded candidate set from catalog IDs and recipe metadata. The recommendation layer may rank/select only those candidate IDs; it cannot create recipes, ingredients, quantities, or instructions.

All returned IDs are resolved server-side against `RecipeCatalogService`. Unknown IDs, duplicate entries, invalid meal slots, and invalid day assignments are rejected. If the configured AI planner provider is unavailable or returns an invalid response, the service uses a deterministic ranking fallback over the same candidate set. The response must not imply that the fallback was an AI-generated result.

This isolates the planner from provider-specific JSON and keeps a deterministic, testable safety boundary.

### 3.2 Persistence model

Introduce two tables/entities:

**MealPlan**
- UUID ID and authenticated owner UUID.
- Week start date (Monday), plan status, currency, optional weekly budget.
- Requested servings, daily meal slots, max preparation minutes, dietary preferences, user goal/notes.
- Created/updated timestamps and optimistic version.

**MealPlanEntry**
- UUID ID, parent plan ID, day/date, meal slot, catalog recipe ID.
- Planned servings and optional user note.
- Stable unique constraint on (plan_id, date, meal_slot).
- Recipe title/time/ingredients are resolved from the catalog at read time; the plan stores the stable recipe ID rather than duplicating recipe JSON.

Plan ownership is always checked through the parent plan. Child mutations must be scoped to the authenticated owner.

### 3.3 Meal slots and week semantics

- Seven consecutive dates beginning on a Monday.
- Initial MVP supports configurable slots from `BREAKFAST`, `LUNCH`, and `DINNER`.
- A user may request one, two, or three slots per day.
- Week start is validated as Monday. The API uses ISO dates; timezone is not used to shift a date-only meal assignment.

### 3.4 Deterministic validation

Before persistence, validate:

- Exactly seven dates in the requested week and no duplicate (date, slot).
- Every selected recipe ID exists in the current curated catalog.
- Recipe dietary tags/ingredients satisfy supported exclusions. Unsupported dietary inference must not be claimed.
- Recipe estimated time does not exceed the requested limit.
- Requested servings are within the existing recipe serving constraints (1–30).
- Repetition is penalized; exact repeated recipes are avoided unless the catalog is too small to fill the requested slots.
- Budget is only a hard constraint when recipe cost metadata exists. The current catalog has no price data, so the API must mark budget validation as unavailable rather than inventing prices or claiming the budget is met.

The current catalog has no reliable cost or dietary-tag schema. This is an explicit limitation, not something the AI may fill with fabricated facts.

### 3.5 Pantry-aware shopping list

Build the shopping list from the final plan entries:

1. Resolve catalog ingredients and scale quantities by `plannedServings / defaultServings`.
2. Normalize ingredient names using the same conservative normalization concept as Pantry.
3. Aggregate only ingredients with the same normalized name and compatible units.
4. Convert mass (g/kg) and volume (ml/l) into canonical units; do not convert volume to mass without ingredient-specific density data.
5. Subtract matching active pantry quantities; cap available stock at the required amount.
6. Return required, pantry-covered, and to-buy quantities separately.
7. For incompatible or unsupported units, keep separate lines and flag that automatic stock subtraction was skipped.

Shopping-list generation is read-only. Pantry stock is not consumed when a plan or list is generated. Any future stock deduction must be a separate explicit user action and use the existing idempotent pantry consumption flow.

## 4. API proposal

All routes are under `/api/v1/meal-plans` and use the existing JWT-authenticated `@AuthenticationPrincipal UUID userId`.

| Method | Route | Purpose |
|---|---|---|
| POST | `/` | Generate and save a weekly plan |
| GET | `/` | List the authenticated user's plans |
| GET | `/{planId}` | Read a plan with resolved recipe summaries |
| PATCH | `/{planId}/entries/{entryId}` | Replace one meal only |
| GET | `/{planId}/recipes` | List alternatives allowed by the saved plan constraints |\n| POST | `/{planId}/regenerate` | Replace all entries in the saved week |\n| GET | `/{planId}/shopping-list` | Generate consolidated shopping list |

Creation request fields: `weekStart`, `servings`, `mealSlots`, `maxPreparationMinutes`, optional `weeklyBudget` + `currency`, `dietaryRestrictions`, `goal`, and `notes`.

The planner response includes the selected recipe IDs, dates/slots, serving counts, validation warnings, and whether budget validation was possible. The shopping list returns grouped ingredient lines with required, pantry-covered, and to-buy amounts.

## 5. Failure and security behavior

- A user cannot read, mutate, or derive a shopping list for another user's plan.
- Unknown plan/entry IDs return 404 without revealing whether another user's resource exists.
- Invalid model output is discarded; it is never partially persisted.
- If the catalog cannot fill all requested slots under the constraints, return a clear validation error with the number of fillable slots; do not silently weaken dietary exclusions or time limits.
- Plan generation and entry replacement are transactional.
- Shopping-list generation is read-only and does not mutate pantry quantities.
- Provider/API keys remain server-side; no provider credentials are exposed to mobile.

## 6. Implementation plan

1. Add Flyway migration after V13 (V14) for `meal_plan` and `meal_plan_entry`.
2. Add entities, repositories, request/response contracts, catalog-backed planner service, deterministic validator, and controller.
3. Add shopping-list aggregation with conservative unit conversion and pantry subtraction.
4. Add unit tests for candidate ranking, validation, scaling, aggregation, unit compatibility, and pantry coverage.
5. Add controller/integration tests for ownership, persistence, single-entry replacement isolation, and shopping-list output.
6. Add manual test cases for the mobile/API flows.
7. Add mobile API methods and a Meal Planner screen reachable from the dashboard, including plan creation, weekly display, single-meal replacement, and shopping list.

## 7. Known MVP limitations

- Catalog is currently very small; plan completeness and variety are limited by the number of suitable curated recipes.
- No trusted recipe-cost dataset exists, so budget can be captured but cannot yet be truthfully enforced.
- Catalog recipes currently do not carry structured allergen/dietary metadata. Only explicit, safely checkable ingredient exclusions can be enforced; broad claims such as gluten-free or vegan must not be inferred from names.
- Nutrition values are not calculated or displayed.
- Pantry matching remains conservative and name-based; semantic ingredient substitution is intentionally excluded.
- The AI selection provider must be constrained to candidate IDs. A deterministic fallback is required so the feature remains testable without live provider credentials.

## 8. Definition of Done

- [ ] Backend schema, API, validation, and ownership checks implemented.
- [ ] Single-entry replacement preserves all other plan entries.
- [ ] Shopping list consolidates compatible ingredients and accounts for pantry stock without deducting it.
- [ ] Automated tests are executed and pass.
- [ ] Manual test plan is documented; unexecuted cases remain `NOT RUN`.
- [ ] Mobile flow is connected to the API and type-checks/builds.
- [ ] Documentation and Notion task status are updated.


## 9. Implementation status (2026-11-13)

Initial backend and mobile implementation is now present on this branch:

- V14 migration creates the plan and entry tables.
- Backend endpoints cover plan creation/list/detail, owned-plan alternatives, single-entry replacement, and shopping-list generation.
- Runware selection is constrained to candidate catalog IDs; missing credentials or invalid output falls back to deterministic selection.
- The shopping list scales ingredients by servings, aggregates matching names/units, and subtracts matching pantry stock without mutating it.
- Mobile dashboard links to a weekly planner screen with plan creation, daily display, meal replacement, and shopping-list view.
- Automated service tests and a manual test matrix have been added; execution results are still pending.

Implementation caveats:
- The current catalog contains very few recipes, so a valid plan may repeat the same recipe and returns a diversity warning.
- The current implementation can reject some explicit dietary restrictions based on ingredient-name checks, but it cannot prove broad allergen safety or handle semantic synonyms. The catalog needs structured dietary/allergen metadata before stronger guarantees can be made.
- Budget is collected and echoed but not enforced because there is no trusted price data.
- No nutrition values are generated or displayed.


### Supported dietary restriction inputs

The first implementation accepts only `vegan`, `vegetarian`/`vejetaryen`, `glutensiz`/`gluten free`, and `laktozsuz`/`lactose free`. Other restriction strings are rejected instead of being silently treated as verified. Ingredient-name matching is still not an allergen guarantee.
