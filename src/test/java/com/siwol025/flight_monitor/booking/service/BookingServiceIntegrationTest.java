package com.siwol025.flight_monitor.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.booking.dto.request.BookingRequest;
import com.siwol025.flight_monitor.booking.dto.request.PassengerRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingResponse;
import com.siwol025.flight_monitor.fare.FareProviderUnavailableException;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.FlightOfferPriceChangedException;
import com.siwol025.flight_monitor.fare.FlightOfferUnavailableException;
import com.siwol025.flight_monitor.fare.FlightOrderPlacer;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.fare.PlacedOrder;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.HttpStatusException;
import com.siwol025.flight_monitor.global.exception.custom.OfferPriceChangedException;
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
 * 구매 흐름(재조회 → 확인 → 주문 → 저장). 업체 포트는 HTTP 경계의 대리라 목으로 두고, 저장 결과는 내 구매 내역으로 본다.
 */
@DataJpaTest
@Import({BookingService.class, BookingServiceIntegrationTest.FixedClockConfig.class})
class BookingServiceIntegrationTest {

    private static final String OFFER_ID = "off_mock_1";
    private static final FlightOffer OFFER = new FlightOffer(OFFER_ID, new BigDecimal("250000.00"), "USD", "ICN", "NRT",
            SeatGrade.ECONOMY, "ZZ", "Duffel Airways", "1001",
            LocalDateTime.of(2026, 11, 26, 9, 0), LocalDateTime.of(2026, 11, 26, 11, 20));

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

