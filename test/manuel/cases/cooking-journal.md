# KAI-010 — Cooking Journal Manual Test Cases

**Branch:** `feature/KAI-010-cooking-journal`  
**GitHub Issue:** [#19](https://github.com/mtlgkbb20/mutfakci/issues/19)  
**Notion:** [Cooking Journal](https://app.notion.com/p/413d941988aa81fc936ac233fcb3b92f)

All cases are initially `NOT RUN`. Record evidence before changing a status.

| Case | Scenario | Preconditions / Steps | Expected result | Status |
|---|---|---|---|---|
| CJ-001 | Save completed cooking session | Sign in; complete a recipe session; POST a journal entry with session ID, servings, rating, notes | Entry is created with recipe snapshot and supplied metadata | NOT RUN |
| CJ-002 | Reject active session | Start a session but do not complete it; attempt to journal it | HTTP 409; no entry is created | NOT RUN |
| CJ-003 | Reject duplicate journal entry | Create an entry; submit the same session again | HTTP 409 / unique constraint; original entry remains unchanged | NOT RUN |
| CJ-004 | List private journal | Create entries for the current user; GET journal | Only current user's entries are returned, newest first | NOT RUN |
| CJ-005 | Read journal detail | Open an owned entry by ID | Snapshot and recorded metadata are returned | NOT RUN |
| CJ-006 | Prevent cross-user read | Sign in as another user and request the entry ID | HTTP 404; no data is disclosed | NOT RUN |
| CJ-007 | Update rating and notes | PATCH an owned entry with rating 4 and notes | Values update; recipe snapshot remains identical | NOT RUN |
| CJ-008 | Validate rating | PATCH with rating 0 or 6 | HTTP 400; no change is persisted | NOT RUN |
| CJ-009 | Validate servings | Create/update with servings 0 or 31 | HTTP 400; no invalid value is persisted | NOT RUN |
| CJ-010 | Enforce notes limit | Create/update with notes longer than 2,000 characters | HTTP 400 | NOT RUN |
| CJ-011 | Recipe attempt history | Create multiple completed sessions for one recipe and journal them | Attempts endpoint returns only the current user's entries for that recipe, newest first | NOT RUN |
| CJ-012 | Snapshot immutability | Save entry; change recipe through a future supported edit path; read journal entry | Journal snapshot remains as originally cooked | NOT RUN |
| CJ-013 | Recipe ownership | Try to create a session using another user's recipe, then journal | Session creation/journal flow cannot expose another user's recipe | NOT RUN |
| CJ-014 | Unauthenticated access | Call each endpoint without a valid token | Authentication layer rejects the request | NOT RUN |
