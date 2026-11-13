# KAI-010 — Cooking Journal (Pişirme Günlüğü)

## Goal
Keep a private, durable record of completed cooking sessions. A journal entry captures the recipe as it was cooked, so later recipe changes do not rewrite history.

## Scope (first iteration)
- Create one journal entry for a completed cooking session.
- Preserve an immutable JSON snapshot of the recipe title, description, category, servings, preparation/cooking time, ingredients, and stages.
- Store the cooked serving count, optional 1–5 rating, and optional personal notes.
- List the latest 100 entries and inspect a single entry.
- List all attempts for a recipe, newest first.
- Edit servings, rating, and notes without changing the recipe snapshot.
- Enforce authenticated-user ownership for sessions, recipes, and journal entries.

Photo upload/editing and personal-variation authoring are intentionally not included in this first backend slice: the repository does not yet expose a journal-specific private media lifecycle or a stable versioned-recipe/variation contract. They should be added as follow-up work rather than storing arbitrary client-supplied object keys or mutating the source recipe.

## API
All endpoints require authentication.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/cooking-journal` | Create from a completed session |
| GET | `/api/v1/cooking-journal` | Latest 100 entries |
| GET | `/api/v1/cooking-journal/{entryId}` | Entry detail |
| GET | `/api/v1/cooking-journal/recipes/{recipeId}/attempts` | Attempts for a recipe |
| PATCH | `/api/v1/cooking-journal/{entryId}` | Update servings, rating, notes |

### Create payload
```json
{
  "cookingSessionId": "UUID",
  "servings": 4,
  "rating": 5,
  "notes": "İkinci denemede daha az fırınladım."
}
```

A session must belong to the authenticated user and have status `COMPLETED`. A session can be journaled only once. The recipe is resolved from the session on the server; clients cannot attach another user's recipe or forge a snapshot.

### Update payload
All fields are optional. Null means “leave unchanged”; an empty notes string clears the notes.
- servings: 1–30
- rating: 1–5
- notes: maximum 2,000 characters

## Data integrity and security
- User identity is taken from `@AuthenticationPrincipal`, never from request JSON.
- Session and recipe ownership are checked server-side.
- Cross-user lookups return 404 to avoid leaking resource existence.
- A unique constraint on `cooking_session_id` prevents duplicate journal entries.
- Snapshot data is stored on the journal entry and never re-read from the mutable recipe for display.
- Migration is additive and uses V15 because the pending AI Meal Planner branch already reserves V14.

## Verification
Automated backend tests are added in this task. Manual cases are in `test/manuel/cases/cooking-journal.md`.
