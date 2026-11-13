# KAI-010 — Cooking Journal (Pişirme Günlüğü)

## Goal
Keep a private, durable record of completed cooking sessions, their photos, and personal recipe changes. A journal entry captures the recipe as it was cooked, so later recipe changes do not rewrite history.

## Scope
- Complete an active cooking session explicitly from the journal screen and award XP through the existing completion endpoint.
- Create one journal entry for a completed cooking session.
- Preserve an immutable JSON snapshot of the recipe title, description, category, servings, preparation/cooking time, ingredients, and stages.
- Store cooked servings, optional 1–5 rating, optional notes, and an optional private photo.
- Replace or delete the photo through authenticated owner-scoped endpoints.
- List the latest 100 entries, inspect a single entry, and list attempts for a recipe.
- Compare attempts through their dates, ratings, servings, notes, and immutable snapshots.
- Edit servings, rating, and notes without changing the recipe snapshot.
- Create a separate private RecipeVariation from a journal snapshot, allowing the user to edit title, description, servings, ingredients, and stages without mutating the original recipe.

## Data model
- `CookingJournalEntry`: one immutable recipe snapshot per completed cooking session, with editable attempt metadata.
- `CookingJournalMedia`: private media reference separated from the journal record.
- `RecipeVariation`: user-owned recipe derived from a journal entry and linked to its source recipe.
- `JournalMediaStorage`: private local storage adapter with sanitized image bytes and owner-scoped reads/deletes.

## API
All endpoints require authentication.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/cooking-journal` | Create from a completed session |
| GET | `/api/v1/cooking-journal` | Latest 100 entries |
| GET | `/api/v1/cooking-journal/{entryId}` | Entry detail |
| GET | `/api/v1/cooking-journal/recipes/{recipeId}/attempts` | Attempts for a recipe |
| PATCH | `/api/v1/cooking-journal/{entryId}` | Update servings, rating, notes |
| PUT | `/api/v1/cooking-journal/{entryId}/photo` | Upload/replace private photo (multipart `image`) |
| GET | `/api/v1/cooking-journal/{entryId}/photo` | Read private photo |
| DELETE | `/api/v1/cooking-journal/{entryId}/photo` | Delete photo |
| POST | `/api/v1/cooking-journal/{entryId}/variations` | Create a separate recipe variation |
| GET | `/api/v1/cooking-journal/variations` | List own variations |
| GET | `/api/v1/cooking-journal/variations/{variationId}` | Read own variation |

### Create journal payload
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

### Variation payload
```json
{
  "title": "Elmalı kek — cevizli varyasyon",
  "description": "Daha az şeker ve ekstra ceviz.",
  "servings": 6,
  "ingredientsJson": "[{\"name\":\"Elma\"},{\"name\":\"Ceviz\"}]",
  "stagesJson": "[{\"title\":\"Karıştır\"}]"
}
```

The variation is stored in its own table and never overwrites the source `recipe` row. Ingredient and stage overrides must be valid JSON arrays.

## Media security
- Only JPEG and PNG uploads are accepted; max 10 MB, minimum 160 px per dimension, maximum 20 MP.
- Images are decoded and re-encoded to strip embedded metadata.
- Files are stored under an owner-specific directory with restrictive POSIX permissions where supported.
- Media keys are never accepted from clients; the server generates them.
- Reads and deletes require the authenticated owner and use normalized path checks.
- Photo responses use `Cache-Control: no-store`.

## Data integrity and security
- User identity is taken from `@AuthenticationPrincipal`, never from request JSON.
- Session and recipe ownership are checked server-side.
- Cross-user lookups return 404 to avoid leaking resource existence.
- A unique constraint on `cooking_session_id` prevents duplicate journal entries.
- Snapshot data is stored on the journal entry and never re-read from the mutable recipe for display.
- Variation and media references are separate entities.
- Migration is additive and uses V15 because the pending AI Meal Planner branch reserves V14.

## Verification
Backend unit tests cover session completion, ownership, duplicate prevention, snapshot immutability, variation validation, and private media storage. Manual cases are in `test/manuel/cases/cooking-journal.md`.
