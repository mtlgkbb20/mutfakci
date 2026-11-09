# Gamification Specification

**Status:** Product and technical specification  
**Target branch:** `feature/gamification`  
**Scope:** XP, levels, weekly leaderboard and rewards, badges, profile recognition, and community cooking events  
**Primary timezone:** `Europe/Istanbul`

---

## 1. Product goals

Gamification should make Mutfakçı feel like an active cooking community rather than only a recipe-generation tool. It should reward meaningful cooking activity, encourage users to return each week, and create periodic community moments.

The system consists of four connected features:

1. **Experience (XP) and levels** — users earn XP through eligible actions. XP is recorded permanently in an auditable ledger.
2. **Weekly leaderboard and reward** — users compete on XP earned during the current week. The weekly winner receives one free AI recipe generation.
3. **Badges** — users earn permanent achievements. Weekly badges are awarded from defined criteria; all historical badges remain visible on the profile.
4. **Community cooking events** — admins create time-bounded themed events. Everyone can participate by submitting cooking photos. The five highest-ranked eligible users become voters and score submissions from 1 to 10.

All scoring, eligibility, awards, and reward grants must be decided by the backend. The frontend must never be authoritative for XP, rank, badges, voting eligibility, or rewards.

## 2. Terminology and time rules

- **Lifetime XP:** Sum of all valid XP ledger entries for a user. It never resets.
- **Weekly XP:** Sum of eligible XP entries inside one leaderboard week. It resets by period, not by deleting history.
- **Level:** A user-facing progression value derived from lifetime XP.
- **Weekly leaderboard:** Ranking based on weekly XP, not lifetime XP.
- **Weekly badge:** A badge issued for a specific week's achievement. It remains owned permanently.
- **Event:** An admin-created cooking challenge with participation, submission, voting, and result phases.
- **Voter:** A user selected from the top five of a frozen leaderboard snapshot for a specific event.

All weekly boundaries and event timestamps use `Europe/Istanbul`. A week starts Monday at 00:00:00 and ends immediately before the next Monday at 00:00:00. Persist timestamps as UTC instants and calculate period boundaries using the configured business timezone. Use half-open intervals: `[periodStart, periodEnd)`.

## 3. XP and levels

### 3.1 XP ledger

Every XP change must be represented by an immutable ledger entry containing at least:

- ID and user ID
- XP delta (positive for earning; negative adjustments only for audited reversals)
- source type (recipe activity, weekly placement, event participation, event result, admin adjustment, etc.)
- source reference (recipe ID, event ID, submission ID, or other domain reference)
- idempotency key
- description/reason code
- creation timestamp
- actor/admin ID when manually granted or reversed

Enforce a unique constraint on the idempotency key (or on a suitable source/user/reason tuple). Retried requests, duplicate webhooks, repeated jobs, and concurrent requests must not grant XP twice.

Do not update a single mutable XP total as the only source of truth. A cached aggregate may be maintained for fast reads, but it must be rebuildable from the ledger and updated transactionally with the ledger entry.

### 3.2 XP earning rules

XP values must be centrally configured and must not be scattered as magic numbers across controllers or frontend code.

Initial proposed earning actions (values are product defaults and can be tuned before release):

| Action | Proposed XP | Rule |
|---|---:|---|
| Complete an eligible cooking session | 10 | Once per completed session; reopening does not count |
| Submit a valid community event entry | 15 | Once per event submission; only after validation |
| Receive an event score | 0–30 | Derived from the final normalized event score; see event rules |
| Weekly leaderboard winner | 50 | Awarded once after weekly settlement |
| Weekly leaderboard second place | 30 | Awarded once after weekly settlement |
| Weekly leaderboard third place | 20 | Awarded once after weekly settlement |

Important constraints:

