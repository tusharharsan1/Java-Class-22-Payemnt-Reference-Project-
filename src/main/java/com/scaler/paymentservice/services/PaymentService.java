package com.scaler.paymentservice.services;

import com.scaler.paymentservice.dtos.CreatePaymentRequest;
import com.scaler.paymentservice.gateways.GatewayException;
import com.scaler.paymentservice.gateways.RazorpayGateway;
import com.scaler.paymentservice.models.Payment;
import com.scaler.paymentservice.models.PaymentStatus;
import com.scaler.paymentservice.repositories.PaymentRepo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    // created = true for a new payment, false when the same key came again.
    public record Result(Payment payment, boolean created) {
    }

    private final PaymentRepo repo;
    private final RazorpayGateway gateway;

    public PaymentService(PaymentRepo repo, RazorpayGateway gateway) {
        this.repo = repo;
        this.gateway = gateway;
    }

    public Payment get(String userId, UUID id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such payment"));
    }

    // Copies the request into a new row. The status starts as CREATED.
    private Payment newPayment(String userId, String key, CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setIdempotencyKey(key);
        payment.setOrderRef(request.orderRef());
        payment.setAmount(request.amount());
        payment.setCurrency("INR");
        payment.setDescription(request.description() == null ? "Payment for order " + request.orderRef()
                : request.description());
        return payment;
    }

    // No database transaction stays open while we wait for the gateway: three short saves.
    public Result create(String userId, String key, CreatePaymentRequest request) {
        Optional<Payment> earlier = repo.findByUserIdAndIdempotencyKey(userId, key);
        if (earlier.isPresent()) {
            return repeat(earlier.get(), request);
        }
        Payment payment = newPayment(userId, key, request);
        try {
            payment = repo.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same key came at the same moment. The unique key let only one in.
            return repeat(repo.findByUserIdAndIdempotencyKey(userId, key).orElseThrow(), request);
        }

        try {
            RazorpayGateway.Link link = gateway.createLink(payment);
            payment.setGatewayReference(link.id());
            payment.setPaymentUrl(link.shortUrl());
            payment.moveTo(PaymentStatus.PENDING);
        } catch (GatewayException e) {
            payment.setFailureReason(e.getMessage());
            payment.moveTo(PaymentStatus.FAILED);
            repo.save(payment);
            throw e;
        }
        return new Result(repo.save(payment), true);
    }

    // The same key is only the same payment if the order and the amount are the same too.
    private Result repeat(Payment earlier, CreatePaymentRequest request) {
        if (earlier.getAmount() != request.amount() || !earlier.getOrderRef().equals(request.orderRef())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This Idempotency-Key was already used for a different payment");
        }
        return new Result(earlier, false);
    }
}
