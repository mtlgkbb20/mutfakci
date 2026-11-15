# Smart Grocery Assistant — Manual Acceptance Tests

Status: all cases are **NOT RUN** until exercised on a running build.

## SG-001 — Create list from one recipe
- Preconditions: authenticated user; curated recipe exists; pantry may be empty.
- Steps:
  1. Create a shopping list from one recipe and a serving count different from its default.
  2. Open the saved list.
- Expected: ingredient quantities are scaled; list is persisted; unknown recipe IDs are rejected.
- Result: NOT RUN

## SG-002 — Aggregate multiple recipes
- Preconditions: at least two catalog recipes share an exact normalized ingredient.
- Steps:
  1. Create a list from both recipes.
  2. Inspect the shared ingredient.
- Expected: quantities are summed after serving scaling only when units are compatible; incompatible units remain separate with a warning.
- Result: NOT RUN

## SG-003 — Build from weekly meal plan
- Preconditions: user owns a saved weekly plan.
- Steps:
  1. Create a shopping list using the plan ID.
  2. Attempt the same with another user's plan ID.
- Expected: owned plan generates a list; cross-user plan returns not found without leaking data.
- Result: NOT RUN

## SG-004 — Subtract pantry stock
- Preconditions: pantry contains an unexpired matching ingredient with compatible unit.
- Steps:
  1. Generate a list containing that ingredient.
  2. Compare required, pantry-covered, and to-buy quantities.
  3. Check pantry movement history.
- Expected: coverage is capped at requirement; to-buy is non-negative; list generation does not mutate pantry stock.
- Result: NOT RUN

## SG-005 — Expired / incompatible stock
- Preconditions: expired matching item and/or same-name item with incompatible unit.
- Steps:
  1. Generate a list requiring the ingredient.
- Expected: expired stock is not deducted; incompatible units are not silently converted.
- Result: NOT RUN

## SG-006 — Edit and categorize items
- Preconditions: active saved list.
- Steps:
  1. Add a manual item.
  2. Edit its quantity and category.
  3. Remove another item.
  4. Reload the list.
- Expected: changes persist and remain scoped to the list owner.
- Result: NOT RUN

## SG-007 — Purchase selected quantities
- Preconditions: active list with at least two items.
- Steps:
  1. Mark one item purchased with a partial quantity and leave another unchecked.
  2. Complete shopping with an operation key.
  3. Inspect pantry inventory and movement history.
- Expected: only selected purchased quantities are added; each addition has an ADD movement; list becomes completed.
- Result: NOT RUN

## SG-008 — Idempotent completion
- Preconditions: a list has been completed successfully.
- Steps:
  1. Replay the same completion request with the same operation key.
  2. Attempt completion with a different key.
- Expected: replay does not duplicate stock; a completed list cannot be applied again.
- Result: NOT RUN

## SG-009 — Atomic failure
- Preconditions: purchase request includes one valid line and one invalid/over-limit line.
- Steps:
  1. Submit completion.
  2. Inspect list state, pantry stock, and movements.
- Expected: entire operation rolls back; no partial pantry additions or completion state.
- Result: NOT RUN

## SG-010 — Ownership and authentication
- Preconditions: two test users and one list per user.
- Steps:
  1. Attempt list read/edit/item mutation/completion using the other user's IDs.
  2. Repeat without authentication.
- Expected: cross-user resources return 404; unauthenticated calls are rejected; no data leaks.
- Result: NOT RUN

## SG-011 — Price unavailable
- Preconditions: no trusted price provider configured.
- Steps:
  1. Open list summary.
- Expected: cost estimate is explicitly unavailable; no fabricated total is shown.
- Result: NOT RUN
