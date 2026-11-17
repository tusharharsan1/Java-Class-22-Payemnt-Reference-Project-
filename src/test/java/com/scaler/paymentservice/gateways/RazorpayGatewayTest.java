package com.scaler.paymentservice.gateways;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jayway.jsonpath.JsonPath;
import com.scaler.paymentservice.models.Payment;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Checks the request that goes to Razorpay and the answer that comes back, with no real call.
class RazorpayGatewayTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RazorpayGateway gateway = new RazorpayGateway(builder, "https://razorpay.test", "rzp_test_abc",
            "the-secret", 30, "http://localhost:8082/payments/callback");

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(UUID.fromString("01a1e532-11f3-7b70-8d2a-6457202d8b8f"));
        payment.setAmount(49900);
        payment.setCurrency("INR");
        payment.setDescription("Red Lipstick");
        return payment;
    }

    @Test
    void createLink_sendsTheLoginAndTheFieldsRazorpayNeeds_andReadsTheLink() {
        String login = "Basic " + Base64.getEncoder().encodeToString("rzp_test_abc:the-secret".getBytes());
        server.expect(requestTo("https://razorpay.test/v1/payment_links"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", login))
                .andExpect(jsonPath("$.amount").value(49900))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.description").value("Red Lipstick"))
                // our payment id is the reference, so Razorpay refuses a second link for the same payment
                .andExpect(jsonPath("$.reference_id").value("01a1e532-11f3-7b70-8d2a-6457202d8b8f"))
                .andExpect(jsonPath("$.callback_url").value("http://localhost:8082/payments/callback"))
                .andExpect(jsonPath("$.callback_method").value("get"))
                .andExpect(request -> {
                    // 30 minutes ahead. Razorpay needs at least 15.
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    long expireBy = ((Number) JsonPath.read(body, "$.expire_by")).longValue();
                    long minutes = (expireBy - Instant.now().getEpochSecond()) / 60;
                    assertThat(minutes).isBetween(29L, 30L);
                    // no customer details and no SMS or email from Razorpay
                    assertThat(body).doesNotContain("customer").doesNotContain("notify");
                })
                .andRespond(withSuccess("{\"id\":\"plink_abc123\",\"short_url\":\"https://rzp.io/i/xyz\","
                        + "\"status\":\"created\",\"amount\":49900,\"accept_partial\":false}",
                        MediaType.APPLICATION_JSON));

        RazorpayGateway.Link link = gateway.createLink(payment());

        assertThat(link.id()).isEqualTo("plink_abc123");
        assertThat(link.shortUrl()).isEqualTo("https://rzp.io/i/xyz");
        server.verify();
    }

    @Test
    void createLink_razorpaySays400_becomesAGatewayExceptionWithTheReason() {
        server.expect(requestTo("https://razorpay.test/v1/payment_links"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"amount too small\"}}"));

        assertThatThrownBy(() -> gateway.createLink(payment()))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("Razorpay answered 400")
                .hasMessageContaining("amount too small");
    }

    @Test
    void createLink_noAnswer_becomesAGatewayException() {
        server.expect(requestTo("https://razorpay.test/v1/payment_links"))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> gateway.createLink(payment()))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("Razorpay did not answer");
    }

    @Test
    void createLink_noKeys_failsClearlyBeforeAnyCall() {
        RazorpayGateway withoutKeys = new RazorpayGateway(RestClient.builder(), "https://razorpay.test", "", "", 30,
                "http://localhost:8082/payments/callback");

        assertThatThrownBy(() -> withoutKeys.createLink(payment()))
                .isInstanceOf(GatewayException.class)
                .hasMessage("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are not set");
    }

}
