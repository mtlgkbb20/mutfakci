# Fix bundle — dashboard session, XP progress, API timeout manual test cases

**Branch:** `fix`  
**Notion:** [KAI-014](https://app.notion.com/p/41bd941988aa8190b8b8c70bb1248812) · [KAI-019](https://app.notion.com/p/41bd941988aa816d90c1e309266c8a36) · [KAI-027](https://app.notion.com/p/41bd941988aa81069bfbe4e35704ad19)

All cases start as `NOT RUN`. Record device, app build, backend environment and network evidence before changing a status.

| Case | Scenario | Preconditions / data | Steps | Expected result | Status |
|---|---|---|---|---|---|
| FIX-001 | Dashboard survives a 5xx | Logged-in user; ability to make `/api/v1/recipes` return 500 (stop DB or proxy) | 1. Open app → dashboard. 2. Observe screen. 3. Restore backend. 4. Tap **Tekrar dene**. | Step 2: user stays on dashboard, "Bir sorun oluştu" card with retry shown, token still in storage. Step 4: card disappears, recipes and XP load. | NOT RUN |
| FIX-002 | Dashboard survives network loss | Logged-in user | 1. Enable airplane mode. 2. Relaunch app. 3. Disable airplane mode, tap **Tekrar dene**. | No redirect to `/auth`; retry card shown; data loads after retry. | NOT RUN |
| FIX-003 | Dashboard timeout | Logged-in user; backend delays `/gamification/progress` > 15 s | 1. Open dashboard. 2. Wait 15 s. | Request aborts after ~15 s; retry card shown; session kept. | NOT RUN |
| FIX-004 | Auth failure still logs out | Logged-in user with revoked/invalid token | 1. Open dashboard. | 401/403 clears token + user; app routes to `/auth` (KAI-011 behaviour preserved). | NOT RUN |
| FIX-005 | Profile survives a 5xx | Same as FIX-001 | 1. Open profile tab while `/gamification/progress` fails with 500. 2. Restore, tap **Tekrar dene**. | Stays on profile with retry card; data loads after retry. | NOT RUN |
| FIX-006 | XP bar at level boundaries | Users with 0, 49, 50, 149, 150, 300 lifetime XP | 1. Open dashboard and profile for each user. | Bar % and "X XP sonra yeni seviye" match API `progressRatio` / `xpToNextLevel` (e.g. 60 XP → Ev Aşçısı, 10 %, 90 XP). Dashboard and profile show the same title. | NOT RUN |
| FIX-007 | XP title consistency | User with 60 XP | 1. Call `GET /api/v1/gamification/progress`. | `level`=HOME_COOK, `levelNumber`=2, `nextLevelMinXp`=150, `xpToNextLevel`=90 — no field contradicts another. | NOT RUN |

## Notes

- Tier titles are now derived from the numeric level (0 / 50 / 150 / 300 XP) instead of the former 30 / 60 / 100 tiers, so some users will see a lower title than before. This is intentional: the old title disagreed with the level number and progress fields in the same response.
- Automated coverage: `mobile/src/__tests__/{api,session,progress}.test.ts`, `LevelProgressTest`, `GamificationControllerTest`.
