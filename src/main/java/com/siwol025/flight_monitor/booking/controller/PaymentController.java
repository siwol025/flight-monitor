package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.booking.dto.request.PaymentConfirmRequest;
import com.siwol025.flight_monitor.booking.service.PaymentConfirmService;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController implements PaymentControllerSwagger {

    private final PaymentConfirmService paymentConfirmService;

    @PostMapping("/toss/confirm")
    public ResponseEntity<?> confirm(
            @Parameter(hidden = true) @LoginUser User user,
            @RequestHeader(value = IdempotentResponses.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return IdempotentResponses.toResponse(
                paymentConfirmService.confirm(user, IdempotentResponses.requireKey(idempotencyKey), request));
    }
}
