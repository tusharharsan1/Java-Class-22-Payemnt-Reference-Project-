package com.scaler.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scaler.paymentservice.dtos.CreatePaymentRequest;
import com.scaler.paymentservice.gateways.GatewayException;
import com.scaler.paymentservice.gateways.RazorpayGateway;
import com.scaler.paymentservice.models.Payment;
import com.scaler.paymentservice.models.PaymentStatus;
import com.scaler.paymentservice.repositories.PaymentRepo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// A test on the REAL MySQL (a separate test schema). Flyway builds both tables, and Hibernate checks the first one.
// The Mockito test only pretends the unique key. This test uses the real ones.
@SpringBootTest
@ActiveProfiles("test")
class PaymentIntegrationTest {

    @Autowired
    private PaymentService service;
    @Autowired
    private PaymentRepo repo;
    @MockitoBean
    private RazorpayGateway gateway;
    @Value("${spring.datasource.url}")
    private String databaseUrl;

    private static final CreatePaymentRequest REQUEST = new CreatePaymentRequest("ORD-1", 49900, "Red Lipstick");

    @BeforeEach
    void emptyTheTestSchema() {
        assertThat(databaseUrl).as("this test deletes every payment, so it must use the test schema").contains("_test");
        repo.deleteAll();
        when(gateway.createLink(any())).thenAnswer(call -> {
            Payment p = call.getArgument(0);
            return new RazorpayGateway.Link("plink_" + p.getId(), "https://rzp.io/i/test");
        });
    }

    @Test
    void create_savesAPendingPaymentWithTheLink() {
        Payment payment = service.create("7", "k1", REQUEST).payment();

        Payment saved = repo.findById(payment.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getAmount()).isEqualTo(49900);
        assertThat(saved.getGatewayReference()).startsWith("plink_");
        assertThat(saved.getPaymentUrl()).isEqualTo("https://rzp.io/i/test");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void create_twelveRequestsWithTheSameKeyAtTheSameMoment_makeOneRowAndOneLink() throws Exception {
        int requests = 12;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<PaymentService.Result>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return service.create("7", "race", new CreatePaymentRequest("ORD-1", 20000, null));
            }));
        }
        go.countDown();
        Set<UUID> ids = new HashSet<>();
        long created = 0;
        for (Future<PaymentService.Result> result : results) {
            ids.add(result.get().payment().getId());
            created += result.get().created() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(repo.count()).isEqualTo(1);
        assertThat(ids).hasSize(1);
        assertThat(created).isEqualTo(1);
        verify(gateway, times(1)).createLink(any());
    }

    @Test
    void create_sameKeyForAnotherUser_isAnotherPayment() {
        Payment mine = service.create("7", "k1", REQUEST).payment();
        Payment theirs = service.create("8", "k1", REQUEST).payment();

        assertThat(theirs.getId()).isNotEqualTo(mine.getId());
        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    void create_gatewayFails_savesAFailedPaymentWithTheReason() {
        doThrow(new GatewayException("Razorpay did not answer: timed out")).when(gateway).createLink(any());

        assertThatThrownBy(() -> service.create("7", "k1", REQUEST)).isInstanceOf(GatewayException.class);

        Payment saved = repo.findByUserIdAndIdempotencyKey("7", "k1").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("did not answer");
        assertThat(saved.getPaymentUrl()).isNull();
    }

    @Test
    void get_ownerSeesIt_anotherUserDoesNot() {
        Payment payment = service.create("7", "k1", REQUEST).payment();

        assertThat(service.get("7", payment.getId()).getId()).isEqualTo(payment.getId());
        assertThatThrownBy(() -> service.get("8", payment.getId())).hasMessageContaining("404");
    }
}
