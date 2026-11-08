# Membership, Plans and Payments

**Status:** Product and technical specification  
**Branch:** `payment`  
**Scope:** Free, Cooker, and future Chef membership; monthly/yearly billing; usage entitlements and payment integration boundaries.

## 1. Product decisions

Mutfakçı will require an account for its membership-based experience. There are three membership tiers in the product model, but only Free and Cooker are defined for the first implementation. Chef is a planned tier and must be represented in a way that can be enabled later without inventing its price, limits, or feature set now.

### 1.1 Free

- No payment required.
- Each account receives **3 AI recipe generations over the entire lifetime of that account**.
- The three-use allowance does not reset daily, monthly, or yearly.
- Logging out, signing in again, changing devices, or reinstalling the client must not reset the allowance.
- When the allowance is exhausted, the user can still sign in and access existing account data, but must upgrade to generate additional AI recipes.
- Free is not a subscription and has no billing period.

### 1.2 Cooker

Cooker is the first paid membership.

- Available as a **monthly** or **yearly** subscription.
- Includes **up to 10 AI recipe generations per calendar day**.
- The daily allowance resets every day at 00:00 in the `Europe/Istanbul` time zone.
- The yearly plan has the same daily allowance as the monthly plan. It does **not** grant 3,650 recipes upfront or a yearly pooled allowance.
- Cooker includes the core recipe-generation and cooking-assistance capabilities available in the product. The precise feature matrix must be kept in one entitlement configuration rather than scattered across frontend conditionals.
- Monthly and yearly Cooker plans differ in billing interval and price, not in daily usage allowance or product capabilities unless a future product decision explicitly changes this.

### 1.3 Chef (planned; do not launch in this implementation)

Chef will be a third tier, but its product definition is intentionally open.

- Add the `CHEF` tier to the domain model so the system can support it later.
- Do not invent a Chef price, billing interval, recipe quota, or feature entitlement.
- Do not display a purchasable Chef plan or promise Chef features in the UI yet.
- Do not implement Chef-only functionality as part of this work.
- Keep plan entitlements data-driven so Chef can be configured later without restructuring the membership system.

Potential future Chef capabilities (ideas only, not commitments):
- Personal taste and cooking profile.
- Recipe personalization (e.g. budget, protein, sweetness, ingredient substitutions).
- Recipe suggestions based on ingredients available at home.
- Weekly meal planning.
- Consolidated shopping lists.
- More advanced photo-based cooking-stage analysis.
- Advanced model selection or higher-quality assistance.

These ideas must not be treated as approved scope or advertised benefits until separately prioritized.

## 2. Plan matrix

| Capability | Free | Cooker Monthly | Cooker Yearly | Chef |
|---|---:|---:|---:|---:|
| Account required | Yes | Yes | Yes | Yes |
| Price | 0 | TBD | TBD | TBD |
| Billing | None | Monthly | Yearly | TBD |
| AI recipe generation | 3 lifetime | 10/day | 10/day | TBD |
| Daily quota reset | N/A | 00:00 Europe/Istanbul | 00:00 Europe/Istanbul | TBD |
| Core cooking assistance | Product-defined Free access; do not infer extra AI quota | Included | Included | TBD |
| Chef-specific capabilities | No | No | No | TBD |
| Availability | Active | Planned paid plan | Planned paid plan | Future |

Do not guess or hardcode subscription prices. Currency, tax display, promotions, refunds, and payment provider are product/business decisions still to be made.

## 3. What consumes a recipe allowance?

The quota applies to **AI recipe generation**, not to every recipe view or every interaction.

Recommended initial rule:
- A successful newly generated AI recipe consumes one recipe allowance.
- A recipe returned from the curated catalog consumes no AI-generation allowance because it does not invoke AI generation.
- Viewing, reopening, saving, or cooking an existing recipe does not consume a generation.
- Asking a follow-up cooking question does not consume a recipe-generation allowance. If follow-up AI assistance needs a separate limit for cost control, model it as a separate entitlement later; do not silently deduct recipe credits.
- A failed generation, provider timeout, invalid model output, or server error must not consume an allowance.
- A retry of the same logical request must not consume a second allowance or create a duplicate recipe.

The backend must reserve/check allowance safely before expensive generation and commit consumption only when a complete, validated AI recipe has been successfully persisted. If reservation is used, it must be released on failure. The reservation and final consumption must be transactional or otherwise concurrency-safe.

