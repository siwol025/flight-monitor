package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;

@FunctionalInterface
public interface FlightDataProvider {

    MockFlightResponse fetchMockFlight(Long flightId);
}
