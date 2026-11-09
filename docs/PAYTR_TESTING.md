# PayTR iFrame Test Checkout

This integration currently supports one-time test payments through PayTR iFrame.
It does not tokenize cards, create a PayTR recurring subscription, or automatically
renew a Cooker membership. A successful monthly checkout grants 30 days; a yearly
checkout grants 365 days. The membership then expires unless a new purchase is made.

## 1. PayTR merchant credentials

Create/activate a PayTR merchant account and retrieve the merchant ID, merchant key,
and merchant salt from the merchant panel's Information page. There are no shared
public test credentials. The iFrame test form supplies its own test card details when
the merchant's test mode is enabled.

## 2. Local environment

Copy .env.example to .env and fill these values:

    PAYMENT_PROVIDER=paytr
    PAYMENT_CHECKOUT_ENABLED=true
    PAYMENT_TEST_MODE=true
    PAYMENT_LIVE_ENABLED=false

    PAYTR_MERCHANT_ID=your-test-merchant-id
    PAYTR_MERCHANT_KEY=your-test-merchant-key
    PAYTR_MERCHANT_SALT=your-test-merchant-salt

    # Must be a public HTTPS URL reachable by PayTR:
    PAYMENT_CALLBACK_URL=https://YOUR_PUBLIC_HOST/api/v1/payments/paytr/callback

    # Browser return page after PayTR checkout:
    PAYMENT_RETURN_URL=https://YOUR_FRONTEND_HOST/

    # Public IPv4 of the user for localhost/LAN testing:
    PAYTR_TEST_USER_IP=YOUR_PUBLIC_IPV4

    # Test-only prices, not production pricing:
    COOKER_MONTHLY_PRICE=1.00
    COOKER_YEARLY_PRICE=2.00

The sample prices are test values only. They are not production prices. The API reads
the plan price from server configuration; the mobile/web client cannot submit an amount.

For local callback testing, expose the backend using a trusted HTTPS tunnel or deploy
a temporary test backend. Configure the exact public callback URL in both .env and
the PayTR merchant panel. Do not point PayTR at localhost or a private LAN address.

## 3. Start the application

Start PostgreSQL, then the backend. Flyway applies
V7__paytr_iframe_checkout.sql automatically. Start the web or mobile client and sign
in with a verified test account.

The membership response exposes Cooker plans as purchasable only when checkout is
enabled and a positive server-side price is configured.

## 4. Test a payment

1. Open Profile / Membership.
2. Enter the address and phone required by PayTR.
3. Select Cooker Monthly or Cooker Yearly.
4. PayTR's hosted iFrame checkout opens.
5. Complete the test transaction using the test details supplied by PayTR's iFrame.
6. PayTR POSTs the result to /api/v1/payments/paytr/callback.
7. The backend verifies the HMAC, matches the amount and currency to the stored checkout,
   processes the callback idempotently, and activates the entitlement only on success.
8. Return to the app and refresh membership. The backend remains authoritative.

## 5. Safety controls

- PAYMENT_TEST_MODE=true is the default.
- Live checkout is blocked unless both PAYMENT_TEST_MODE=false and
  PAYMENT_LIVE_ENABLED=true are explicitly configured.
- The checkout endpoint accepts only a plan code and customer details. Price is
  resolved on the server.
- Card number, expiry, CVV, and card tokens are never stored by this application.
- Invalid callback signatures are rejected.
- Duplicate callbacks do not activate or extend a membership twice.
- Amount/currency mismatches and expired orders do not grant Cooker access.
- The client redirect is informational; only a verified PayTR callback activates access.

## 6. Before production

Do not switch to live mode until PayTR confirms the appropriate merchant product and
commercial terms. This implementation is not an auto-renewing subscription. Automatic
renewal requires PayTR Card Storage API enrollment, secure utoken/ctoken persistence,
non-3D recurring payment permission, renewal scheduling, retry/grace-period policy,
cancellation, refund/chargeback reconciliation, and dedicated tests.
