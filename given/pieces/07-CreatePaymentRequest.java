package com.scaler.paymentservice.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotBlank @Size(max = 64) String orderRef,
        @Min(100) long amount,
        @Size(max = 255) String description) {
}
