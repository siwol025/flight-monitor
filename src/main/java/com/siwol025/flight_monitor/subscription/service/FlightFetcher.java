package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class FlightFetcher implements FlightDataProvider {

    private final RestTemplate restTemplate;
    private final String mockApiUrl;

    public FlightFetcher(RestTemplate restTemplate,
                         @Value("${external.api.url}") String mockApiUrl) {
        this.restTemplate = restTemplate;
        this.mockApiUrl = mockApiUrl;
    }

    @Override
    public MockFlightResponse fetchMockFlight(Long flightId) {
        String url = mockApiUrl + flightId;

        try {
            MockFlightResponse response = restTemplate.getForObject(url, MockFlightResponse.class);

            if (response == null) {
                throw new NotFoundException(ErrorTag.FLIGHT_NOT_FOUND);
            }
            return response;
        } catch (RestClientException e) {
            log.error("[FlightFetcher] 외부 API 조회 중 오류 발생", e);
            throw new RuntimeException("외부 항공 시스템 상세 조회 중 오류가 발생했습니다. ID: " + flightId, e);
        }
    }
}
