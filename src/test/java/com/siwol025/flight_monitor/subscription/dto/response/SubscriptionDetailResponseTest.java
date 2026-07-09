package com.siwol025.flight_monitor.subscription.dto.response;

import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionDetailResponseTest {

    private Flight flight;
    private final LocalDateTime departureTime = LocalDateTime.of(2026, 8, 1, 10, 0);
    private final LocalDateTime arrivalTime = LocalDateTime.of(2026, 8, 1, 12, 0);

    @BeforeEach
    void setUp() {
        flight = Flight.builder()
                .flightId(1L)
                .flightNumber("KE123")
                .airlineCode("KE")
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureTime(departureTime)
                .arrivalTime(arrivalTime)
                .build();
    }

    @Test
    void of_EXPIRED구독_status_EXPIRED_매핑됨() {
        Subscription subscription = Subscription.builder()
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150_000))
                .build();
        subscription.updateStatus(SubscriptionStatus.EXPIRED);
        BigDecimal currentPrice = BigDecimal.valueOf(130_000);

        SubscriptionDetailResponse response = SubscriptionDetailResponse.of(subscription, currentPrice);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void of_priceDifference_currentPrice에서_subscribedPrice_차감됨() {
        BigDecimal subscribedPrice = BigDecimal.valueOf(150_000);
        BigDecimal currentPrice = BigDecimal.valueOf(130_000);
        Subscription subscription = Subscription.builder()
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(subscribedPrice)
                .build();

        SubscriptionDetailResponse response = SubscriptionDetailResponse.of(subscription, currentPrice);

        BigDecimal expectedDifference = currentPrice.subtract(subscribedPrice); // -20_000
        assertThat(response.priceDifference()).isEqualByComparingTo(expectedDifference);
    }

    @Test
    void of_모든필드_정상매핑됨() {
        BigDecimal subscribedPrice = BigDecimal.valueOf(180_000);
        BigDecimal currentPrice = BigDecimal.valueOf(200_000);
        Subscription subscription = Subscription.builder()
                .flight(flight)
                .seatGrade(SeatGrade.BUSINESS)
                .price(subscribedPrice)
                .targetPrice(BigDecimal.valueOf(160_000))
                .dropThresholdPercent(BigDecimal.valueOf(15.00))
                .build();

        SubscriptionDetailResponse response = SubscriptionDetailResponse.of(subscription, currentPrice);

        assertThat(response.flightNumber()).isEqualTo("KE123");
        assertThat(response.airlineCode()).isEqualTo("KE");
        assertThat(response.departureAirportCode()).isEqualTo("ICN");
        assertThat(response.arrivalAirportCode()).isEqualTo("NRT");
        assertThat(response.departureTime()).isEqualTo(departureTime);
        assertThat(response.arrivalTime()).isEqualTo(arrivalTime);
        assertThat(response.seatGrade()).isEqualTo(SeatGrade.BUSINESS);
        assertThat(response.subscribedPrice()).isEqualByComparingTo(subscribedPrice);
        assertThat(response.currentPrice()).isEqualByComparingTo(currentPrice);
        assertThat(response.priceDifference()).isEqualByComparingTo(BigDecimal.valueOf(20_000));
        assertThat(response.targetPrice()).isEqualByComparingTo(BigDecimal.valueOf(160_000));
        assertThat(response.dropThresholdPercent()).isEqualByComparingTo(BigDecimal.valueOf(15.00));
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
