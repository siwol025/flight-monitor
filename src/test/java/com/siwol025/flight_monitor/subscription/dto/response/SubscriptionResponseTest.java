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

class SubscriptionResponseTest {

    private Flight flight;
    private final LocalDateTime departureTime = LocalDateTime.of(2026, 8, 1, 10, 0);

    @BeforeEach
    void setUp() {
        flight = Flight.builder()
                .flightId(1L)
                .flightNumber("KE123")
                .airlineCode("KE")
                .departureAirport("ICN")
                .arrivalAirport("NRT")
                .departureTime(departureTime)
                .arrivalTime(departureTime.plusHours(2))
                .build();
    }

    @Test
    void from_ACTIVE구독_모든필드_정상매핑됨() {
        Subscription subscription = Subscription.builder()
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150_000))
                .targetPrice(BigDecimal.valueOf(120_000))
                .dropThresholdPercent(BigDecimal.valueOf(10.00))
                .build();

        SubscriptionResponse response = SubscriptionResponse.from(subscription);

        assertThat(response.flightNumber()).isEqualTo("KE123");
        assertThat(response.airlineCode()).isEqualTo("KE");
        assertThat(response.departureAirportCode()).isEqualTo("ICN");
        assertThat(response.arrivalAirportCode()).isEqualTo("NRT");
        assertThat(response.departureTime()).isEqualTo(departureTime);
        assertThat(response.seatGrade()).isEqualTo(SeatGrade.ECONOMY);
        assertThat(response.subscribedPrice()).isEqualByComparingTo(BigDecimal.valueOf(150_000));
        assertThat(response.targetPrice()).isEqualByComparingTo(BigDecimal.valueOf(120_000));
        assertThat(response.dropThresholdPercent()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void from_EXPIRED구독_status_EXPIRED_매핑됨() {
        Subscription subscription = Subscription.builder()
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150_000))
                .build();
        subscription.updateStatus(SubscriptionStatus.EXPIRED);

        SubscriptionResponse response = SubscriptionResponse.from(subscription);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void from_targetPrice_null이면_null_매핑됨() {
        Subscription subscription = Subscription.builder()
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(BigDecimal.valueOf(150_000))
                .targetPrice(null)
                .build();

        SubscriptionResponse response = SubscriptionResponse.from(subscription);

        assertThat(response.targetPrice()).isNull();
    }
}
