package com.scaler.paymentservice.exceptions;

import com.scaler.paymentservice.gateways.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    // Our service is fine. The gateway behind it said no, or did not answer.
    @ExceptionHandler(GatewayException.class)
    public ProblemDetail gatewayFailed(GatewayException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("The payment gateway could not make the link");
        return problem;
    }

    // A move that the status does not allow, for example PAID to PENDING.
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail illegalMove(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Not allowed for this payment");
        return problem;
    }
}
