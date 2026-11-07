package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.auth.annotation.LoginUser;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.response.AdminBookingResponse;
import com.siwol025.flight_monitor.booking.service.BookingService;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.HttpStatusException;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자용 예매 조회(읽기 전용). 결과를 모르는 채 보류된 예매를 상태로 찾아본다. 재결제·상태 변경 API는 두지 않는다 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<List<AdminBookingResponse>> getBookings(
            @Parameter(hidden = true) @LoginUser User user,
            @RequestParam BookingStatus status
    ) {
        if (user.getRole() != Role.ADMIN) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, ErrorTag.ADMIN_ONLY);
        }
        return ResponseEntity.ok(bookingService.getBookingsByStatus(status));
    }
}
