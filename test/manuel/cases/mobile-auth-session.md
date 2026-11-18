# KAI-011 — Mobile auth session bootstrap manual test cases

**Branch:** `fix/KAI-011-auth-me-request-without-session`  
**GitHub Issue:** [#25](https://github.com/mtlgkbb20/mutfakci/issues/25)  
**Notion:** [KAI-011](https://app.notion.com/p/419d941988aa81849959cd59464e5b58)

All cases start as `NOT RUN`. Record device, app build, backend environment, and network evidence before changing a status.

| Case | Scenario | Steps | Expected result | Status |
|---|---|---|---|---|
| AUTH-001 | Fresh install / no token | Clear app storage; launch app; inspect network requests | App routes to `/auth`; no `GET /auth/me`, recipes, or other protected request is sent | NOT RUN |
| AUTH-002 | Direct protected route without token | Clear app storage; open `/(tabs)/dashboard` directly (deep link/navigation) | Tab layout waits for storage check, then redirects to `/auth`; protected screen does not mount or fetch data | NOT RUN |
| AUTH-003 | Successful login | From `/auth`, sign in with valid credentials | Login/register request succeeds; token and user are persisted; dashboard opens and loads recipes/progress without an extra `/auth/me` request | NOT RUN |
| AUTH-004 | Persisted valid session | Sign in; fully close and relaunch app | Token is found; protected tabs render; stored user is displayed; protected data loads | NOT RUN |
| AUTH-005 | Revoked/expired token | Store an invalid token and user; open dashboard/profile | Protected API response 401/403 clears token and user; app redirects to `/auth`; relaunch remains logged out | NOT RUN |
| AUTH-006 | Logout | Sign in; open profile; tap logout; inspect storage/network | Token and user are removed; app routes to `/auth`; protected tabs cannot be opened and no protected request follows | NOT RUN |
| AUTH-007 | Storage read failure | Simulate AsyncStorage read rejection on protected tab startup | App fails closed to `/auth`; protected tab content does not render | NOT RUN |
| AUTH-008 | Public login endpoint | With no token, submit valid login credentials | `POST /auth/login` is sent without Authorization; successful response persists session and navigates to dashboard | NOT RUN |

## Implementation notes

- The Expo Router tab layout is the guard for all protected tab routes, including direct navigation.
- Dashboard/profile no longer issue `/auth/me`; they use the user object persisted by the successful login flow.
- A 401/403 from protected dashboard/profile data requests clears the persisted session before redirecting to login.
- The mobile package currently has no configured unit-test runner. These cases are the manual regression suite; run the Expo TypeScript check before release:
  - `cd mobile && npx tsc --noEmit`
  - `cd mobile && npx expo start -c`