- Merely opening the app, viewing a recipe, refreshing a page, or reopening an existing recipe grants no XP.
- AI recipe generation itself should not automatically grant XP. A user should be rewarded for meaningful cooking activity, not for repeatedly generating recipes.
- Failed or abandoned cooking sessions grant no completion XP.
- XP is granted only after the corresponding domain action has succeeded.
- A user cannot earn XP by submitting the same action repeatedly.
- Admin adjustments must include a reason and be auditable.
- XP rules should be configurable, but changing a rule must not retroactively rewrite historical ledger entries.

The exact XP schedule is a launch configuration decision. Keep it configurable so it can be balanced without code changes.

### 3.3 Levels

Level is derived from lifetime XP and is independent of weekly rank. The initial level curve should be configurable and monotonic.

Suggested first version:

- Level 1 starts at 0 XP.
- Each subsequent level requires progressively more lifetime XP.
- The backend returns current level, lifetime XP, XP required for the next level, and progress within the current level.
- A level-up is derived from ledger totals; if notifications are introduced, level-up notification delivery must be idempotent.

Do not allow a client to submit or modify its own XP or level.

## 4. Weekly leaderboard

### 4.1 Ranking

- The default leaderboard displays the current week's ranking by **weekly XP**.
- Only active, non-banned accounts are eligible.
- The current user's own rank and weekly XP should be available even if they are outside the visible top list.
- Display rank, username, avatar, level, weekly XP, and relevant badge indicator.
- The UI should clearly show the week range and time remaining until settlement.
- Provide pagination or a bounded top list; do not load every user into the client.
- A separate lifetime leaderboard may be added later, but it is not part of the initial requirement.

### 4.2 Deterministic tie-breaking

Use a stable tie-break sequence:

1. Higher weekly XP.
2. Earlier timestamp at which the user reached that final weekly XP total.
3. Earlier account creation timestamp.
4. Stable user ID as the final deterministic fallback.

The same ordering must be used by the leaderboard API, weekly settlement, reward assignment, and event voter selection. Never calculate rank differently in different services.

### 4.3 Weekly settlement and reward

At the end of each week, a scheduled backend job must:

1. Lock or otherwise safely claim the week settlement.
2. Calculate the final ranking for the closed week.
3. Persist an immutable leaderboard snapshot and placements.
4. Grant configured placement XP through idempotent ledger entries.
5. Grant the first-place user one free AI recipe-generation credit.
6. Issue any applicable weekly badges.
7. Mark settlement complete and record timestamps/errors.

The reward is **one additional AI recipe generation**, not a subscription extension and not cash. It should be integrated with the existing membership/quota system as a separate promotional credit source. It must not silently alter the user's Free/Cooker tier or lifetime quota rules.

Reward rules:

- One reward per weekly winner per week.
- Reward grants are idempotent and auditable.
- The reward should have a configurable claim/expiry policy. Proposed default: claim within 30 days of issue.
- A reward is consumed only when a successful AI recipe generation commits; failed generation must release the reservation, following the existing quota reservation/commit/release pattern.
- The UI must show whether the reward is available, used, or expired.
- If settlement fails partway through, it must be safely retryable without duplicate XP, badges, or reward credits.

If the first-place reward cannot be granted because the membership integration is unavailable, settlement must remain visibly incomplete or record a retryable reward-grant failure. Do not silently mark the reward as delivered.

## 5. Badges and achievements

### 5.1 Badge catalog

Badges are defined in a backend-managed catalog. Each definition should include:

- Stable code/key
- Name and short description
- Icon/image reference
- Category (weekly, cooking, event, community, milestone)
- Award criteria or criterion identifier
- Active/retired status
- Optional display priority

Badge criteria must be deterministic and testable. Avoid free-form AI decisions for badge awards.

Example badge concepts (names and thresholds are initial suggestions):

| Badge | Category | Example criterion |
|---|---|---|
| First Dish | Milestone | Complete the first eligible cooking session |
| Weekly Regular | Weekly | Complete at least 3 eligible cooking sessions in one week |
| Kitchen Explorer | Cooking | Complete recipes from 5 distinct categories |
| Community Chef | Event | Submit a valid entry to 3 different events |
| Podium Finish | Weekly | Finish in the top 3 of a weekly leaderboard |
| Tiramisu Creator | Event | Participate in the Tiramisu event |
| People's Choice | Event | Win an event based on the final community score |

