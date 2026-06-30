package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class FlightDataProviderTest {

    @Mock
    RestTemplate restTemplate;

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
    void FlightFetcher를_FlightDataProvider_타입으로_업캐스팅해도_fetchMockFlight_정상동작() {
        MockFlightResponse expectedResponse = MockFlightResponse.builder()
                .id(1L)
                .flightNumber("KE101")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .isSeatAvailable(true)
                .build();

        given(restTemplate.getForObject(anyString(), eq(MockFlightResponse.class)))
                .willReturn(expectedResponse);

        FlightDataProvider provider = new FlightFetcher(restTemplate, "http://mock-api/flights/");

        MockFlightResponse actual = provider.fetchMockFlight(1L);

        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.flightNumber()).isEqualTo("KE101");
    }
}
