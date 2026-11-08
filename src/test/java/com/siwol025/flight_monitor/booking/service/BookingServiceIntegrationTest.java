package com.siwol025.flight_monitor.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.response.BookingDetailResponse;
import com.siwol025.flight_monitor.booking.dto.response.BookingResponse;
import com.siwol025.flight_monitor.booking.dto.response.FlightOfferResponse;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.OfferTerms;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.HttpStatusException;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 결제 직전 재조회와 내 구매 내역. 업체 포트는 HTTP 경계의 대리라 목으로 둔다. 구매(hold)는 CheckoutServiceTest가 본다.
 */
@DataJpaTest
@Import({BookingService.class, BookingPolicy.class, BookingServiceIntegrationTest.FixedClockConfig.class})
class BookingServiceIntegrationTest {

    private static final String OFFER_ID = "off_mock_1";
    private static final FlightOffer OFFER = offer(OFFER_ID,
            new OfferTerms(List.of("pas_1"), false, Instant.parse("2026-10-25T23:30:00Z"), "ZZ"));

    @TestConfiguration
    static class FixedClockConfig {
        // 한국 시각 2026-10-26 08:00
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @MockitoBean
    private FlightOfferFinder flightOfferFinder;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TestEntityManager em;

    private User user;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder()
                .email("booker@test.com")
                .name("예매자")
                .provider(Provider.GOOGLE)
                .providerId("google-booker")
                .role(Role.USER)
                .build());
    }

    @Test
    void getMyBookings_내것만_최근순() {
        User other = em.persist(User.builder()
                .email("other@test.com")
                .name("다른사용자")
                .provider(Provider.GOOGLE)
                .providerId("google-other")
                .role(Role.USER)
                .build());
        Booking mine1 = em.persist(Booking.holdRequested(user, OFFER, PASSENGER));
        em.persist(Booking.holdRequested(other, OFFER, PASSENGER));
        Booking mine2 = em.persist(Booking.holdRequested(user, OFFER, PASSENGER));

        assertThat(bookingService.getMyBookings(user)).extracting(BookingResponse::bookingId)
                .containsExactly(mine2.getId(), mine1.getId());
        assertThat(bookingService.getMyBookings(user)).extracting(BookingResponse::status)
                .containsOnly(BookingStatus.HOLD_REQUESTED);
    }

    @Test
    void detail_내예매_상태() {
        Booking mine = em.persist(Booking.holdRequested(user, OFFER, PASSENGER));

        BookingDetailResponse detail = bookingService.getBooking(user, mine.getId());

        assertThat(detail.bookingId()).isEqualTo(mine.getId());
        assertThat(detail.status()).isEqualTo(BookingStatus.HOLD_REQUESTED);
        assertThat(detail.payment()).isNull();
    }

    @Test
    void detail_발권된예매_항공권번호목록() {
        Booking mine = em.persist(Booking.holdRequested(user, OFFER, PASSENGER));
        em.getEntityManager().createNativeQuery(
                        "UPDATE bookings SET status = 'TICKETED', ticket_numbers = '0123456789,0987654321' WHERE id = ?")
                .setParameter(1, mine.getId())
                .executeUpdate();
        em.clear();

        BookingDetailResponse detail = bookingService.getBooking(user, mine.getId());

        assertThat(detail.status()).isEqualTo(BookingStatus.TICKETED);
        assertThat(detail.ticketNumbers()).containsExactly("0123456789", "0987654321");
    }

    @Test
    void detail_미발권예매_항공권번호없음() {
        Booking mine = em.persist(Booking.holdRequested(user, OFFER, PASSENGER));

        assertThat(bookingService.getBooking(user, mine.getId()).ticketNumbers()).isEmpty();
    }

    @Test
    void detail_남의예매_404() {
        User other = em.persist(User.builder()
                .email("other@test.com")
                .name("다른사용자")
                .provider(Provider.GOOGLE)
                .providerId("google-other")
                .role(Role.USER)
                .build());
        Booking others = em.persist(Booking.holdRequested(other, OFFER, PASSENGER));

        // 남의 예매는 있는지조차 알리지 않는다(없는 예매와 같은 404)
        for (long bookingId : new long[]{others.getId(), others.getId() + 1000}) {
            assertThatThrownBy(() -> bookingService.getBooking(user, bookingId))
                    .isInstanceOfSatisfying(HttpStatusException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(e.getErrorTag()).isEqualTo(ErrorTag.BOOKING_NOT_FOUND);
                    });
        }
    }

    @Test
    void getOffer_매진_409() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getOffer(OFFER_ID))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getErrorTag()).isEqualTo(ErrorTag.OFFER_UNAVAILABLE);
                });
    }

    @Test
    void getOffer_bookable과만료와원화예상() {
        Instant expiresAt = Instant.parse("2026-10-25T23:30:00Z");
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));
        given(flightOfferFinder.findOffer("off_instant")).willReturn(Optional.of(offer("off_instant",
                new OfferTerms(List.of("pas_1"), true, expiresAt, "ZZ"))));
        given(flightOfferFinder.findOffer("off_ke")).willReturn(Optional.of(offer("off_ke",
                new OfferTerms(List.of("pas_1"), false, expiresAt, "KE"))));

        FlightOfferResponse zz = bookingService.getOffer(OFFER_ID);
        // 76.04 USD × 1400 = 106,456원(원 단위 올림)
        assertThat(zz.bookable()).isTrue();
        assertThat(zz.expiresAt()).isEqualTo(expiresAt);
        assertThat(zz.estimatedChargeAmount()).isEqualTo(106_456L);
        // 결제를 미룰 수 없는 offer, 허용 외 항공사는 예매할 수 없다
        assertThat(bookingService.getOffer("off_instant").bookable()).isFalse();
        assertThat(bookingService.getOffer("off_ke").bookable()).isFalse();
    }

    private static FlightOffer offer(String offerId, OfferTerms terms) {
        return new FlightOffer(offerId, new BigDecimal("76.04"), "USD", "ICN", "NRT", SeatGrade.ECONOMY, "ZZ",
                "Duffel Airways", "1001", LocalDateTime.of(2026, 11, 26, 9, 0), LocalDateTime.of(2026, 11, 26, 11, 20),
                terms);
    }

    private static final Passenger PASSENGER = new Passenger(PassengerTitle.MR, "Gildong", "Hong", Gender.M,
            LocalDate.of(1990, 1, 1), "gildong@test.com", "+821012345678");
}