These examples are not a commitment to ship every badge in the first release. The catalog and thresholds must be configurable.

### 5.2 Award lifecycle and history

- Badge ownership is permanent once awarded.
- A badge award record must include user, badge, awarded timestamp, source period/event, and relevant evidence/reference.
- Award jobs must be idempotent.
- A user may earn the same badge again only if the badge definition explicitly allows repeatable awards (for example, one weekly badge per distinct week). For repeatable badges, each award must have a distinct period/source reference.
- Retiring a badge prevents future awards but must not delete historical user awards.
- Admins must not be able to silently erase historical awards. Corrections require an auditable revocation record and reason.

### 5.3 Profile and compact badge indicator

The profile page must show the user's complete badge history, including badges earned in previous months and years. Group or filter badges by category and date.

The general application UI should show badges earned within the **last 30 days** in its recent-badges area. This is a rolling 30-day window, not the current calendar month.

Additionally:

- Display a small badge/medal icon immediately next to the username wherever usernames are shown in supported UI surfaces (leaderboard, comments/community surfaces, event submissions, and other agreed user identity components).
- On hover (desktop), show a compact tooltip/popover with the badge name, description, and earned date. If multiple recent badges exist, show a small preview and allow opening the full badge list.
- On touch devices, the same information must be available through tap; hover cannot be the only interaction.
- Do not overcrowd the username. Show one representative recent badge icon or a compact badge count, with the complete list in the tooltip/popover/profile.
- Badge icons need accessible labels and must not be the sole carrier of important information.

The profile history is permanent; the general UI's 30-day preview is intentionally limited.

## 6. Community cooking events

### 6.1 Event lifecycle

Admins can create and manage events from the admin interface. An event should support:

- Title and description
- Theme/challenge (for example, Tiramisu Week)
- Cover image
- Start and end timestamps
- Participation/submission opening and closing timestamps
- Voting opening and closing timestamps
- Timezone (default `Europe/Istanbul`)
- Recipe/challenge instructions and required submission details
- Maximum number of photos per submission (configurable)
- Allowed image formats and size limits
- Event status
- XP and badge policy
- Moderation policy and result publication settings

Suggested lifecycle:

`DRAFT → SCHEDULED → SUBMISSION_OPEN → VOTING → COMPLETED`

Additional terminal/exception states: `CANCELLED`. Admins may pause an event if moderation or operational issues require it. Every transition must be validated server-side and audited.

The event may be scheduled for a special day (holiday, seasonal occasion, product campaign, or a date chosen by the admin). The system must not assume that events happen every week.

### 6.2 Participation and submissions

- Every eligible registered user may join an open event.
- Participation is not restricted by leaderboard position.
- A participant submits one entry per event by default. If revisions are allowed, define a configurable edit deadline and retain submission history.
- Each submission can contain multiple photos of the prepared dish.
- Store images using the application's existing secure object/file storage approach; store metadata and ownership in PostgreSQL.
- Validate MIME type, file size, dimensions, count, and ownership on the backend. Do not trust file extensions or client-side checks.
- Submission must be tied to the authenticated user and event.
- Users may edit/delete their submission only before the submission deadline, unless an admin explicitly intervenes.
- Do not expose private storage keys or unrestricted upload paths.
- The submission UI should show the event rules, deadline, upload progress, and confirmation before final submission.
- Participants must be able to see their own submission status and the event's published entries according to the event's visibility policy.

A submission is not eligible for voting until it passes basic validation and any required moderation. Rejected entries must not receive participation XP.

### 6.3 Voter selection

The five highest-ranked users on the **weekly leaderboard snapshot taken at the event's voting-open time** become that event's voters.

Rules:

