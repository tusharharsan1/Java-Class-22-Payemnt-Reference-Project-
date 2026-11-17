package com.scaler.paymentservice.models;

// The steps of a payment, and the only moves that are allowed.
public enum PaymentStatus {
    CREATED, PENDING, PAID, EXPIRED, CANCELLED, FAILED;

    public boolean canMoveTo(PaymentStatus next) {
        return switch (this) {
            case CREATED -> next == PENDING || next == FAILED;
            case PENDING -> next == PAID || next == EXPIRED || next == CANCELLED;
            default -> false; // PAID, EXPIRED, CANCELLED and FAILED are final
        };
    }
}
