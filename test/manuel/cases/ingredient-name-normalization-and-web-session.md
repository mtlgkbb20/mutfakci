# Ingredient Name Normalization & Web Session Restore — Manual Test Cases

**Branch:** `test/KAI-018-unit-test-coverage`
**Scope:** Pantry/zero-waste ingredient keys (`IngredientNames.normalize`, Flyway `V21`), web session restore via `GET /api/v1/auth/me`.
**Status:** All cases are `NOT RUN` until executed against a running backend and web app.

## Ingredient name normalization

| ID | Scenario | Preconditions / data | Steps | Expected result | Status |
|---|---|---|---|---|---|
| ING-001 | Hyphenated remainder merges into pantry | User A has pantry item `Domates salçası`, 200 g | 1. Record a zero-waste remainder `domates-salçası`, 50 g. 2. Transfer it to the pantry. | The existing item becomes 250 g; no second `domates-salçası` row is created; one `ADD` movement is recorded | NOT RUN |
| ING-002 | Pantry item with punctuation matches recipes | Recipe catalog contains `Domates salçası` | 1. Add pantry item `Domates-Salçası`. 2. Open pantry recipe matches. | Recipes needing tomato paste list it as available | NOT RUN |
| ING-003 | Incompatible units still rejected | Pantry `Domates salçası` 1 piece | Transfer remainder `domates-salçası` 50 g | HTTP 400 "Pantry birimi ile artan malzeme birimi uyumlu değil."; pantry unchanged | NOT RUN |
| ING-004 | V21 migration on existing data | Postgres DB at V20 with pantry rows `Domates-salçası` (`domatessalcasi`) and `Süt` (`sut`) | 1. Start the backend so Flyway runs. 2. `SELECT name, normalized_name FROM pantry_item`. | `domates salcasi` and `sut`; names, quantities, units and `pantry_movement` rows unchanged; `flyway_schema_history` has version 21 | NOT RUN |
| ING-005 | V21 with keys that now collide | Same user has `Domates-salçası` 200 g and `Domates salçası` 1 piece before migration | Run migration, then transfer a 50 g remainder | Both items still exist (not merged); the transfer goes to the oldest item; no HTTP 500 | NOT RUN |
| ING-006 | Cross-user isolation | User B has `Domates salçası` | User A transfers a `domates-salçası` remainder | Only User A's pantry changes | NOT RUN |

## Web session restore

| ID | Scenario | Preconditions / data | Steps | Expected result | Status |
|---|---|---|---|---|---|
| WEB-001 | Reload keeps the session | Logged in on web | Press F5 | A short "Oturumun yükleniyor..." state appears, then the dashboard with "Merhaba, <ad>"; no login form flash | NOT RUN |
| WEB-002 | Expired token | Logged in; wait past `JWT_EXPIRATION_MINUTES` (or edit token in DevTools) | Reload the page | The login form appears; `localStorage.token` is removed | NOT RUN |
| WEB-003 | Backend down on reload | Logged in | 1. Stop the backend. 2. Reload. | Retry message "Oturumun şu anda doğrulanamadı…" with "Tekrar dene" and "Çıkış yap"; token is still in `localStorage` | NOT RUN |
| WEB-004 | Retry after backend returns | Continue from WEB-003 | Start the backend, click "Tekrar dene" | Dashboard loads without logging in again | NOT RUN |
| WEB-005 | Logout from retry screen | Retry screen visible | Click "Çıkış yap" | Login form appears; token removed | NOT RUN |
| WEB-006 | Deleted account | Logged in, then delete the user row in the DB | Reload | `/auth/me` returns 401; login form appears (no 500) | NOT RUN |
| WEB-007 | Admin flag after reload | Logged in as admin | Reload | Admin event form is still visible; for a regular user it is not | NOT RUN |
| WEB-008 | Logout | Logged in | Click "Çıkış", then reload | Login form stays; no `/auth/me` request is sent | NOT RUN |