- Freeze the voter list at voting-open time; later leaderboard movement must not change eligibility.
- If fewer than five eligible users exist, all eligible users may be selected.
- The event creator/admin cannot manipulate the snapshot manually without an audited override.
- Voters must not vote for their own submission.
- A voter must not vote more than once for the same submission.
- A voter may score every eligible submission, subject to the event's voting rules.
- If a selected voter is also a participant, they may vote for other users but never for their own entry.
- If a selected voter becomes banned or is disqualified before voting closes, an audited replacement may be selected from the frozen ranking order.
- The UI must clearly identify the selected voters and explain why the user can or cannot vote.
- Voter identity and scores should be handled according to the event's configured privacy policy. Default recommendation: hide individual scores during voting and reveal aggregate results after voting closes.

Voter selection must use the same deterministic weekly ranking/tie-break logic as the leaderboard.

### 6.4 Scoring and event results

- Each eligible submission receives a score from each eligible voter on a 1–10 integer scale.
- Validate score range and voter eligibility on the backend.
- Store each vote as an immutable record; edits are allowed only if the event explicitly enables vote changes before the deadline. Keep an audit trail for changes.
- A submission's final score is the arithmetic mean of its valid voter scores, rounded only for display. Preserve the unrounded value for ranking.
- A submission with no valid votes must be marked unscored; do not treat it as zero or as a winner.
- Event ranking is by average score descending, then by number of valid votes descending, then by submission timestamp ascending, then stable submission ID.
- The event result must be frozen after voting closes and persisted for auditability.
- If a tie remains after the deterministic tie-breakers, the event may declare a shared placement; the admin must not silently pick a winner.

### 6.5 Event XP and badges

Proposed initial XP policy:

| Event outcome | Proposed XP |
|---|---:|
| Valid event submission | 15 |
| Event winner | 30 |
| Second place | 20 |
| Third place | 10 |
| Other participants | No additional placement XP |

The submission XP and placement XP are separate ledger entries with different idempotency keys. A user cannot receive placement XP twice for the same event.

Avoid rewarding voters based on how many votes they cast or how high they score entries; this can incentivize collusion. If voter participation XP is introduced later, award a small fixed amount for completing all required votes, independent of the scores selected.

Event-specific badges may be awarded for participation, podium placement, or special distinctions. Badge criteria must be configured before the event opens and must not be changed retroactively after submissions begin, except through an audited admin correction.

### 6.6 Tiramisu event example

1. Admin creates a Tiramisu event and configures its title, rules, dates, photo limit, XP, and badge criteria.
2. Users see the event page and join while submissions are open.
3. Each user uploads photos showing their tiramisu. The backend validates and stores the entry.
4. Valid submissions become visible according to the event policy.
5. At voting-open time, the backend freezes the current weekly leaderboard and selects its top five eligible users as voters.
6. Selected voters score each eligible entry from 1 to 10. They cannot score their own entry.
7. At voting close, the backend calculates and freezes the final ranking.
8. XP and event badges are awarded idempotently; the event results are published.
9. The event remains visible as historical content, including entries, final scores, winners, and participation records, subject to retention/moderation policy.

## 7. Admin experience

Admin UI must support:

### Gamification configuration
- Enable/disable gamification features globally or by feature.
- Configure XP values and level curve.
- Configure weekly period timezone and settlement schedule (business timezone remains Europe/Istanbul unless deliberately changed).
- Configure weekly placement rewards and reward expiry.
- Manage badge catalog, criteria, icon, category, active status, and repeatability.
- Inspect XP ledger entries and badge awards.
- Perform audited XP adjustments and badge corrections.

### Event management
- Create, edit, schedule, publish, pause, cancel, and complete events.
- Configure event dates, submission rules, photo limits, voting rules, XP, and badges.
- Preview the public event page before publishing.
- View participants, submissions, moderation status, selected voters, vote completion, and results.
- Moderate entries (approve/reject/hide) with a mandatory reason.
- View audit history for event configuration and admin actions.
- Trigger safe retries for failed settlement/result jobs; retries must be idempotent.

