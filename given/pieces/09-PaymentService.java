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
}
