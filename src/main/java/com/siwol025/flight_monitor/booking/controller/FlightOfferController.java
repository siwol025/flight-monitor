package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.booking.dto.request.FlightOfferSearchRequest;
import com.siwol025.flight_monitor.booking.dto.response.FlightOfferResponse;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인 사용자만 쓴다(사용자 값은 쓰지 않고 인증만 확인한다)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flights/offers")
public class FlightOfferController implements FlightOfferControllerSwagger {

    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<List<FlightOfferResponse>> searchOffers(
            @Parameter(hidden = true) @LoginUser User user,
            @Valid @ModelAttribute FlightOfferSearchRequest request
    ) {
        return ResponseEntity.ok(bookingService.searchOffers(request.departureAirport(), request.arrivalAirport(),
                request.departureDate(), request.seatGrade()));
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<FlightOfferResponse> getOffer(
            @Parameter(hidden = true) @LoginUser User user,
            @PathVariable String offerId
    ) {
        return ResponseEntity.ok(bookingService.getOffer(offerId));
    }
}