A catalog match should be identified explicitly in the response (e.g. `source=CURATED`) so clients do not attempt to debit quota themselves. All quota decisions are authoritative on the backend.

## 4. Membership and subscription domain model

Adapt names and mappings to the existing entities and persistence conventions after inspecting the repository. The following is a conceptual model, not a requirement to duplicate existing user/account tables.

### 4.1 Plan tier

Conceptual enum:

```java
public enum MembershipTier {
    FREE,
    COOKER,
    CHEF
}
```

The plan tier describes the product entitlement level. It must not encode billing interval.

### 4.2 Billing interval

```java
public enum BillingInterval {
    NONE,
    MONTHLY,
    YEARLY
}
```

Free uses `NONE`; Cooker uses `MONTHLY` or `YEARLY`. Chef remains unavailable until its plan configuration is approved.

### 4.3 Subscription record

A subscription record should capture at least:
- Internal subscription ID.
- User/account ID.
- Tier and billing interval.
- Status.
- Start time.
- Current billing-period start and end.
- Cancellation-at-period-end flag and cancellation timestamp where applicable.
- Payment provider and provider-side customer/subscription identifiers (nullable until a provider is selected).
- Created/updated timestamps.

Suggested statuses (adapt to provider lifecycle):
- `PENDING`: checkout started; payment not yet confirmed.
- `ACTIVE`: paid entitlement is active.
- `PAST_DUE`: renewal payment failed; access follows the configured grace policy.
- `CANCELED`: subscription ended/canceled.
- `EXPIRED`: paid period ended and no renewal is active.
- `INCOMPLETE` or `PAYMENT_FAILED`: initial payment did not complete, if needed by the chosen provider.

Do not grant Cooker entitlements merely because the client returns from a checkout page. Activate paid access only after verifying a trusted provider event or server-side payment confirmation.

### 4.4 Usage ledger

Do not store only a mutable integer on the user record. Maintain auditable usage events or an equivalent atomic counter model.

Each usage record should identify:
- User ID.
- Entitlement/limit key, e.g. `AI_RECIPE_GENERATION`.
- Amount (normally 1).
- Event type such as `RESERVED`, `CONSUMED`, or `RELEASED`, or a simpler immutable debit/refund ledger if that fits the existing architecture.
- Logical request/idempotency key.
- Related recipe ID when generation succeeds.
- Timestamp.
- Applicable quota period key.

Quota period keys:
- Free: one lifetime bucket per account, e.g. `FREE_LIFETIME`.
- Cooker: one bucket per account and Istanbul calendar date, e.g. `COOKER_DAILY:2026-11-08`.

Use database constraints/transactions or atomic updates to prevent concurrent requests from exceeding the limit. Two simultaneous generation requests must not both pass when only one allowance remains.

Do not rely on frontend counters, local storage, device identifiers, or client-provided tier/usage values.

## 5. Entitlement configuration

Keep limits and feature flags centralized and configurable. Avoid scattering checks such as `if (user.isPremium())` throughout controllers and UI.

Conceptual configuration:

| Tier | Entitlement key | Limit | Period |
|---|---|---:|---|
| FREE | `AI_RECIPE_GENERATION` | 3 | Lifetime |
| COOKER | `AI_RECIPE_GENERATION` | 10 | Istanbul calendar day |
| CHEF | `AI_RECIPE_GENERATION` | Not defined | Not defined |

The Chef row must remain disabled/unconfigured. Do not treat an unknown limit as unlimited. Fail closed for unavailable/unconfigured paid tiers.

Expose a backend membership/entitlement summary endpoint or extend the existing profile response. It should return:
- Current tier.
- Subscription status and billing interval, if applicable.
- Whether paid access is active.
- Current entitlement usage and limit.
- Reset timestamp for a daily limit.
- Available plans for the current release (Free and Cooker monthly/yearly only).

Do not expose provider secrets, internal payment identifiers, or raw webhook payloads to clients.

## 6. Subscription lifecycle and plan changes

Implement the lifecycle in a provider-neutral domain service. Exact provider event names belong in the payment adapter.

### Purchase
1. Authenticated user selects Cooker Monthly or Cooker Yearly.
2. Backend validates that the selected plan is currently available.
3. Backend creates a checkout/payment session through the payment provider adapter.
4. Client is redirected to or opens the provider's approved checkout experience.
5. Backend verifies payment using a signed webhook and/or server-to-server confirmation.
6. Only then does the subscription become active and Cooker entitlement take effect.

