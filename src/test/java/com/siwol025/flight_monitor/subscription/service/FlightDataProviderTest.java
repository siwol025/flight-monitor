package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

class FlightDataProviderTest {

    @Test
    void Fake구현체_주입시_fetchMockFlight_기대한_응답반환() {
        MockFlightResponse expected = MockFlightResponse.builder()
                .id(1L)
                .flightNumber("KE101")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .isSeatAvailable(true)
                .seatPrices(List.of())
                .build();

        FlightDataProvider provider = flightId -> expected;

        MockFlightResponse actual = provider.fetchMockFlight(1L);

        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.flightNumber()).isEqualTo("KE101");
    }

    @Test
    void FlightFetcher는_FlightDataProvider를_구현해야한다() {
        assertThat(FlightDataProvider.class)
                .isAssignableFrom(FlightFetcher.class);
    }
}
