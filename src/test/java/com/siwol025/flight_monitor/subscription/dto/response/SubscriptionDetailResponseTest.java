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

class SubscriptionDetailResponseTest {

    private Route route;
    private final LocalDate departureDate = LocalDate.of(2026, 8, 1);
    private final LocalDateTime departingAt = LocalDateTime.of(2026, 8, 1, 10, 0);
    private final LocalDateTime arrivingAt = LocalDateTime.of(2026, 8, 1, 12, 0);

    @BeforeEach
    void setUp() {
        route = new Route(new RouteKey("ICN", "NRT", departureDate, SeatGrade.BUSINESS));
    }

    @Test
    void of_EXPIRED구독_status_EXPIRED_매핑됨() {
        Subscription subscription = Subscription.builder()
                .route(route)
                .price(BigDecimal.valueOf(150_000))
                .build();
        subscription.updateStatus(SubscriptionStatus.EXPIRED);

        SubscriptionDetailResponse response = SubscriptionDetailResponse.of(subscription, latestFare(BigDecimal.valueOf(130_000)));

        assertThat(response.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void of_priceDifference_currentPrice에서_subscribedPrice_차감됨() {
        BigDecimal subscribedPrice = BigDecimal.valueOf(150_000);
        BigDecimal currentPrice = BigDecimal.valueOf(130_000);
        Subscription subscription = Subscription.builder()
                .route(route)
                .price(subscribedPrice)
                .build();

        SubscriptionDetailResponse response = SubscriptionDetailResponse.of(subscription, latestFare(currentPrice));

        BigDecimal expectedDifference = currentPrice.subtract(subscribedPrice); // -20_000
        assertThat(response.priceDifference()).isEqualByComparingTo(expectedDifference);
    }

    @Test
    void of_모든필드_정상매핑됨() {
        BigDecimal subscribedPrice = BigDecimal.valueOf(180_000);
        BigDecimal currentPrice = BigDecimal.valueOf(200_000);
        Subscription subscription = Subscription.builder()
                .route(route)
                .price(subscribedPrice)
                .targetPrice(BigDecimal.valueOf(160_000))
                .dropThresholdPercent(BigDecimal.valueOf(15.00))
                .build();

        SubscriptionDetailResponse response = SubscriptionDetailResponse.of(subscription, latestFare(currentPrice));

        assertThat(response.departureAirportCode()).isEqualTo("ICN");
        assertThat(response.arrivalAirportCode()).isEqualTo("NRT");
        assertThat(response.departureDate()).isEqualTo(departureDate);
        assertThat(response.seatGrade()).isEqualTo(SeatGrade.BUSINESS);
        assertThat(response.carrierCode()).isEqualTo("KE");
        assertThat(response.carrierName()).isEqualTo("Korean Air");
        assertThat(response.flightNumber()).isEqualTo("123");
        assertThat(response.departingAt()).isEqualTo(departingAt);
        assertThat(response.arrivingAt()).isEqualTo(arrivingAt);
        assertThat(response.observedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(response.subscribedPrice()).isEqualByComparingTo(subscribedPrice);
        assertThat(response.latestPrice()).isEqualByComparingTo(currentPrice);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.priceDifference()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(response.fareStatus()).isEqualTo(FareStatus.MEASURED);
        assertThat(response.targetPrice()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(response.dropThresholdPercent()).isEqualByComparingTo(BigDecimal.valueOf(15.00));
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    private RouteLatestFare latestFare(BigDecimal amount) {
        return RouteLatestFare.builder()
                .route(route)
                .amount(amount)
                .currency("KRW")
                .carrierCode("KE")
                .carrierName("Korean Air")
                .flightNumber("123")
                .departingAt(departingAt)
                .arrivingAt(arrivingAt)
                .observedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
    }
}
