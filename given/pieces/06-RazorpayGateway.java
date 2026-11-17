package com.scaler.paymentservice.gateways;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scaler.paymentservice.models.Payment;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RazorpayGateway {

    // The two things we need from Razorpay's long answer: its id for the link, and the address the customer opens.
    public record Link(String id, @JsonProperty("short_url") String shortUrl) {
    }

    private final RestClient restClient;
    private final boolean hasKeys;
    private final long expiryMinutes;
    private final String callbackUrl;

    public RazorpayGateway(RestClient.Builder builder,
                           @Value("${razorpay.base-url}") String baseUrl,
                           @Value("${razorpay.key-id}") String keyId,
                           @Value("${razorpay.key-secret}") String keySecret,
                           @Value("${payment.link-expiry-minutes}") long expiryMinutes,
                           @Value("${payment.callback-url}") String callbackUrl) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeaders(h -> h.setBasicAuth(keyId, keySecret)).build();
        this.hasKeys = !keyId.isBlank() && !keySecret.isBlank();
        this.expiryMinutes = expiryMinutes;
        this.callbackUrl = callbackUrl;
    }

    // The same fields as our curl. The reference is our payment id: Razorpay refuses a second link with it.
    private Map<String, Object> body(Payment payment) {
        return Map.of(
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "description", payment.getDescription(),
                "reference_id", payment.getId().toString(),
                "expire_by", Instant.now().plus(Duration.ofMinutes(expiryMinutes)).getEpochSecond(),
                "callback_url", callbackUrl,
                "callback_method", "get");
    }
}
