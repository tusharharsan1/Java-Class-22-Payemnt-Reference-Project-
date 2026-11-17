package com.scaler.paymentservice.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

// The rules of the status: which moves are allowed, and which are not.
class PaymentStatusTest {

    @ParameterizedTest
    @CsvSource({
            "CREATED, PENDING, true", "CREATED, FAILED, true", "CREATED, PAID, false",
            "PENDING, PAID, true", "PENDING, EXPIRED, true", "PENDING, CANCELLED, true",
            "PENDING, CREATED, false", "PENDING, FAILED, false",
            "PAID, PENDING, false", "PAID, EXPIRED, false", "PAID, CANCELLED, false",
            "EXPIRED, PAID, false", "CANCELLED, PAID, false", "FAILED, PENDING, false"})
    void canMoveTo_followsTheTable(PaymentStatus from, PaymentStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void newPayment_startsAsCreated() {
        assertThat(new Payment().getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void moveTo_allowedMove_changesTheStatus() {
        Payment payment = new Payment();

        payment.moveTo(PaymentStatus.PENDING);
        payment.moveTo(PaymentStatus.PAID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void moveTo_illegalMove_isRefusedAndTheStatusStays() {
        Payment payment = new Payment();
        payment.moveTo(PaymentStatus.PENDING);
        payment.moveTo(PaymentStatus.PAID);

        assertThatThrownBy(() -> payment.moveTo(PaymentStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A payment cannot move from PAID to PENDING");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
