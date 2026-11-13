# KAI-010 — Cooking Journal Manual Test Cases

**Branch:** `feature/KAI-010-cooking-journal`  
**GitHub Issue:** [#19](https://github.com/mtlgkbb20/mutfakci/issues/19)  
**Pull Request:** [#20](https://github.com/mtlgkbb20/mutfakci/pull/20)  
**Notion:** [Cooking Journal](https://app.notion.com/p/413d941988aa81fc936ac233fcb3b92f)

All cases are initially `NOT RUN`. Record evidence before changing a status.

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
