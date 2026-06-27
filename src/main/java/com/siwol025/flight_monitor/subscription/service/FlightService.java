package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightService {

    private final FlightRepository flightRepository;

    @Transactional
    public Flight findOrCreateFlight(MockFlightResponse response) {
        return flightRepository.findByFlightNumberAndDepartureTime(
                        response.flightNumber(), response.departureTime())
                .orElseGet(() -> flightRepository.save(
                        Flight.builder()
                                .flightId(response.id())
                                .flightNumber(response.flightNumber())
                                .airlineCode(response.airlineCode())
                                .departureAirport(response.departureAirportCode())
                                .arrivalAirport(response.arrivalAirportCode())
                                .departureTime(response.departureTime())
                                .arrivalTime(response.arrivalTime())
                                .build()
                ));
    }

    public Flight getFlight(Long flightId) {
        return flightRepository.findByFlightId(flightId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.FLIGHT_NOT_FOUND));
    }
}
