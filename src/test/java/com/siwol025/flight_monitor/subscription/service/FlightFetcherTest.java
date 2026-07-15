package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class FlightFetcherTest {

    private static final String BASE_URL = "http://mock-api/flights/";

    @Mock
    private RestTemplate restTemplate;

    private SimpleMeterRegistry meterRegistry;
    private PipelineMetrics pipelineMetrics;
    private FlightFetcher flightFetcher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        pipelineMetrics = new PipelineMetrics(meterRegistry);
        flightFetcher = new FlightFetcher(restTemplate, BASE_URL, pipelineMetrics);
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

    @Test
    void 생성자파라미터로_mockApiUrl을_전달하면_fetchMockFlight가_올바른_URL로_호출된다() {
        Long flightId = 42L;
        MockFlightResponse expected = MockFlightResponse.builder()
                .id(flightId)
                .flightNumber("KE101")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .isSeatAvailable(true)
                .build();

        given(restTemplate.getForObject(BASE_URL + flightId, MockFlightResponse.class))
                .willReturn(expected);

        MockFlightResponse result = flightFetcher.fetchMockFlight(flightId);

        assertThat(result.id()).isEqualTo(flightId);
        assertThat(result.flightNumber()).isEqualTo("KE101");
        verify(restTemplate).getForObject(BASE_URL + flightId, MockFlightResponse.class);
    }

    @Test
    void fetchMockFlight_정상호출시_외부API지연타이머_1회기록() {
        Long flightId = 42L;
        MockFlightResponse expected = MockFlightResponse.builder()
                .id(flightId)
                .flightNumber("KE101")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .isSeatAvailable(true)
                .build();

        given(restTemplate.getForObject(BASE_URL + flightId, MockFlightResponse.class))
                .willReturn(expected);

        flightFetcher.fetchMockFlight(flightId);

        assertThat(meterRegistry.get(PipelineMetrics.METRIC_EXTERNAL_API_LATENCY).timer().count())
                .isEqualTo(1L);
    }

    @Test
    void fetchMockFlight_RestClientException발생해도_외부API지연타이머_1회기록() {
        given(restTemplate.getForObject(anyString(), eq(MockFlightResponse.class)))
                .willThrow(new RestClientException("Connection timeout"));

        assertThrows(
                RuntimeException.class,
                () -> flightFetcher.fetchMockFlight(1L)
        );

        assertThat(meterRegistry.get(PipelineMetrics.METRIC_EXTERNAL_API_LATENCY).timer().count())
                .isEqualTo(1L);
    }
}
