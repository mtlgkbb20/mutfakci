# Payment Provider Decision: PayTR

**Status:** Selected for the first payment integration  
**Branch:** `feature/membership-plans`  
**Decision:** Use PayTR for Cooker monthly/yearly subscriptions. Keep the membership domain provider-neutral.

## Why PayTR

- PayTR's subscription product supports recurring collection and card tokenization.
- Its published commercial model does not require a fixed subscription-platform monthly fee; the merchant commission is quoted according to business profile and transaction volume.
- This is a reasonable fit for an early-stage product with two paid subscription intervals.
- The exact merchant commission, settlement terms, supported recurring-payment behavior, refund handling, and account eligibility must be confirmed in the merchant agreement before production launch.

## Product rules (unchanged)

- **Free:** 3 successful AI recipe generations over the account lifetime.
- **Cooker Monthly:** 10 successful AI recipe generations per Istanbul calendar day.
- **Cooker Yearly:** same 10/day quota; yearly billing does not create an annual pooled quota.
- **Chef:** represented as a future tier only; not purchasable and shown as **Coming soon**. Do not invent price, quota, billing interval, or features.
- Catalog lookups, reopening/saving existing recipes, and cooking follow-ups do not consume recipe-generation quota.
- Failed or invalid AI generation does not consume quota.
- Backend remains authoritative for membership and quota.

## Integration boundary

Implement a PayTR adapter behind an internal payment-provider interface. Membership and entitlement services must not depend on PayTR request/response DTOs.

Responsibilities:

- Membership service: plan availability, subscription lifecycle, effective paid access.
- Entitlement/usage service: Free lifetime and Cooker daily quota enforcement.
- PayTR adapter: checkout initialization, provider response mapping, callback verification, subscription operations supported by the merchant's enabled PayTR product.
- Webhook/callback handler: verify PayTR's documented hash/signature before processing; persist provider event/reference IDs and process each event idempotently.
- Fake adapter: deterministic tests without real merchant credentials or charges.

## Security and payment correctness

- Never accept a client redirect or client-supplied status as proof of payment.
- Never accept an arbitrary amount, tier, or plan price from the client. Resolve plan and amount from server-side configuration.
- Do not store card number, CVV, or raw payment credentials. Use PayTR's approved hosted/tokenized collection flow.
- Keep merchant credentials and salts in environment/secret management; never commit them.
- Validate callback authenticity exactly as specified in the current PayTR integration documentation. Reject invalid callbacks.
- Make callback handling idempotent and safe under retries.
- Do not activate Cooker until trusted server-side confirmation is received.
- Record subscription and payment state transitions without logging secrets or sensitive card data.
- Keep the adapter replaceable; no PayTR-specific fields should leak into public membership DTOs.

## Configuration

Use environment variables (names are implementation placeholders and must be aligned with the selected PayTR product/API):

- `PAYTR_MERCHANT_ID`
- `PAYTR_MERCHANT_KEY`
- `PAYTR_MERCHANT_SALT`
- `PAYTR_BASE_URL` (only if required by the integration)

No real values belong in Git. Do not enable live payment mode by default.

## Subscription lifecycle to implement

1. Authenticated user requests a server-defined Cooker monthly or yearly checkout.
2. Backend validates plan availability and creates a pending internal subscription/checkout record.
3. Backend initializes PayTR checkout using server-side amount and product configuration.
4. Client opens the approved PayTR checkout experience.
5. PayTR sends its server callback; backend validates the callback hash and maps the verified result.
6. Successful initial payment activates the correct Cooker interval and paid-through period.
7. Recurring payment callbacks update the billing period once; duplicate callbacks have no additional effect.
8. Cancellation, failed renewal, grace period, refund, and chargeback behavior must follow the merchant-enabled PayTR subscription capabilities and an explicit product policy. Do not assume unsupported provider operations.

## Required before live activation

- PayTR merchant account approved for the business and recurring/subscription product enabled.
- Merchant-specific commercial quote and payout/settlement terms reviewed.
- Current PayTR subscription API/callback documentation checked against the exact enabled product.
- Sandbox/test credentials supplied through local environment or secret manager.
- Production callback URL deployed over HTTPS and verified.
- Refund, cancellation, failed-renewal, retry, and grace-period policies explicitly decided.
- Legal, tax, invoicing, privacy, and consumer cancellation requirements reviewed for the launch entity and market.

Until those prerequisites are complete, use the fake provider in tests and keep checkout disabled in production. Never simulate a successful live payment.

## Implementation order

1. Inspect existing user, recipe-generation, persistence, and API flows.
2. Implement membership tiers, subscription persistence, and centralized plan configuration.
3. Implement concurrency-safe quota reservation/commit/release around successful AI recipe generation.
4. Add provider-neutral payment interface and fake adapter.
5. Add PayTR adapter and verified callback endpoint using the exact current PayTR product contract.
6. Add frontend plan presentation and checkout handoff; show Chef as **Coming soon**.
7. Add tests for quota concurrency, invalid/duplicate callbacks, failed payments, renewals, cancellation, and access expiry.
8. Run backend tests and frontend build; report any environment-blocked checks.

**Important:** Selecting PayTR authorizes the provider choice, not live charging. Production payment activation still requires merchant onboarding, credentials, tested callback configuration, and explicit lifecycle policies.


## Repository implementation status (2026-11-09)

Implemented on `feature/membership-plans`:

- Shared `PaymentProvider` contract with provider code, recurring capability, checkout creation, and callback verification operations.
- PayTR adapter selected by `PAYMENT_PROVIDER=paytr`; PayTR callback HMAC verification uses the official iFrame/direct callback formula.
- Iyzico adapter selection point via `PAYMENT_PROVIDER=iyzico`; this adapter is currently a disabled integration stub, not a live iyzico implementation.
- Free membership backfill and per-account quota usage storage.
- Server-side quota reservation before AI generation and release on generation failure.
- Existing completed AI recipe generations are counted in the initial Free lifetime usage backfill.
- Membership summary endpoint: `GET /api/v1/membership`.
- Checkout endpoint exists but intentionally returns unavailable until prices, merchant configuration, card-token enrollment, recurring charge flow, and subscription lifecycle are implemented and tested.
- Web and mobile profile surfaces display current tier, remaining quota, Cooker plan descriptions, and Chef Coming soon.
- PayTR callback route is public to the provider and validates the callback signature. It does not activate memberships yet.
- Unit tests were added for Free quota reservation/release, Chef fail-closed behavior, and PayTR callback HMAC verification; they have not been executed in this environment.

### PayTR-specific production prerequisites

PayTR's official Card Storage API documents card tokenization and recurring payments using stored `utoken` / `ctoken`; recurring charges are Non-3D and require the merchant's Non-3D permission. The initial card enrollment flow and token persistence must be implemented before enabling automated renewals. See:
- https://dev.paytr.com/direkt-api/kart-saklama-api/yeni-kart-ekleme
- https://dev.paytr.com/direkt-api/kart-saklama-api/kayitli-kart-tekrarlayan-odeme

The current adapter deliberately reports recurring checkout as unsupported. This is safer than treating a one-time iFrame payment as a recurring subscription. The next implementation slice is PayTR card enrollment + stored-token persistence + recurring collection + signed callback-to-subscription reconciliation. An iyzico switch remains isolated behind the same `PaymentProvider` contract, but its adapter must be implemented and verified before selecting it.
