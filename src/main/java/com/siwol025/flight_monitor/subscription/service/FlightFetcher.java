package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class FlightFetcher {

    private final RestTemplate restTemplate;

    @Value("${external.api.url}")
    private String mockApiUrl;

    public MockFlightResponse fetchMockFlight(Long flightId) {
        String url = mockApiUrl + flightId;

        try {
            MockFlightResponse response = restTemplate.getForObject(url, MockFlightResponse.class);

            if (response == null) {
                throw new RuntimeException("해당 ID의 항공편 정보를 찾을 수 없습니다: " + flightId);
            }
            return response;
        } catch (RestClientException e) {
            throw new RuntimeException("외부 항공 시스템 상세 조회 중 오류가 발생했습니다. ID: " + flightId, e);
        }
    }
}
