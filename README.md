# paymentservice (ShopNest): the project at the END of the first payment class

The third ShopNest service, with **Razorpay as the only gateway**. A logged-in customer asks for a payment. The service saves it, asks Razorpay for a payment link, and gives the link back. It is safe to click twice, and it answers clearly when Razorpay says no or does not answer. It does **not** yet learn that the customer paid: the next class adds the webhook, the "ask Razorpay" call, the scheduled job and refunds. Spring Boot 4.1.1, Java 17 (also runs on 21), MySQL 8 or newer, port **8082**.

## What is in it

| Part | What it does |
|---|---|
| `POST /payments` | Header `Idempotency-Key` (needed), body `orderRef`, `amount` (in paise, at least 100), optional `description`. **201** for a new payment, **200** when the same key comes again, **409** for the same key with another order or amount, **502** when Razorpay says no or does not answer. |
| `GET /payments/{id}` | The payment of the logged-in user. Another user's payment is **404**. |
| `GET /payments/callback` | Open (the customer's browser comes back here). It says thank you and changes nothing. |
| `models/PaymentStatus`, `Payment.moveTo` | The states and the only moves that are allowed. `status` has no setter. |
| `gateways/RazorpayGateway` | A plain `RestClient` call to `POST /v1/payment_links`. All the talk with Razorpay is in this one class. |
| `services/PaymentService` | Looks for the key, saves CREATED, calls the gateway with no transaction open, saves PENDING or FAILED. The unique key `(user_id, idempotency_key)` decides a race. |
| `exceptions/ApiExceptionHandler` | A gateway problem becomes a clear 502; a refused move becomes 409. |
| `db/migration` | `V1__payment.sql` (status is a VARCHAR). |
| `config/SecurityConfig` | The same token check as the catalog, with one open door: the callback. |

## Run it

```bash
export DB_PASSWORD=your-password RAZORPAY_KEY_ID=rzp_test_... RAZORPAY_KEY_SECRET=...   # never write the keys in a file
mysql -u root -p -e "CREATE DATABASE paymentservice"
mvn spring-boot:run
```

The user service (port 8081) must run, because it gives the login tokens. Use **Test Mode** keys only (they start with `rzp_test_`).

## The folders `given/` and `tools/`

- `given/skeleton/`: `pom.xml`, `application.properties`, `SecurityConfig` and the application class.
- `given/pieces/`: every piece the instructor pastes, in the order of the class, with imports. Made from the code boxes of the script. Its README says where each piece goes.
- `tools/fake_razorpay.py`: a pretend Razorpay for tests. Its error words and its link format were copied from real Test Mode answers. Class 23 does not need it. The next class uses it, to show a slow gateway (`POST /_control?delay=8`) and a link that gets paid (`POST /_paid/<plink id>`).

## What was tested (JDK 21, Boot 4.1.1, MySQL 8.4)

- `mvn test` with a test schema (the integration test refuses a URL without `_test`): **44 tests, 0 failures.** Status table (17); the service with Mockito (8); the Razorpay call with `MockRestServiceServer` (4); the controller with MockMvc (10); and on the real MySQL (5): 12 requests at once with one key make one row and one link, the same key with another amount, and so on.
- **Every code box of the class script was built into a project, stage by stage (four stages), and every command of the script was run** against the pretend Razorpay and the real user service: **20 of 20 checks passed** (17 Nov 2026, after the class was changed to have more theory and a shorter Section 7; this includes the `openssl` demo of Section 2). The last stage equals this project, line by line (comments and imports aside).
- A gateway that waits 8 seconds gives a 502 after about 5 seconds, and a FAILED row (checked on 17 Nov 2026, before this demo moved to the next class).

## Tested against the real Razorpay (Test Mode, 16 Nov 2026)

With a real Razorpay account in **Test Mode** and `rzp_test_` keys (nothing was done in Live mode):
- The by-hand `curl` (Section 4) makes a real link: `HTTP 200`, `plink_...`, `short_url` of the form `https://rzp.io/rzp/...`, `status` `created`. The same `reference_id` again gives `HTTP 400` with "payment link with given reference_id: ... already exists. Please create a payment link with a different reference_id".
- **Razorpay accepted the `http://localhost:8082/payments/callback` address.** The Java call (a `RestClient` body sent in pieces, without a length) was accepted: `201`, `PENDING`, and the same key again gave `200`.
- A link time of 10 minutes: Razorpay answered 400 with "expire_by: timestamp must be atleast 15 minutes in future." Our service answered 502 and saved a FAILED row with that reason.
- Razorpay's own payment page for the link says "This payment link is created in Test Mode. Only test payments can be made for this." It shows the description, the amount and a **Receipt** line that is our payment id.
- **A real test payment, end to end** (the instructor paid with the test card; no real money): the card is followed by a Razorpay **demo bank page** with a Success and a Failure button. After Success the browser went to `http://localhost:8082/payments/callback?razorpay_payment_id=...&razorpay_payment_link_id=...&razorpay_payment_link_reference_id=<our payment id>&razorpay_payment_link_status=paid&razorpay_signature=...` and showed our thank-you message. Our table still said `PENDING`. `GET /v1/payment_links/{id}` answered `status: paid`, `amount_paid: 49900`, one payment by card with status `captured`.

## Not tested

- JDK 17 at run time, Windows, and other laptops.
