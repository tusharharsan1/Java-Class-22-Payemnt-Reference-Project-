package com.scaler.paymentservice.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scaler.paymentservice.config.SecurityConfig;
import com.scaler.paymentservice.dtos.CreatePaymentRequest;
import com.scaler.paymentservice.exceptions.ApiExceptionHandler;
import com.scaler.paymentservice.gateways.GatewayException;
import com.scaler.paymentservice.models.Payment;
import com.scaler.paymentservice.models.PaymentStatus;
import com.scaler.paymentservice.services.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class PaymentControllerTest {

    private static final String BODY = "{\"orderRef\":\"ORD-1\",\"amount\":49900,\"description\":\"Red Lipstick\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PaymentService service;

    private Payment pending() {
        Payment payment = new Payment();
        payment.setId(UUID.fromString("01a1e532-11f3-7b70-8d2a-6457202d8b8f"));
        payment.setOrderRef("ORD-1");
        payment.setAmount(49900);
        payment.setCurrency("INR");
        payment.setGatewayReference("plink_1");
        payment.setPaymentUrl("https://rzp.io/i/xyz");
        payment.moveTo(PaymentStatus.PENDING);
        return payment;
    }

    @Test
    void post_withoutToken_returns401_andTheServiceIsNeverCalled() throws Exception {
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "k1")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void post_newPayment_returns201WithTheLink_andTheUserComesFromTheToken() throws Exception {
        when(service.create(eq("7"), eq("k1"), any())).thenReturn(new PaymentService.Result(pending(), true));

        mockMvc.perform(post("/payments").with(jwt().jwt(j -> j.subject("7"))).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1").content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(49900))
                .andExpect(jsonPath("$.paymentUrl").value("https://rzp.io/i/xyz"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

        verify(service).create(eq("7"), eq("k1"), eq(new CreatePaymentRequest("ORD-1", 49900, "Red Lipstick")));
    }

    @Test
    void post_sameKeyAgain_returns200() throws Exception {
        when(service.create(any(), any(), any())).thenReturn(new PaymentService.Result(pending(), false));

        mockMvc.perform(post("/payments").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1").content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void post_amountBelow100Paise_returns400() throws Exception {
        mockMvc.perform(post("/payments").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1").content("{\"orderRef\":\"ORD-1\",\"amount\":50}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void post_withoutTheIdempotencyKeyHeader_returns400() throws Exception {
        mockMvc.perform(post("/payments").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void post_gatewayFails_returns502WithTheReason() throws Exception {
        when(service.create(any(), any(), any())).thenThrow(new GatewayException("Razorpay answered 400: bad"));

        mockMvc.perform(post("/payments").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k1").content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Razorpay answered 400: bad"));
    }

    @Test
    void get_aPaymentOfSomeoneElse_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get("8", id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No such payment"));

        mockMvc.perform(get("/payments/" + id).with(jwt().jwt(j -> j.subject("8"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_illegalMove_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(any(), eq(id))).thenThrow(new IllegalStateException("A payment cannot move from PAID to PENDING"));

        mockMvc.perform(get("/payments/" + id).with(jwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/payments/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void callback_isOpenToTheBrowser_andNeverTouchesAStatus() throws Exception {
        mockMvc.perform(get("/payments/callback?razorpay_payment_link_status=paid&razorpay_payment_link_reference_id=x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(service);
    }
}