Admin-only operations must be protected by backend authorization. Hiding admin controls in the frontend is not sufficient.

## 8. User experience and surfaces

### Main navigation / home
- Add a clear entry point to the leaderboard and active events.
- Show current weekly rank, weekly XP, lifetime XP/level, and recent badge preview where appropriate.
- Show the current weekly reward and how it is earned.
- Highlight active/upcoming events with deadlines.

### Leaderboard page
- Current week and period countdown.
- Top ranked users with rank, avatar, username, level, weekly XP, and small badge indicator.
- Current user's rank even when outside the top list.
- Clear explanation of tie-breaks and weekly reward.
- Loading, empty, error, and settled-week states.

### Profile
- Lifetime XP and level progress.
- Complete historical badge collection, including old badges.
- Badge categories and earned dates.
- Event participation and achievements.
- Weekly placements/history where useful.

### Username badge affordance
- Small icon immediately adjacent to username.
- Desktop hover tooltip and mobile tap interaction.
- Recent badge details limited to last 30 days in general surfaces.
- Full badge collection reachable from profile.

### Event page
- Event description, dates, rules, countdown, and participation CTA.
- Multi-photo upload with validation feedback.
- Submission preview and edit window.
- Voting UI only for selected voters during voting phase.
- Result view after completion.

All web and mobile clients must use the same backend definitions and scoring rules. Visual presentation may differ, but behavior and eligibility must remain consistent.

## 9. Backend architecture and data model

Implement gamification as a separate domain/module, integrated with existing user, recipe/cooking-session, membership, and admin domains.

Suggested entities (names are illustrative; align with existing naming conventions):

| Entity | Responsibility |
|---|---|
| `user_gamification` | Cached lifetime XP, level, and aggregate fields; rebuildable from ledger |
| `xp_ledger_entry` | Immutable XP source of truth and idempotency |
| `weekly_leaderboard_snapshot` | Frozen weekly ranking and settlement status |
| `weekly_leaderboard_entry` | User rank, weekly XP, tie-break timestamp, placement |
| `gamification_reward_grant` | Reward grant, status, expiry, redemption, source week |
| `badge_definition` | Badge catalog and criteria metadata |
| `user_badge_award` | Permanent badge award history |
| `cooking_event` | Event definition, schedule, status, rules/config |
| `event_participation` | User registration/participation state |
| `event_submission` | User entry and moderation state |
| `event_submission_image` | Image metadata and storage reference |
| `event_voter_snapshot` | Frozen top-five voter list and source leaderboard |
| `event_vote` | Voter-to-submission score and audit metadata |
| `event_result` | Frozen final score and placement |
| `gamification_audit_log` | Admin and lifecycle actions |

Use foreign keys, unique constraints, indexes, and explicit state transitions. Add Flyway migrations; do not edit already-applied migrations.

Suggested important constraints/indexes:

- Unique XP idempotency key.
- Unique weekly snapshot per week.
- Unique user per weekly leaderboard snapshot.
- Unique reward grant per user/reward/source week.
- Unique badge award per user/badge/source period (for repeatable weekly awards).
- Unique event participation per event/user.
- Unique event submission per event/user for the default one-entry policy.
- Unique voter/submission pair per event.
- Check constraint for vote score between 1 and 10.
- Index leaderboard queries by period and rank/weekly XP.
- Index profile badge history by user and awarded timestamp.
- Index event submissions by event and moderation/status.

Avoid storing derived leaderboard rank as mutable truth outside a frozen snapshot. Current-week ranking can be queried from weekly XP aggregates; closed-week ranking must come from the immutable snapshot.

## 10. API surface (proposed)

All endpoints require authentication unless explicitly marked admin-only.

### User gamification
- `GET /api/v1/gamification/me` — lifetime XP, level, current weekly XP/rank, next level progress, recent badges, available rewards.
- `GET /api/v1/gamification/leaderboard/current` — paginated current-week leaderboard and current user's rank.
- `GET /api/v1/gamification/leaderboard/weeks/{weekKey}` — historical settled leaderboard.
- `GET /api/v1/users/{userId}/badges` — public/profile badge history subject to profile visibility.
- `GET /api/v1/gamification/badges/catalog` — active badge catalog.

