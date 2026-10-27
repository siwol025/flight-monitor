package com.siwol025.flight_monitor.subscription.dto.response;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionResponseTest {

    private Route route;
    private final LocalDate departureDate = LocalDate.of(2026, 8, 1);

    @BeforeEach
    void setUp() {
        route = new Route(new RouteKey("ICN", "NRT", departureDate, SeatGrade.ECONOMY));
    }

    @Test
    void of_ACTIVE구독_모든필드_정상매핑됨() {
        Subscription subscription = Subscription.builder()
                .route(route)
                .price(BigDecimal.valueOf(150_000))
                .targetPrice(BigDecimal.valueOf(120_000))
                .dropThresholdPercent(BigDecimal.valueOf(10.00))
                .build();
        RouteLatestFare latestFare = RouteLatestFare.builder()
                .route(route)
                .amount(BigDecimal.valueOf(140_000))
                .currency("KRW")
                .carrierCode("KE")
                .carrierName("Korean Air")
                .flightNumber("123")
                .departingAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .arrivingAt(LocalDateTime.of(2026, 8, 1, 12, 0))
                .observedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();

        SubscriptionResponse response = SubscriptionResponse.of(subscription, latestFare);

        assertThat(response.departureAirportCode()).isEqualTo("ICN");
        assertThat(response.arrivalAirportCode()).isEqualTo("NRT");
        assertThat(response.departureDate()).isEqualTo(departureDate);
        assertThat(response.seatGrade()).isEqualTo(SeatGrade.ECONOMY);
        assertThat(response.subscribedPrice()).isEqualByComparingTo(BigDecimal.valueOf(150_000));
        assertThat(response.latestPrice()).isEqualByComparingTo(BigDecimal.valueOf(140_000));
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.fareStatus()).isEqualTo(FareStatus.MEASURED);
        assertThat(response.targetPrice()).isEqualByComparingTo(BigDecimal.valueOf(120_000));
        assertThat(response.dropThresholdPercent()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void of_EXPIRED구독_status_EXPIRED_매핑됨() {
        Subscription subscription = Subscription.builder()
                .route(route)
                .price(BigDecimal.valueOf(150_000))
                .build();
        subscription.updateStatus(SubscriptionStatus.EXPIRED);

        SubscriptionResponse response = SubscriptionResponse.of(subscription, null);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void of_targetPrice_null이면_null_매핑됨() {
        Subscription subscription = Subscription.builder()
                .route(route)
                .price(BigDecimal.valueOf(150_000))
                .targetPrice(null)
                .build();

        SubscriptionResponse response = SubscriptionResponse.of(subscription, null);

        assertThat(response.targetPrice()).isNull();
    }
}