### Renewal
- Renewals are handled by the payment provider.
- Verify renewal events and update the billing period idempotently.
- A duplicate webhook must not extend a period twice or duplicate entitlements.

### Cancellation
- Support cancellation at the end of the paid period unless the selected provider/product policy requires another explicit behavior.
- If canceled at period end, Cooker remains active until the paid-through timestamp.
- After the paid period ends, the account falls back to Free and retains recipes/history.
- Cancellation must not delete the account or user content.

### Failed renewal
- Define a configurable grace-period policy before launch.
- Do not silently grant indefinite paid access on failed payment.
- Keep payment failure and entitlement state changes auditable.
- The client should show a clear renewal/payment status and next action.

### Upgrade/downgrade and interval changes
- Define proration and effective-date behavior based on the selected payment provider before implementing these flows.
- Do not assume whether monthly-to-yearly or yearly-to-monthly changes take effect immediately or at renewal.
- Prevent overlapping active Cooker subscriptions for the same account unless the payment provider's migration flow explicitly requires a temporary transition state.

### Refunds and chargebacks
- Map verified refunds/chargebacks to subscription status and entitlement policy.
- Do not delete generated recipes when access changes.
- Record payment/refund events for audit and support.

## 7. Payment provider boundary and security

The payment provider has not been selected. Keep integration replaceable.

Suggested conceptual boundary:
- `PaymentProvider`: create checkout, retrieve/verify payment, cancel subscription, and verify webhook signature.
- Provider-specific adapter: SDK/API calls and provider event mapping.
- `MembershipService`: domain lifecycle and plan validation.
- `EntitlementService`: authoritative access and quota checks.
- `UsageService`: reservation, consumption, release, and history.
- Controller/webhook endpoint: transport only; no business rules.

Security requirements:
- Never store raw card numbers, CVV, or payment credentials.
- Prefer provider-hosted checkout or tokenized payment collection.
- Verify webhook signatures and reject untrusted events.
- Make webhook processing idempotent using provider event IDs.
- Do not trust client-supplied payment success, tier, price, or subscription status.
- Store secrets in environment/secret management, never in Git.
- Validate product/price identifiers against server-side configuration; never accept arbitrary client-provided amounts.
- Log state transitions and provider event IDs, but redact secrets and sensitive payment data.
- Use HTTPS in production.

Do not select or integrate a payment vendor until supported countries, business/legal setup, recurring payment support, fees, refund handling, and payout requirements have been evaluated for the intended launch market (initially Türkiye unless product direction changes).

## 8. API and frontend requirements

Inspect the existing auth/profile/recipe API before selecting routes or DTO names. Reuse current conventions.

The client needs to:
- Display the current membership and remaining recipe allowance.
- Display Free, Cooker Monthly, and Cooker Yearly options.
- Clearly explain that Free is 3 total AI recipe generations, not 3 per day/month.
- Clearly explain that Cooker is 10 AI recipe generations per Istanbul calendar day.
- Clearly explain that Cooker Yearly also resets daily and does not provide an annual pooled quota.
- Start checkout only through a backend-created checkout session.
- Show pending, active, canceled, past-due, and expired states where relevant.
- Refresh membership status from the backend after returning from checkout; never locally assume payment succeeded.
- Explain quota exhaustion and provide a plan-upgrade path.
- Preserve access to existing recipes and history after downgrade/expiry.
- Hide Chef from purchasable plans until separately enabled.

Do not implement a UI that advertises unapproved prices or Chef features. Price/currency copy must come from server-side plan configuration or an explicitly maintained product configuration, not duplicated literals in multiple screens.

## 9. Configuration and operational concerns

- Use `Europe/Istanbul` for Cooker daily quota boundaries. Persist timestamps consistently (prefer UTC instants) and derive quota dates in the configured business time zone.
- Make the business time zone configurable if expansion to other markets is expected, but do not let a device's local time determine quota reset.
- Keep plan availability, billing interval, price identifier, quota, and feature flags in centralized configuration/database records.
- Do not store current prices in source code as permanent truth.
- Record generation token usage and provider/model cost separately from membership quota. One recipe allowance is a product entitlement, not a token budget.
- Add monitoring for generation count, quota denials, payment failures, webhook failures, refunds, and active subscriptions.
- Ensure database migrations are backward-compatible with existing users and recipes.
- Existing accounts should receive Free membership and the 3-lifetime-generation allowance through a safe migration/defaulting strategy. Do not reset or duplicate existing usage if the user has already generated recipes; decide how historical generated recipes map to the initial allowance before rollout.

