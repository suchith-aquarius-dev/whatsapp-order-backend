# WhatsApp Ordering App (Spring Boot, single application)

Combines:
- **Spring MVC** – serves the order form website (Thymeleaf)
- **REST controller** – handles the WhatsApp Cloud API webhook
- **Spring Data JPA** – persists orders (H2 file DB, swap for Postgres/MySQL in prod)

## Flow implemented

1. User messages your WhatsApp Business number →
   `WhatsAppWebhookController.receiveEvent()` fires.
2. That handler calls `WhatsAppApiService.sendOrderFormLink(waId)`, which
   sends an interactive **CTA-URL** message back to the user with a link to
   `/order?token=...`. The token is an HMAC-signed, 30-minute-expiring
   package that carries the `wa_id` — see `LinkTokenService`.
3. User taps the link, opens `OrderController.showForm()`, validates the
   token, and sees the order form (`order-form.html`).
4. User submits the form → `OrderController.submitForm()` validates the
   token again, saves the `Order` + `OrderItem`s, then calls
   `WhatsAppApiService.sendPayNowMessage(order)` to push a **Pay Now**
   button straight back to that same WhatsApp chat.
5. If the 24-hour customer-service window has already closed, use
   `sendPayNowTemplate(order)` instead — this requires an approved Meta
   message **template** (Utility category) with one URL button whose
   `{{1}}` becomes the order id.

## Setup

### 1. Meta / WhatsApp Business Platform
- Create a Meta App with the WhatsApp product added, get:
  - Phone Number ID
  - Permanent Access Token (System User token, not the 24h temp token)
- In **App Dashboard → WhatsApp → Configuration → Webhook**:
  - Callback URL: `https://yourdomain.com/webhook`
  - Verify token: must match `whatsapp.webhook-verify-token` below
  - Subscribe to the `messages` field
- Your server must be reachable over HTTPS publicly (use ngrok for local dev:
  `ngrok http 8080`, then use that URL as the callback).

### 2. Configure `application.yml` (or env vars)

```
WA_PHONE_NUMBER_ID=xxxxxxxxxxxx
WA_ACCESS_TOKEN=xxxxxxxxxxxx
WA_VERIFY_TOKEN=some-string-you-choose
WA_LINK_SECRET=a-long-random-secret-for-signing-links
APP_BASE_URL=https://yourdomain.com
PAYMENT_BASE_URL=https://yourpay.example.com/checkout
WA_PAYMENT_TEMPLATE=order_payment_ready
```

### 3. Run

```
mvn spring-boot:run
```

### 4. Test locally with ngrok

```
ngrok http 8080
# copy the https URL into Meta's webhook config
```

Send a message to your WhatsApp test number from your own phone — you
should get the "Order Now" button, and after submitting the form, a
"Pay Now" button back in the same chat.

## Notes / production hardening to add

- **Signature verification on inbound webhooks**: Meta signs each webhook
  POST with `X-Hub-Signature-256`. This starter does not verify it yet —
  add that check in `WhatsAppWebhookController` before trusting payloads.
- **Idempotency**: Meta may redeliver webhook events; dedupe on message id.
- **Async processing**: move `postToGraphApi` calls onto a queue/executor
  so webhook responses return instantly (Meta expects a fast 200).
- **Real payment integration**: `sendPayNowMessage` currently links to a
  placeholder `payment-base-url/{orderId}` — wire this to your actual
  Razorpay/Stripe/PayU hosted checkout, and update `Order.status` via
  that gateway's webhook when payment completes.
- **Product catalog**: replace the hardcoded list in `OrderController`
  with a real repository/service.
- **Quantity binding**: the demo form pairs `productIds[]`/`quantities[]`
  by index, which only works cleanly when checkboxes are handled with a
  little JS to keep arrays aligned (unchecked items are simply omitted
  from `productIds`). For production, prefer per-product hidden quantity
  fields or a small JS layer building a JSON payload instead of raw form
  arrays.