### Events
- `GET /api/v1/events` — published/upcoming/active events.
- `GET /api/v1/events/{eventId}` — event details and user's participation/submission/voting eligibility.
- `POST /api/v1/events/{eventId}/participation` — join event.
- `POST /api/v1/events/{eventId}/submissions` — create submission and attach validated images.
- `PUT /api/v1/events/{eventId}/submissions/me` — edit own entry before deadline.
- `GET /api/v1/events/{eventId}/submissions` — visible entries with pagination.
- `GET /api/v1/events/{eventId}/voters/me` — current user's voter eligibility/status.
- `PUT /api/v1/events/{eventId}/submissions/{submissionId}/vote` — cast/update a score when permitted.
- `GET /api/v1/events/{eventId}/results` — results after publication.

### Admin
- `POST /api/v1/admin/events`, `PUT /api/v1/admin/events/{id}`, `POST /api/v1/admin/events/{id}/publish`, `POST /api/v1/admin/events/{id}/cancel`
- `GET /api/v1/admin/events/{id}/submissions`
- `POST /api/v1/admin/events/{id}/submissions/{submissionId}/moderation`
- `GET /api/v1/admin/gamification/config`, `PUT /api/v1/admin/gamification/config`
- `GET /api/v1/admin/gamification/xp-ledger`
- `POST /api/v1/admin/gamification/xp-adjustments`
- `GET /api/v1/admin/gamification/settlements`
- `POST /api/v1/admin/gamification/settlements/{weekKey}/retry`

These are design-level routes, not a requirement to implement every route in one controller. Follow existing API conventions and response envelopes.

## 11. Security, abuse prevention, and privacy

- Derive user identity from the authenticated principal; never accept arbitrary user IDs for self-service XP, submission, or voting operations.
- Validate all event state and deadlines on the server.
- Use authorization checks for admin endpoints and voter-only operations.
- Enforce rate limits for event participation, image upload, vote submission, and admin mutations.
- Validate image content, MIME type, file size, dimensions, and storage ownership. Consider malware scanning where supported.
- Prevent self-voting, duplicate votes, duplicate submissions, and duplicate XP/reward grants.
- Do not expose private image storage paths or internal moderation metadata.
- Provide reporting/moderation paths for inappropriate images or abusive submissions.
- Keep an audit trail for moderation, admin adjustments, event changes, vote edits, and settlement retries.
- Define image retention and user deletion behavior consistently with the application's privacy policy.
- Do not expose individual voter scores before voting closes by default.
- Avoid public display of sensitive account data; use display names/usernames and existing profile visibility rules.

## 12. Reliability and concurrency

- XP grant and its source domain transition must be transactional where possible.
- Use unique idempotency keys and database constraints as the final protection against duplicates.
- Weekly settlement must be protected against concurrent scheduler instances (database lock, claim row, or equivalent).
- Event voting close/result calculation must be idempotent and based on a frozen eligible voter list.
- Freeze leaderboard and voter snapshots so later XP changes cannot rewrite historical decisions.
- Use an outbox or retryable job pattern for side effects that cannot share a database transaction, especially membership reward grants and notifications.
- A failed image upload must not create a valid submission or grant XP.
- A failed reward grant must be visible and retryable.
- Use UTC for persisted instants and `Europe/Istanbul` for business-period calculations.

## 13. Notifications

Notifications are recommended for:
- Weekly leaderboard result and reward availability.
- New badge awards and level-ups.
- Event publication and submission deadline reminders.
- Voter selection and voting deadline reminders.
- Event results and placement.

Notification delivery must not determine whether XP, badge, or reward is granted. Persist the domain result first; deliver notifications asynchronously and idempotently.

## 14. Testing requirements

