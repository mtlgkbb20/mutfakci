# KAI-010 — Cooking Journal Manual Test Cases

**Branch:** fix/KAI-010-cooking-journal-ux  
**Original feature branch:** feature/KAI-010-cooking-journal  
**GitHub Issue:** [#19](https://github.com/mtlgkbb20/mutfakci/issues/19)  
**Original Pull Request:** [#20](https://github.com/mtlgkbb20/mutfakci/pull/20)  
**Notion:** [Cooking Journal](https://app.notion.com/p/413d941988aa81fc936ac233fcb3b92f)

All cases are initially NOT RUN. Record evidence before changing a status.

| Case | Scenario | Preconditions / Steps | Expected result | Status |
|---|---|---|---|---|
| CJ-001 | Complete and save a cooking session | Sign in; open journal; mark an active session complete; save it with servings, rating, and notes | Session completes, XP is awarded by existing endpoint, journal entry is created with snapshot | NOT RUN |
| CJ-002 | Reject active session | Start a session but do not complete it; POST a journal entry | HTTP 409; no entry is created | NOT RUN |
| CJ-003 | Reject duplicate journal entry | Create an entry; submit the same session again | HTTP 409 / unique constraint; original entry remains unchanged | NOT RUN |
| CJ-004 | List private journal | Create entries for the current user; GET journal | Only current user's entries are returned, newest first | NOT RUN |
| CJ-005 | Read journal detail | Open an owned entry by ID | Snapshot and recorded metadata are returned | NOT RUN |
| CJ-006 | Prevent cross-user read | Sign in as another user and request the entry ID | HTTP 404; no data is disclosed | NOT RUN |
| CJ-007 | Update rating and notes | PATCH an owned entry with rating 4 and notes | Values update; recipe snapshot remains identical | NOT RUN |
| CJ-008 | Validate rating | PATCH with rating 0 or 6 | HTTP 400; no change is persisted | NOT RUN |
| CJ-009 | Validate servings | Create/update with servings 0 or 31 | HTTP 400; no invalid value is persisted | NOT RUN |
| CJ-010 | Enforce notes limit | Create/update with notes longer than 2,000 characters | HTTP 400 | NOT RUN |
| CJ-011 | Recipe attempt history | Create multiple completed sessions for one recipe and journal them | Attempts endpoint returns only current user's entries for that recipe, newest first | NOT RUN |
| CJ-012 | Compare attempts | Open a recipe with multiple journal entries | Dates, servings, ratings, notes, and original snapshots can be compared without data mixing | NOT RUN |
| CJ-013 | Snapshot immutability | Save entry; change source recipe through a supported variation flow; read journal entry | Journal snapshot remains as originally cooked | NOT RUN |
| CJ-014 | Recipe ownership | Try to create a session using another user's recipe, then journal | Session creation/journal flow cannot expose another user's recipe | NOT RUN |
| CJ-015 | Upload private photo | Upload a JPEG/PNG to an owned journal entry | Photo is sanitized, stored under the owner's private directory, and shown in the entry | NOT RUN |
| CJ-016 | Replace photo | Upload a second photo to the same entry | New photo is displayed; previous stored file is deleted | NOT RUN |
| CJ-017 | Delete photo | Delete photo from an owned entry | Media reference and file are removed; entry remains | NOT RUN |
| CJ-018 | Reject invalid photo | Upload SVG, malformed image, oversized image, or too-small image | HTTP 400/413; no media reference is stored | NOT RUN |
| CJ-019 | Prevent cross-user photo access | Sign in as another user and request the photo endpoint | HTTP 404; bytes are not disclosed | NOT RUN |
| CJ-020 | Create personal variation | Create a variation from a journal entry and change ingredients/stages | A new user-owned variation is created and linked to the source recipe | NOT RUN |
| CJ-021 | Keep source recipe unchanged | Create a variation with changed ingredients and stages | Original recipe and historical journal snapshot remain unchanged | NOT RUN |
| CJ-022 | Reject invalid variation JSON | Submit malformed/non-array ingredients or stages JSON | HTTP 400; no variation is persisted | NOT RUN |
| CJ-023 | Prevent cross-user variation access | Request another user's variation ID | HTTP 404 | NOT RUN |
| CJ-024 | Unauthenticated access | Call each endpoint without a valid token | Authentication layer rejects the request | NOT RUN |
| CJ-025 | Summarize active sessions | Create 0, 1, 3, and more than 3 active sessions | Section shows the total count, at most the first 3 recipe names, and + X more when needed; no full list is rendered inline | NOT RUN |
| CJ-026 | Open active-session manager | Tap “Tüm oturumları gör” with multiple active sessions | A modal lists all active sessions and allows each to be marked completed | NOT RUN |
| CJ-027 | Resolve session recipe names | Create active and completed sessions linked to saved recipes | Session rows show the actual recipe title resolved from the user's recipe list, not generic “Tarifli pişirme” text | NOT RUN |
| CJ-028 | Select completed cooking for journal | Complete multiple sessions; open “Pişirme seç” | A searchable selection modal lists unjournaled completed sessions; selecting one opens only that session's journal editor | NOT RUN |
| CJ-029 | Filter completed-cooking choices | Search completed sessions by a partial recipe title | Only matching sessions are shown; selecting one binds the journal form to that session | NOT RUN |
| CJ-030 | Select one historical attempt | Create multiple journal entries; open “Deneme seç” | A searchable selection modal lists attempts; selecting one shows only that attempt's editable details | NOT RUN |
| CJ-031 | Filter historical attempts | Search historical attempts by recipe title | Only matching attempts are shown and the selected attempt remains unambiguous | NOT RUN |
| CJ-032 | Empty selection states | Open journal with no active sessions, no completed sessions, or no historical entries | Clear empty-state messages appear; no misleading “Tarifsiz pişirme” heading is shown | NOT RUN |
| CJ-033 | Lightweight journal list | Inspect `GET /cooking-journal` response with entries containing large recipe snapshots | Response contains only summary fields; recipeSnapshot and notes are absent | NOT RUN |
| CJ-034 | Fetch selected journal detail | Select a historical attempt in the mobile picker | Exactly the selected entry is fetched via `GET /cooking-journal/{entryId}`; full snapshot and notes appear only after selection | NOT RUN |
| CJ-035 | Cache selected detail | Select an entry, close/reopen picker without changing selection | Detail is not fetched again while cached in screen state | NOT RUN |
| CJ-036 | Lightweight variation list | Inspect `GET /cooking-journal/variations/summaries` | Response contains only ID, title, servings, and createdAt; ingredients/stages are absent | NOT RUN |
| CJ-037 | Session picker payload | Inspect `GET /cooking-sessions` | Response contains session metadata and recipe title only; recipe ingredients/stages are not loaded | NOT RUN |
