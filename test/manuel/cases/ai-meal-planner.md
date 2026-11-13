# AI Meal Planner — Manual Test Cases

**Branch:** `feature/ai-meal-planner`  
**API prefix:** `/api/v1/meal-plans`  
**Status:** All cases are `NOT RUN` until executed against a running backend and mobile app.

| ID | Scenario | Steps | Expected result | Status |
|---|---|---|---|---|
| MP-001 | Create weekly plan | Choose a Monday, 2 servings, lunch and dinner; create plan | Seven dates and 14 entries are saved | NOT RUN |
| MP-002 | Reject non-Monday start | Submit a Tuesday as `weekStart` | HTTP 400; no plan is saved | NOT RUN |
| MP-003 | Invalid servings | Submit 0 or 31 servings | Request validation fails | NOT RUN |
| MP-004 | Empty meal slots | Submit an empty slot list | HTTP 400; no plan is saved | NOT RUN |
| MP-005 | Invalid meal slot | Submit `SNACK` | Request validation fails | NOT RUN |
| MP-006 | AI selection | Configure a valid Runware key and create a plan | Model selects only supplied catalog IDs; each date/slot has one entry | NOT RUN |
| MP-007 | AI unavailable fallback | Remove Runware key and create a plan | Plan is created with deterministic catalog selection and fallback warning | NOT RUN |
| MP-008 | Invalid AI recipe ID | Simulate model output containing an unknown recipe ID | Entire AI selection is rejected; deterministic fallback is used | NOT RUN |
| MP-009 | Preparation time | Set maximum time below every catalog recipe time | HTTP 422; no plan is saved | NOT RUN |
| MP-010 | Dietary restriction vs meal slot | Request a vegan breakfast plan | HTTP 422 when no breakfast-category recipe passes the supported restriction filter | NOT RUN |
| MP-011 | Duplicate weekly plan | Create a second plan for the same user and week | HTTP 409 | NOT RUN |
| MP-012 | Plan ownership | Read another user's plan ID | HTTP 404 | NOT RUN |
| MP-013 | Single entry replacement | Replace one meal in a saved plan | Only the selected entry changes; all other entry IDs and recipe IDs remain unchanged | NOT RUN |
| MP-014 | Invalid replacement recipe | Patch an entry with an unknown catalog ID | HTTP 400; existing entry remains unchanged | NOT RUN |
| MP-015 | Replacement time limit | Replace with a recipe over the plan's time limit | HTTP 422; existing entry remains unchanged | NOT RUN |
| MP-016 | Alternative recipe ownership | Request alternatives using another user's plan ID | HTTP 404 | NOT RUN |
| MP-017 | Ingredient aggregation | Generate a plan with repeated recipes and open its shopping list | Same ingredient and compatible units are consolidated | NOT RUN |
| MP-018 | Serving scaling | Plan a recipe for twice its default servings | Required ingredient quantities are doubled | NOT RUN |
| MP-019 | Pantry coverage | Add a matching pantry item with partial quantity | Pantry-covered and to-buy quantities are both shown correctly | NOT RUN |
| MP-020 | No automatic deduction | Generate a shopping list | Pantry quantity and movement history remain unchanged | NOT RUN |
| MP-021 | Incompatible units | Use same ingredient with incompatible custom units | Lines remain separate; a warning explains skipped automatic subtraction | NOT RUN |
| MP-022 | Budget transparency | Create a plan with a weekly budget | Response warns that budget cannot be verified because catalog cost data is unavailable | NOT RUN |
| MP-023 | Nutrition transparency | Inspect plan output | No unverified calorie or macro values are displayed | NOT RUN |
| MP-024 | Mobile create flow | Open Dashboard → Weekly Plan; fill form and submit | Saved plan is displayed grouped by day and meal slot | NOT RUN |
| MP-025 | Mobile replace flow | Open a meal's alternatives and choose another recipe | Only that meal updates in the displayed plan | NOT RUN |
| MP-026 | Mobile shopping list | Generate the shopping list from the mobile screen | Required, pantry-covered, and to-buy quantities are visible | NOT RUN |

## Execution notes

- Use two authenticated test accounts for ownership checks.
- Test AI behavior with a stub/mock response before using a billable live provider call.
- The current curated catalog is small; repeated meals are expected until the catalog expands.
- Do not mark a case as passed based only on source inspection. Record actual HTTP/UI results.

| MP-027 | Regenerate saved plan | Regenerate an existing plan for the same week | Existing entries are replaced atomically and new entries are returned; no duplicate slots remain | NOT RUN |
| MP-028 | Unsupported dietary restriction | Submit an unsupported allergy/restriction label | HTTP 400; no plan is saved; UI does not imply safety | NOT RUN |

| MP-029 | Pantry-aware recipe ranking | Add enough stock for one recipe's ingredients and create a plan | Recipes fully covered by unexpired pantry stock are prioritized | NOT RUN |
| MP-030 | Expired pantry stock | Add an expired matching pantry item and generate shopping list | Expired quantity is not counted as pantry coverage | NOT RUN |

| MP-031 | Breakfast category validation | Create a breakfast plan | Only BREAKFAST/CAKE category recipes are selected | NOT RUN |
| MP-032 | Lunch/dinner category validation | Create lunch and dinner plan; attempt to replace with breakfast recipe | Only MAIN/SOUP/SALAD recipes are offered; incompatible replacement returns HTTP 422 | NOT RUN |

| MP-033 | AI repetition control | Stub AI output to select the same recipe for every day while alternatives exist | AI selection is rejected and deterministic balanced selection is used | NOT RUN |
