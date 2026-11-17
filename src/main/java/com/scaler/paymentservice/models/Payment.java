package com.scaler.paymentservice.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String userId;
    private String idempotencyKey;
    private String orderRef;

    // Money is a whole number of paise. 49900 means 499.00 rupees.
    private long amount;
    private String currency;
    private String description;

    // No setter: the status changes only through moveTo, so an illegal move is refused.
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.CREATED;

    private String gatewayReference;
    private String paymentUrl;
    private String failureReason;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public void moveTo(PaymentStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("A payment cannot move from " + status + " to " + next);
        }
        status = next;
    }
}
