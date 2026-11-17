package com.scaler.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scaler.paymentservice.dtos.CreatePaymentRequest;
import com.scaler.paymentservice.gateways.GatewayException;
import com.scaler.paymentservice.gateways.RazorpayGateway;
import com.scaler.paymentservice.models.Payment;
import com.scaler.paymentservice.models.PaymentStatus;
import com.scaler.paymentservice.repositories.PaymentRepo;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentServiceTest {

    private final PaymentRepo repo = mock(PaymentRepo.class);
    private final RazorpayGateway gateway = mock(RazorpayGateway.class);
    private PaymentService service;

    @BeforeEach
    void setUp() {
        when(repo.saveAndFlush(any())).thenAnswer(call -> {
            Payment p = call.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(repo.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new PaymentService(repo, gateway);
    }

    private CreatePaymentRequest request(long amount) {
        return new CreatePaymentRequest("ORD-1", amount, "Red Lipstick");
    }

    @Test
    void create_newKey_savesCreated_callsTheGateway_thenSavesPending() {
        when(gateway.createLink(any())).thenReturn(new RazorpayGateway.Link("plink_1", "https://rzp.io/i/abc"));

        PaymentService.Result result = service.create("7", "k1", request(49900));

        assertThat(result.created()).isTrue();
        Payment payment = result.payment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getGatewayReference()).isEqualTo("plink_1");
        assertThat(payment.getPaymentUrl()).isEqualTo("https://rzp.io/i/abc");
        assertThat(payment.getUserId()).isEqualTo("7");
        assertThat(payment.getCurrency()).isEqualTo("INR");
        assertThat(payment.getAmount()).isEqualTo(49900);
        InOrder order = inOrder(repo, gateway);
        order.verify(repo).saveAndFlush(payment);
        order.verify(gateway).createLink(payment);
        order.verify(repo).save(payment);
    }

    @Test
    void create_noDescription_usesTheOrderRef() {
        when(gateway.createLink(any())).thenReturn(new RazorpayGateway.Link("plink_1", "u"));

        Payment payment = service.create("7", "k1", new CreatePaymentRequest("ORD-5", 1000, null)).payment();

        assertThat(payment.getDescription()).isEqualTo("Payment for order ORD-5");
    }

    @Test
    void create_sameKeyAndSamePayment_returnsTheEarlierOneAndNeverCallsTheGateway() {
        Payment earlier = new Payment();
        earlier.setOrderRef("ORD-1");
        earlier.setAmount(49900);
        when(repo.findByUserIdAndIdempotencyKey("7", "k1")).thenReturn(Optional.of(earlier));

        PaymentService.Result result = service.create("7", "k1", request(49900));

        assertThat(result.created()).isFalse();
        assertThat(result.payment()).isSameAs(earlier);
        verify(gateway, never()).createLink(any());
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void create_sameKeyButAnotherAmount_isRefusedWith409() {
        Payment earlier = new Payment();
        earlier.setOrderRef("ORD-1");
        earlier.setAmount(49900);
        when(repo.findByUserIdAndIdempotencyKey("7", "k1")).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> service.create("7", "k1", request(99900)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(gateway, never()).createLink(any());
    }

    @Test
    void create_sameKeyButAnotherOrder_isRefusedWith409() {
        Payment earlier = new Payment();
        earlier.setOrderRef("ORD-1");
        earlier.setAmount(49900);
        when(repo.findByUserIdAndIdempotencyKey("7", "k1")).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> service.create("7", "k1", new CreatePaymentRequest("ORD-2", 49900, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void create_twoRequestsAtTheSameMoment_theLoserGetsTheWinnersPayment() {
        Payment winner = new Payment();
        winner.setOrderRef("ORD-1");
        winner.setAmount(49900);
        when(repo.findByUserIdAndIdempotencyKey("7", "k1")).thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new DataIntegrityViolationException("Duplicate entry")).when(repo).saveAndFlush(any());

        PaymentService.Result result = service.create("7", "k1", request(49900));

        assertThat(result.created()).isFalse();
        assertThat(result.payment()).isSameAs(winner);
        verify(gateway, never()).createLink(any());
    }

    @Test
    void create_gatewayFails_savesFailedWithTheReasonAndPassesTheErrorOn() {
        when(gateway.createLink(any())).thenThrow(new GatewayException("Razorpay answered 400: bad"));

        assertThatThrownBy(() -> service.create("7", "k1", request(49900)))
                .isInstanceOf(GatewayException.class).hasMessage("Razorpay answered 400: bad");

        org.mockito.ArgumentCaptor<Payment> saved = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getValue().getFailureReason()).isEqualTo("Razorpay answered 400: bad");
        assertThat(saved.getValue().getPaymentUrl()).isNull();
    }

    @Test
    void get_aPaymentOfAnotherUser_is404() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, "8")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("8", id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
