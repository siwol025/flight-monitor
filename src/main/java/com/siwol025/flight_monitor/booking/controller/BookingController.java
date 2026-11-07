package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.booking.dto.request.BookingRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingResponse;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.booking.service.CheckoutService;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookings")
public class BookingController implements BookingControllerSwagger {

    private final BookingService bookingService;
    private final CheckoutService checkoutService;

    @PostMapping
    public ResponseEntity<?> book(
            @Parameter(hidden = true) @LoginUser User user,
            @RequestHeader(value = IdempotentResponses.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody BookingRequest request
    ) {
        return IdempotentResponses.toResponse(
                checkoutService.purchase(user, IdempotentResponses.requireKey(idempotencyKey), request));
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingResponse>> getMyBookings(@Parameter(hidden = true) @LoginUser User user) {
        return ResponseEntity.ok(bookingService.getMyBookings(user));
    }
}