### XP and levels
- Eligible action grants the expected XP exactly once.
- Duplicate request/idempotency key grants no duplicate XP.
- Failed or abandoned action grants no XP.
- Lifetime XP and level are consistent with ledger entries.
- Concurrent duplicate grants cannot double-count.

### Leaderboard and weekly settlement
- Week boundaries are correct in `Europe/Istanbul`, including daylight-saving-safe timezone handling.
- Weekly XP excludes entries outside the period.
- Tie-break ordering is deterministic.
- Settlement creates one snapshot per week.
- Repeated/concurrent settlement does not duplicate XP, badges, or reward grants.
- Reward credit is usable through the existing recipe quota flow and is consumed only on successful generation.
- Historical snapshots remain unchanged after later XP changes.

### Badges
- Criteria award badges exactly once per source period.
- Weekly repeatable badges can be earned in different weeks.
- Historical badges remain visible after retirement.
- Last-30-day preview excludes older awards while profile history includes them.
- Hover and touch interactions expose the same badge details accessibly.

### Events
- Only eligible users can join and submit during the configured window.
- Invalid image files, oversized files, excess photos, and unauthorized storage references are rejected.
- Only the frozen top five voters can vote.
- Voter cannot score own submission.
- Duplicate voter/submission scores are rejected or safely updated according to policy.
- Score is constrained to integer 1–10.
- Results use the specified average and tie-break rules.
- Voting closure, result calculation, XP, and badge awards are idempotent.
- Cancelled/paused events cannot accept submissions or votes outside explicitly allowed admin workflows.
- Admin actions require authorization and are audited.

### API and UI
- Web and mobile clients render the same backend XP, ranks, badges, and eligibility.
- Empty/loading/error states are covered.
- A user outside the top list still sees their own rank.
- Username badge affordance works with mouse, keyboard, and touch.
- Admin event lifecycle and moderation flows are covered end-to-end.

## 15. Delivery phases

### Phase 1 — XP foundation and leaderboard
- XP ledger, idempotent grant service, level calculation.
- Weekly XP aggregation and current leaderboard API/UI.
- Weekly snapshot and settlement job.
- Weekly winner reward integration with membership quota credit.
- Basic tests and observability.

### Phase 2 — Badge system
- Badge catalog and award history.
- Deterministic criteria evaluator and weekly award job.
- Profile badge history.
- Last-30-day badge preview and username icon tooltip/popover.

### Phase 3 — Community events
- Admin event CRUD and lifecycle.
- Public event listing/details and participation.
- Multi-image submissions and moderation.
- Frozen top-five voter selection.
- 1–10 voting, result calculation, XP, and event badges.

### Phase 4 — Polish and operations
- Notifications and reminders.
- Historical leaderboard pages.
- Admin analytics and settlement retry tools.
- Abuse monitoring, retention controls, and operational dashboards.

Phases may be delivered incrementally, but shared primitives (XP ledger, idempotency, timezone handling, and authorization) must be designed once and reused.

## 16. Acceptance criteria

The feature is ready for release when:

1. XP is server-authoritative, ledger-backed, auditable, and idempotent.
2. Current weekly leaderboard ranks users by weekly XP with deterministic ties.
3. Weekly settlement freezes results and grants the winner exactly one free AI recipe-generation credit.
4. Badge awards are permanent and historical; profile shows all badges.
5. General UI shows only badges earned in the last 30 days and places a compact badge indicator next to usernames with hover/tap details.
6. Admins can create and manage scheduled cooking events from the admin interface.
7. Every eligible user can participate and upload multiple validated photos.
8. Exactly the frozen top five eligible weekly leaderboard users are selected as event voters (or fewer when fewer than five eligible users exist).
9. Voters can score eligible submissions from 1 to 10, cannot vote for themselves, and cannot duplicate votes.
10. Event results, XP, and badges are finalized exactly once and remain historically reproducible.
11. Web and mobile clients display consistent backend state.
12. Automated tests cover the critical business, security, concurrency, and lifecycle rules.

---

