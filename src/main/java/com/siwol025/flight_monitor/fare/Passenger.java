package com.siwol025.flight_monitor.fare;

import java.time.LocalDate;

/**
 * 주문할 성인 승객 1명. 로그인 사용자와 별개로 받는다. phoneNumber는 E.164 형식이다(예: +821012345678).
 */
public record Passenger(
        PassengerTitle title,
        String givenName,
        String familyName,
        Gender gender,
        LocalDate bornOn,
        String email,
        String phoneNumber
) {
}
