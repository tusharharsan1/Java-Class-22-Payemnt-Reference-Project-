package com.scaler.paymentservice.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// What the caller sends. The amount is in paise: 49900 means 499.00 rupees. Razorpay needs at least 100.
public record CreatePaymentRequest(
        @NotBlank @Size(max = 64) String orderRef,
        @Min(100) long amount,
        @Size(max = 255) String description) {
}
