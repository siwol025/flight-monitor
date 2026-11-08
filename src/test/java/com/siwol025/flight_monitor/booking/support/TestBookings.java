package com.siwol025.flight_monitor.booking.support;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.booking.service.BookingStateWriter;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.OfferTerms;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 예매 흐름 테스트가 쓰는 예매 행. 실측 ZZ hold 주문(s03b_h1_hold_order.json, 31.18 USD)을 가리키는 예매를 원하는 상태와
 * 상태 시각(DB NOW(6) 기준 몇 초 전)으로 만든다.
 */
public final class TestBookings {

    public static final String ORDER_ID = "ord_0000BC7e5XO0QzobMUjokT";
    public static final String ORDER_PATH = "/air/orders/" + ORDER_ID;
    public static final long CHARGE_AMOUNT = 43652;

    private TestBookings() {
    }

    public static User newUser(UserRepository userRepository, Role role) {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.builder().email(unique + "@test.com").name("예매자")
                .provider(Provider.GOOGLE).providerId(unique).role(role).build());
    }

    /** ④를 마친 HELD 예매(Duffel 주문 ID·고객 결제 금액·Toss 주문번호·Toss 멱등키 고정) */
    public static long held(BookingRepository bookingRepository, BookingStateWriter writer, User owner,
                            String offerId) {
        long id = bookingRepository.save(Booking.holdRequested(owner, offer(offerId), passenger())).getId();
        bookingRepository.stampStatusChangedAt(id);
        writer.hold(id, ORDER_ID, "VNMYQL", Instant.parse("2026-11-08T01:10:25Z"), CHARGE_AMOUNT,
                "fm-" + UUID.randomUUID(), UUID.randomUUID().toString());
        return id;
    }

    /** 예매 행을 이 상태로, 상태 시각을 DB 시각 기준 secondsAgo초 전으로 바꾼다 */
    public static void moveTo(JdbcTemplate jdbc, long bookingId, BookingStatus status, long secondsAgo) {
        jdbc.update("UPDATE bookings SET status = ?, status_changed_at = NOW(6) - INTERVAL ? SECOND WHERE id = ?",
                status.name(), secondsAgo, bookingId);
    }

    public static FlightOffer offer(String offerId) {
        return new FlightOffer(offerId, new BigDecimal("31.18"), "USD", "JFK", "EWR", SeatGrade.ECONOMY, "ZZ",
                "Duffel Airways", "1234", LocalDateTime.of(2026, 12, 3, 9, 0), LocalDateTime.of(2026, 12, 3, 10, 0),
                new OfferTerms(List.of("pas_0000BC7e5QuMWIXPGop3iK"), false, Instant.parse("2026-12-01T00:00:00Z"),
                        "ZZ"));
    }

    public static Passenger passenger() {
        return new Passenger(PassengerTitle.MR, "Tony", "Stark", Gender.M, LocalDate.of(1980, 7, 24),
                "tony@example.com", "+442080160508");
    }
}
