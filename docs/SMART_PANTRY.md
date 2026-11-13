# Smart Pantry (Akıllı Kiler) — Implementation Specification

**Task:** KitchenAI V1 / ID 6  
**Status:** PR Opened — pending review and CI verification  
**Branch:** `feature/smart-pantry`  
**Source:** Notion task — Smart Pantry (Akıllı Kiler)

## 1. Goal

Provide each authenticated user with a private, quantity-aware pantry inventory. Users can maintain what they have, see items nearing expiry, and discover recipes they can make from current stock while clearly identifying missing ingredients. Stock changes must be auditable and safe against duplicate deductions.

## 2. MVP scope

### Included
- Create, list, update and delete pantry items.
- Track normalized item name, quantity, unit, category and optional expiry date.
- Record stock additions, corrections and consumption as immutable movement records.
- Deduct recipe ingredients only after explicit user confirmation.
- Make deduction idempotent using a client-supplied operation key.
- Show expired / expiring-soon items in a separate prioritized view.
- Match the curated recipe catalog against available stock and return missing ingredients and quantities.
- Keep all pantry reads and writes scoped to the authenticated user.

### Explicitly out of scope
- Barcode scanning, receipt OCR, grocery/market integrations, automatic purchasing.
- Automatic stock deduction when a cooking session completes without a separate user confirmation.
- AI-based guessing of quantity/unit conversions.

## 3. Domain model

- `PantryItem`: owner, canonical/display name, normalized name, category, quantity, unit, expiry date, timestamps, optimistic version.
- `PantryMovement`: owner, item, movement type (`ADD`, `ADJUST`, `CONSUME`), quantity delta, unit, reason/reference, idempotency key, timestamp.
- `PantryUnit`: use a controlled set of mass, volume and count units. Convert only within compatible dimensions (g/kg, ml/l); never infer cross-dimension conversions or ingredient-specific density.

A stock quantity may not become negative. An update that changes the item quantity must also create a movement record in the same database transaction. Deleting an item should preserve its movement history (soft-delete/archive rather than cascading history).

## 4. API contract

All endpoints are authenticated under `/api/v1/pantry`.

- `GET /items` — current user's active inventory.
- `POST /items` — create an item and initial `ADD` movement.
- `PATCH /items/{id}` — edit metadata and/or quantity; quantity changes create `ADJUST` movements.
- `DELETE /items/{id}` — archive item without deleting movements.
- `GET /movements?itemId=&limit=` — current user's movement history.
- `GET /expiring?withinDays=7` — expired and soon-to-expire items, earliest expiry first.
- `POST /recipe-matches` — evaluate curated recipes against pantry stock.
- `POST /recipe-consumptions` — explicitly confirmed, idempotent stock deduction for a recipe; body includes operation key and ingredient lines.

Never accept a user ID from request bodies or query parameters as an ownership selector. Derive owner from `@AuthenticationPrincipal UUID`.

## 5. Recipe matching

Use the existing curated recipe catalog as the initial recipe source. Normalize names using the existing recipe text normalization conventions. Match exact normalized ingredient names and compatible units only. Return:
- recipe identity/title and serving count;
- matched ingredients and available quantities;
- missing ingredients and required quantities;
- a deterministic coverage score.

Do not claim a recipe is makeable when an ingredient cannot be safely compared. Treat unknown units or ambiguous ingredient equivalence as missing/needs-review rather than silently converting.

## 6. Transaction and idempotency rules

- All stock writes and movement inserts occur in one transaction.
- Enforce idempotency with a unique constraint on (owner, operation key).
- A repeated consumption request returns the previously recorded result and must not deduct stock again.
- Lock affected pantry rows or use optimistic version checks to prevent concurrent overspending.
- Validate all requested lines before applying any deduction; fail the whole operation if any line is invalid or insufficient.
- Movement quantities are stored as signed deltas in the item's canonical unit.

## 7. Data migration

Add a new Flyway migration after the current latest migration. Do not edit historical migrations. Add indexes for owner + active item lookup, expiry date, item movement history, and unique idempotency key.

## 8. Client experience

Add a Pantry entry point to the existing authenticated dashboard. The screen should support:
- inventory list and category grouping;
- add/edit item form (name, quantity, unit, category, expiry);
- expiry badges and a dedicated expiring list;
- movement history;
- recipe suggestions with missing ingredient details;
- explicit confirmation before stock deduction.

Keep forms concise and validate quantities/units client-side, while treating backend validation as authoritative.

## 9. Acceptance criteria

- [ ] Authenticated users can create, read, update, and archive only their own pantry items.
- [ ] Quantity changes always create a movement in the same transaction.
- [ ] Negative stock is rejected.
- [ ] Expired and soon-to-expire items are listed in expiry order.
- [ ] Curated recipe matches show missing ingredients and quantities.
- [ ] Stock deduction requires explicit confirmation.
- [ ] Duplicate operation keys never cause duplicate deductions.
- [ ] Concurrent requests cannot overdraw an item.
- [ ] Unit conversion is deterministic and dimension-safe.
- [ ] Backend tests cover ownership, CRUD, movement audit, expiry, matching, idempotency and insufficient stock.
- [ ] Client build/typecheck passes.

## 10. Implementation notes / assumptions

- Backend is Spring Boot 3 / Java 21 / PostgreSQL / Spring Data JPA / Flyway.
- Existing JWT filter exposes authenticated user UUID as the principal.
- Existing curated catalog JSON is the source for MVP recipe matching.
- UI implementation should follow the existing mobile Expo app patterns; web frontend may remain unchanged unless the current product navigation already exposes it.
- Notifications are not required for MVP; expiry is surfaced in-app.
