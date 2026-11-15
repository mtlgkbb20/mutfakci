# KAI-011 — Zero Waste Cooking Manual Test Cases

**Branch:** feature/KAI-011-zero-waste-cooking  
**GitHub Issue:** [#21](https://github.com/mtlgkbb20/mutfakci/issues/21)  
**Notion:** [Zero Waste Cooking](https://app.notion.com/p/413d941988aa816e8840e7c9b190bbc9)

All cases start as NOT RUN. Record actual request/UI evidence before changing status.

| Case | Scenario | Preconditions / Steps | Expected result | Status |
|---|---|---|---|---|
| ZW-001 | Preview yolk-only recipe | Use a recipe whose structured metadata declares 4 yolks and a 1:1 egg-white by-product rule; request 4 servings | Preview shows 4 yolks used and 4 potential whites, marked as rule-derived/pending confirmation | NOT RUN |
| ZW-002 | Scale by servings | Preview the same recipe at half and double default servings | Ingredient and by-product quantities scale deterministically with documented rounding | NOT RUN |
| ZW-003 | No implicit stock creation | Open a preview and leave without confirming | Pantry and remainder tables remain unchanged | NOT RUN |
| ZW-004 | Confirm actual remainder | Confirm 3 egg whites after preview estimated 4 | Persisted remainder is 3, user-confirmed; it does not retain the unconfirmed estimate as stock | NOT RUN |
| ZW-005 | Correct preview quantity | Edit the proposed quantity before confirming | Only the user-confirmed quantity is persisted | NOT RUN |
| ZW-006 | Unknown conversion | Preview a by-product with no explicit conversion rule | Quantity is UNKNOWN; no exact numeric stock claim is made | NOT RUN |
| ZW-007 | Reject invalid quantity | Submit zero, negative, excessive precision, or unsupported unit | HTTP 400; no remainder is created | NOT RUN |
| ZW-008 | Complementary recipe match | Confirm an egg-white remainder and request suggestions | Only catalog recipes using egg whites with compatible scaled quantities are returned | NOT RUN |
| ZW-009 | Reject insufficient remainder | Request a recipe requiring more egg whites than available | Candidate is excluded or clearly marked as requiring additional quantity; no false full-coverage claim | NOT RUN |
| ZW-010 | Suggestion ranking | Prepare pantry coverage and candidates with different remainder yields | Deterministic ranking prefers greater remainder use, pantry coverage, lower shopping need, and lower new waste | NOT RUN |
| ZW-011 | No AI fabrication | Simulate invalid/unknown AI recipe IDs or quantities | Invalid AI output is discarded; backend candidate and quantity validation remains authoritative | NOT RUN |
| ZW-012 | Transfer to existing pantry item | Transfer a confirmed remainder matching an owned pantry item | Pantry quantity increases once; a movement is recorded; remainder becomes TRANSFERRED | NOT RUN |
| ZW-013 | Transfer creates pantry item | Transfer a remainder with no matching active pantry item | One pantry item and one movement are created; remainder becomes TRANSFERRED | NOT RUN |
| ZW-014 | Transfer idempotency | Repeat the same transfer operation key | Second request returns already-applied result; pantry quantity does not increase again | NOT RUN |
| ZW-015 | Transfer rollback | Force pantry movement persistence to fail during transfer | Transaction rolls back; remainder remains AVAILABLE and pantry stock is unchanged | NOT RUN |
| ZW-016 | Partial consumption | Consume part of a confirmed remainder | Remaining quantity is reduced exactly; status remains AVAILABLE while quantity is positive | NOT RUN |
| ZW-017 | Full consumption | Consume the entire remaining quantity | Quantity reaches zero and status becomes CONSUMED | NOT RUN |
| ZW-018 | Concurrent consumption | Submit two concurrent consume requests against the same version/quantity | No negative quantity or duplicate consumption occurs; one request conflicts or is idempotently recognized | NOT RUN |
| ZW-019 | Dismiss remainder | Explicitly dismiss/discard an available remainder | State changes to DISCARDED and audit history is retained | NOT RUN |
| ZW-020 | Prevent cross-user access | Use another account to read, edit, consume, transfer, or dismiss the remainder | HTTP 404; no data or stock is changed | NOT RUN |
| ZW-021 | Invalid state transition | Try to consume or transfer an already transferred/discarded remainder | HTTP 409; pantry and remainder remain unchanged | NOT RUN |
| ZW-022 | Expiry date is user supplied | Create/edit a remainder with and without a use-by date | No default shelf-life is invented; supplied date is preserved | NOT RUN |
| ZW-023 | Completed cooking session link | Confirm leftovers from an owned completed cooking session | Remainder links to the correct session/journal record; another user's session is rejected | NOT RUN |
| ZW-024 | Mobile end-to-end flow | Preview a recipe, confirm/edit a remainder, open suggestions, transfer or consume | Loading, empty, success, and error states are clear; no stock changes before confirmation | NOT RUN |
| ZW-025 | Regression: Pantry | List, edit, consume, and inspect movement history for normal pantry items | Existing Smart Pantry behavior and API contracts remain unchanged | NOT RUN |
| ZW-026 | Regression: Meal Planner | Generate plan and shopping list after a remainder is created | Existing planner remains read-only; remainder is not silently double-counted as pantry stock | NOT RUN |

## Execution notes

- Use at least two authenticated accounts for ownership checks.
- Stub AI selection/ranking in automated tests; do not make tests depend on billable live calls.
- Use exact unit fixtures; do not assert conversions the backend does not explicitly support.
- Food-safety and shelf-life assertions require an approved source and are not inferred from model output.