    @MockitoBean
    private FlightOrderPlacer flightOrderPlacer;

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
    void book_예매가능_금액일치_주문후구매내역저장() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));
        given(flightOrderPlacer.placeOrder(OFFER_ID, new BigDecimal("250000.00"), "USD", PASSENGER))
                .willReturn(new PlacedOrder("ord_mock_7", "000007", new BigDecimal("250000.00"), "USD"));

        // 금액 표기가 달라도(250000 vs 250000.00) 같은 금액이다
        bookingService.book(user, request("250000", "USD", PASSENGER_REQUEST));

        // 주문에는 재조회한 offer의 금액(업체 표기)을 그대로 보낸다
        verify(flightOrderPlacer).placeOrder(OFFER_ID, new BigDecimal("250000.00"), "USD", PASSENGER);
        assertThat(bookingService.getMyBookings(user))
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("bookingId", "createdAt")
                .containsExactly(new BookingResponse(null, "ord_mock_7", "000007", "ICN", "NRT",
                        LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY, "ZZ", "Duffel Airways", "1001",
                        LocalDateTime.of(2026, 11, 26, 9, 0), LocalDateTime.of(2026, 11, 26, 11, 20),
                        new BigDecimal("250000.00"), "USD", "Gildong Hong", null));
    }

    @Test
    void book_재조회매진_409_주문안함() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.book(user, request("250000.00", "USD", PASSENGER_REQUEST)))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getErrorTag()).isEqualTo(ErrorTag.OFFER_UNAVAILABLE);
                });
        verify(flightOrderPlacer, never()).placeOrder(any(), any(), any(), any());
    }

    @Test
    void book_가격변동_409_현재가포함_주문안함() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));

        assertThatThrownBy(() -> bookingService.book(user, request("240000.00", "USD", PASSENGER_REQUEST)))
                .isInstanceOfSatisfying(OfferPriceChangedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getErrorTag()).isEqualTo(ErrorTag.OFFER_PRICE_CHANGED);
                    assertThat(e.getCurrentAmount()).isEqualByComparingTo("250000.00");
                    assertThat(e.getCurrentCurrency()).isEqualTo("USD");
                });
        verify(flightOrderPlacer, never()).placeOrder(any(), any(), any(), any());
    }

    @Test
    void book_통화불일치_409() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));

        assertThatThrownBy(() -> bookingService.book(user, request("250000.00", "KRW", PASSENGER_REQUEST)))
                .isInstanceOfSatisfying(OfferPriceChangedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCurrentCurrency()).isEqualTo("USD");
                });
        verify(flightOrderPlacer, never()).placeOrder(any(), any(), any(), any());
    }

    @Test
    void book_주문단계매진_409_저장안함() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));
        given(flightOrderPlacer.placeOrder(any(), any(), any(), any()))
                .willThrow(new FlightOfferUnavailableException("재조회 뒤 매진"));

        assertThatThrownBy(() -> bookingService.book(user, request("250000.00", "USD", PASSENGER_REQUEST)))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getErrorTag()).isEqualTo(ErrorTag.OFFER_UNAVAILABLE);
                });
        assertThat(bookingService.getMyBookings(user)).isEmpty();
    }

    @Test
    void book_업체장애_503_저장안함() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));
        given(flightOrderPlacer.placeOrder(any(), any(), any(), any()))
                .willThrow(new FareProviderUnavailableException("Duffel 응답 없음(타임아웃·연결 실패)"));

        assertThatThrownBy(() -> bookingService.book(user, request("250000.00", "USD", PASSENGER_REQUEST)))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getErrorTag()).isEqualTo(ErrorTag.FARE_PROVIDER_UNAVAILABLE);
                });
        assertThat(bookingService.getMyBookings(user)).isEmpty();
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
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));
        given(flightOrderPlacer.placeOrder(any(), any(), any(), any())).willReturn(
                new PlacedOrder("ord_mine_1", "000001", new BigDecimal("250000.00"), "USD"),
                new PlacedOrder("ord_other", "000002", new BigDecimal("250000.00"), "USD"),
                new PlacedOrder("ord_mine_2", "000003", new BigDecimal("250000.00"), "USD"));

        bookingService.book(user, request("250000.00", "USD", PASSENGER_REQUEST));
        bookingService.book(other, request("250000.00", "USD", PASSENGER_REQUEST));
        bookingService.book(user, request("250000.00", "USD", PASSENGER_REQUEST));

        assertThat(bookingService.getMyBookings(user)).extracting(BookingResponse::orderId)
                .containsExactly("ord_mine_2", "ord_mine_1");
    }

    @Test
    void book_미성년_400_주문안함() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));
        given(flightOrderPlacer.placeOrder(any(), any(), any(), any()))
                .willReturn(new PlacedOrder("ord_adult", "000001", new BigDecimal("250000.00"), "USD"));

        // 출발일(2026-11-26)에 하루 모자라 만 17세
        assertThatThrownBy(() -> bookingService.book(user,
                request("250000.00", "USD", passengerBornOn(LocalDate.of(2008, 11, 27)))))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getErrorTag()).isEqualTo(ErrorTag.PASSENGER_NOT_ADULT);
                });
        verify(flightOrderPlacer, never()).placeOrder(any(), any(), any(), any());

        // 출발일이 만 18세 생일이면 성인
        bookingService.book(user, request("250000.00", "USD", passengerBornOn(LocalDate.of(2008, 11, 26))));
        assertThat(bookingService.getMyBookings(user)).extracting(BookingResponse::orderId).containsExactly("ord_adult");
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
    void book_주문단계가격변동_409_금액없음() {
        given(flightOfferFinder.findOffer(OFFER_ID)).willReturn(Optional.of(OFFER));
        given(flightOrderPlacer.placeOrder(any(), any(), any(), any()))
                .willThrow(new FlightOfferPriceChangedException("재조회 뒤 가격 변동"));

        // 재조회 뒤 바뀐 현재가는 모르므로 비워 둔다(업체를 다시 부르지 않는다)
        assertThatThrownBy(() -> bookingService.book(user, request("250000.00", "USD", PASSENGER_REQUEST)))
                .isInstanceOfSatisfying(OfferPriceChangedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCurrentAmount()).isNull();
                    assertThat(e.getCurrentCurrency()).isNull();
                });
        verify(flightOfferFinder, times(1)).findOffer(OFFER_ID);
        assertThat(bookingService.getMyBookings(user)).isEmpty();
    }

    private static PassengerRequest passengerBornOn(LocalDate bornOn) {
        return new PassengerRequest("mr", "Gildong", "Hong", "m", bornOn, "gildong@test.com", "+821012345678");
    }

    private static final PassengerRequest PASSENGER_REQUEST = new PassengerRequest("mr", "Gildong", "Hong", "m",
            LocalDate.of(1990, 1, 1), "gildong@test.com", "+821012345678");

    private static final Passenger PASSENGER = new Passenger(PassengerTitle.MR, "Gildong", "Hong", Gender.M,
            LocalDate.of(1990, 1, 1), "gildong@test.com", "+821012345678");

    private static BookingRequest request(String amount, String currency, PassengerRequest passenger) {
        return new BookingRequest(OFFER_ID, new BigDecimal(amount), currency, passenger);
    }
}