## 17. Product decisions to confirm before implementation

The following defaults are proposed so implementation can proceed without blocking, but should be confirmed before launch:

- XP values in Section 3.2 and Section 6.5.
- Whether weekly rewards go only to first place or to multiple placements. Initial default: first place gets one free AI recipe generation; second/third receive XP only.
- Reward claim expiry. Initial default: 30 days.
- Whether event participants may edit submissions after the first upload. Initial default: editable until submission deadline, with history retained.
- Whether voters may change a vote before voting closes. Initial default: yes, with an audit trail.
- Whether event scores and voter identities are public. Initial default: aggregate scores and results are public after voting closes; individual voter scores remain private.
- Exact badge catalog, icons, and thresholds.
- Whether event winner XP stacks with weekly leaderboard placement XP. Initial default: yes, because they are separate achievements with separate idempotency keys.


---

## 18. Initial implementation status

The first implementation slice is present on `feature/gamification`. It establishes the backend source of truth and the primary web/mobile user surfaces. This section records what is actually implemented; it does not imply every optional item in this specification is complete.

### Implemented

- Flyway migrations V7–V10 create the XP ledger, badge catalog/awards, weekly leaderboard snapshots, reward grants, event participation/submissions/images/voters/votes/results, and the admin flag.
- Existing XP transaction records and legacy `user_progress.total_xp` aggregates are backfilled into the ledger without double-counting.
- Completing a cooking session grants 10 XP using a session-scoped idempotency key. The first-dish badge is awarded once.
- XP ledger grants update the legacy progress aggregate for compatibility.
- Current-week leaderboard and lifetime XP/level summary APIs are available.
- Weekly settlement runs Monday at 00:05 in `Europe/Istanbul`; settlement is retry-safe and snapshots placements.
- Weekly top-three XP bonuses and podium badges are awarded. First place receives one promotional AI recipe-generation credit, expiring after 30 days.
- Promotional credits are reserved, committed only after successful AI generation, and released when generation fails. Stale reservations are recovered after one hour.
- Badge catalog and full badge-history APIs are available. The web dashboard shows recent badges and leaderboard badge indicators; the mobile profile shows the full badge history.
- Admins can create scheduled events, inspect submissions, moderate submissions, open voting, finalize results, and cancel events.
- Event submissions accept JPEG, PNG, and WebP images (maximum 8 MB each, and bounded by the event photo limit). Images are currently stored as PostgreSQL `BYTEA` data.
- Voting eligibility is frozen from the top five weekly XP users at voting-open time. Scores are integers from 1 to 10; self-voting is rejected.
- Event results are automatically finalized after voting closes. Event submission and placement XP use idempotent ledger keys.
- The web dashboard includes a basic event administration form. Mobile includes leaderboard and event participation/photo upload/voting screens.

### Admin bootstrap

After creating the first account, grant administrator access directly in the database:

```sql
UPDATE app_user
SET is_admin = TRUE
WHERE email = 'admin@example.com';
```

The account must sign in again to receive the updated `isAdmin` value in its authentication response.

### Remaining work before production release

- Run Maven tests, Flyway migration verification against PostgreSQL, frontend TypeScript/build checks, and Expo type/build checks. These checks have not been executed as part of this GitHub-only change.
- Add automated integration tests for concurrency, settlement retries, quota reservation/commit/release, event voting, and image validation.
- Add event editing/rescheduling, configurable XP and badge criteria, badge catalog administration, manual XP adjustments, full audit-log screens, and notification delivery.
- Add event submission revision/deletion policy, explicit content moderation tooling, upload retention/deletion handling, and object storage migration. PostgreSQL image storage is a functional first implementation, not the preferred long-term storage design.
- Add a richer event results view and image gallery. The current mobile flow exposes entry metadata and voting controls; the public image gallery and detailed result presentation still need polish.
- Add user-facing event deadline reminders and leaderboard settlement notifications.
- Configure the first administrator through the SQL bootstrap above; new accounts are not administrators by default.
