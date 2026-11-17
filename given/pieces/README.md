# given/pieces: the code you PASTE in the payment class

The numbers are the order of the class. Each file has its imports. The code you TYPE in class is not here: it is in the reference project, one folder up.

| # | File | Where it goes | What to do |
|---|---|---|---|
| 01 | `V1__payment.sql` | `src/main/resources/db/migration/` | Paste the whole file. Section 3. |
| 02 | `Payment.java` | `src/main/java/com/scaler/paymentservice/models/` | Paste the whole file, then paste piece 03 inside it. Section 3. |
| 03 | `fragments/moveTo-method.txt` | `inside Payment, at the bottom` | Paste inside `Payment`, above the last brace. Section 3. |
| 04 | `PaymentRepo.java` | `src/main/java/com/scaler/paymentservice/repositories/` | Paste the whole file. Section 3. |
| 05 | `GatewayException.java` | `src/main/java/com/scaler/paymentservice/gateways/` | Paste the whole file. Section 4. |
| 06 | `RazorpayGateway.java` | `src/main/java/com/scaler/paymentservice/gateways/` | Paste the whole file. Then TYPE the method `createLink` inside it. Section 4. |
| 07 | `CreatePaymentRequest.java` | `src/main/java/com/scaler/paymentservice/dtos/` | Paste the whole file. Section 5. |
| 08 | `PaymentDto.java` | `src/main/java/com/scaler/paymentservice/dtos/` | Paste the whole file. Section 5. |
| 09 | `PaymentService.java` | `src/main/java/com/scaler/paymentservice/services/` | Paste the whole file, then paste piece 10 inside it. Section 5. |
| 10 | `fragments/create-first-version.txt` | `inside PaymentService, at the bottom` | Paste inside `PaymentService`. This is the FIRST version of `create`. Section 5. |
| 11 | `PaymentController.java` | `src/main/java/com/scaler/paymentservice/controllers/` | Paste the whole file. This is the FIRST version: piece 14 replaces its `create` method in Section 6. Section 5. |
| 12 | `fragments/Result-record.txt` | `top of PaymentService` | Paste at the top of `PaymentService`, below the class line. Section 6. |
| 13 | `fragments/repeat-method.txt` | `bottom of PaymentService` | Paste below `create`. Section 6. |
| 14 | `fragments/controller-create-method.txt` | `PaymentController` | Paste over the old `create` method. Let the editor add the imports. Section 6. |
| 15 | `ApiExceptionHandler.java` | `src/main/java/com/scaler/paymentservice/exceptions/` | Paste the whole file. Section 7. |

You TYPE these in class (the answers are the reference project): `PaymentStatus`, the method `createLink` in `RazorpayGateway`, and three changes in `create` (Section 6).

`given/skeleton/` is the project start: `pom.xml`, `application.properties`, `SecurityConfig` and the application class.
`tools/fake_razorpay.py` (one folder up from `given/`) is a pretend Razorpay. Section 7 uses it to show a slow gateway.
