package com.scaler.paymentservice.dtos;

import com.scaler.paymentservice.models.Payment;
import java.time.Instant;
import java.util.UUID;

// What we send back. We never send the user id or the idempotency key.
public record PaymentDto(UUID id, String orderRef, long amount, String currency, String status,
                         String gatewayReference, String paymentUrl, String failureReason, Instant createdAt) {

    public static PaymentDto from(Payment p) {
        return new PaymentDto(p.getId(), p.getOrderRef(), p.getAmount(), p.getCurrency(), p.getStatus().name(),
                p.getGatewayReference(), p.getPaymentUrl(), p.getFailureReason(), p.getCreatedAt());
    }
}