## 10. Testing requirements

### Free
- New account receives exactly 3 lifetime AI recipe generations.
- Fourth successful AI generation is rejected with a stable quota-exhausted response.
- Logging out/in, changing device, and reinstalling do not reset usage.
- Failed generation does not consume usage.
- Catalog recipe lookup does not consume AI generation quota.
- Concurrent requests cannot exceed the remaining lifetime allowance.

### Cooker
- Monthly Cooker has a limit of 10 AI generations per Istanbul calendar day.
- Yearly Cooker has the same 10-per-day limit.
- Usage resets at Istanbul midnight, including month/year boundaries and daylight-saving/time-zone-safe date calculations.
- The 11th generation on a given day is rejected.
- Existing recipes and cooking sessions do not consume generation quota.
- Concurrent requests cannot exceed the daily allowance.
- Expired/canceled/failed-payment subscriptions follow the configured entitlement policy.

### Payments
- Checkout creation rejects unknown/unavailable plans and client-supplied price tampering.
- Cooker entitlement is not activated by a client redirect alone.
- Valid provider webhook activates the correct tier and interval.
- Invalid webhook signatures are rejected.
- Duplicate webhook events are idempotent.
- Renewal updates billing period once.
- Cancellation at period end preserves access until the paid-through date.
- Payment failure, refund, and chargeback map to the configured states.
- Existing user content remains available after downgrade/expiry.

### Chef
- Chef cannot be purchased or accidentally treated as unlimited while unconfigured.
- Adding Chef later should require plan configuration and explicit feature/limit decisions, not a redesign of the membership domain.

Use fake payment-provider adapters in automated tests. Tests must not require live payment credentials or real charges.

## 11. Non-goals for this iteration

- Defining or launching Chef benefits, price, quota, or checkout.
- Choosing a payment provider before business and market requirements are evaluated.
- Building a complex coupon, referral, gift, family, or enterprise billing system.
- Implementing advanced tax invoicing without selecting the launch provider/merchant setup.
- Charging for curated catalog lookups or existing recipe views.
- Implementing a separate token-credit wallet unless product analytics later show it is necessary.

## 12. Definition of done

- [ ] Free, Cooker, and future Chef tiers are represented in the domain model.
- [ ] Only Free and Cooker Monthly/Yearly are available for purchase.
- [ ] Free allowance is 3 lifetime AI recipe generations per account.
- [ ] Cooker Monthly allowance is 10 AI recipe generations per Istanbul calendar day.
- [ ] Cooker Yearly allowance is also 10 AI recipe generations per Istanbul calendar day.
- [ ] Quotas are checked and enforced server-side, safely under concurrent requests.
- [ ] Failed generation does not consume quota; successful complete AI generation consumes exactly one.
- [ ] Curated catalog lookup and existing recipe use do not consume AI recipe quota.
- [ ] Membership and entitlement status are available to the frontend.
- [ ] Payment integration is isolated behind a provider-neutral adapter.
- [ ] Paid access is granted only after trusted payment verification.
- [ ] Webhook processing is signature-verified and idempotent.
- [ ] Cancellation, renewal, failed-payment, and downgrade behavior is tested.
- [ ] Chef remains unavailable until separately specified.
- [ ] Existing user recipes/history survive membership changes.
- [ ] Tests cover quota, lifecycle, payment security, and failure paths.

## 13. Instructions for the coding agent

Before implementation:
1. Inspect the existing User/account, auth, recipe generation, recipe persistence, profile API, migrations, and frontend navigation/settings.
2. Identify whether any membership, usage, or payment-related models already exist. Extend them rather than creating duplicates.
3. Confirm how many AI-generated recipes existing accounts have already created and define a migration/backfill rule before granting the initial Free allowance.
4. Implement the domain entitlement and usage enforcement before wiring checkout UI.
5. Use a fake provider adapter until a real payment provider is selected.
6. Keep plan and quota values centralized; do not duplicate business rules in frontend code.
7. Add transactional/concurrency tests and deterministic payment webhook tests.
8. Run backend tests and frontend build. Report environment-blocked verification separately from code failures.
9. Update `docs/IMPLEMENTATION_STATUS.md` only after verifying implementation.

This document specifies product behavior and architecture. It does not authorize a specific payment provider, price, tax policy, Chef feature set, or legal/merchant arrangement.
