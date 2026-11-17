package com.scaler.paymentservice.repositories;

import com.scaler.paymentservice.models.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepo extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    // Somebody else's payment is "not found", not "forbidden".
    Optional<Payment> findByIdAndUserId(UUID id, String userId);
}
