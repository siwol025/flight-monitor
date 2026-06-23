package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class FlightFetcherTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FlightFetcher flightFetcher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(flightFetcher, "mockApiUrl", "http://mock-api/flights/");
    }

    @Test
    void fetchMockFlight_null응답이면_NotFoundException발생() {
        given(restTemplate.getForObject(anyString(), eq(MockFlightResponse.class)))
                .willReturn(null);

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> flightFetcher.fetchMockFlight(1L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.FLIGHT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void fetchMockFlight_RestClientException이면_RuntimeException발생() {
        given(restTemplate.getForObject(anyString(), eq(MockFlightResponse.class)))
                .willThrow(new RestClientException("Connection timeout"));

        assertThrows(
                RuntimeException.class,
                () -> flightFetcher.fetchMockFlight(1L)
        );
    }
}
