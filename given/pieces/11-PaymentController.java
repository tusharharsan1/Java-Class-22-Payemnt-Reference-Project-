package com.scaler.paymentservice.controllers;

import com.scaler.paymentservice.dtos.CreatePaymentRequest;
import com.scaler.paymentservice.dtos.PaymentDto;
import com.scaler.paymentservice.services.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePaymentRequest request) {
        return PaymentDto.from(service.create(jwt.getSubject(), request));
    }

    @GetMapping("/{id}")
    public PaymentDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PaymentDto.from(service.get(jwt.getSubject(), id));
    }

    // The customer's browser comes back here after paying. Anyone can type this address, so it never changes a status.
    @GetMapping("/callback")
    public Map<String, String> callback(@RequestParam Map<String, String> params) {
        log.info("The browser came back from the gateway with {}. Not trusted.", params);
        return Map.of("message", "Thank you. We are checking your payment. Ask GET /payments/{id} for the status.");
    }
}
