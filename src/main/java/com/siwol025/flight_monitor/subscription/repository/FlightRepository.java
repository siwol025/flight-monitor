package com.siwol025.flight_monitor.subscription.repository;

import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightId(Long flightId);

    Optional<Flight> findByFlightNumberAndDepartureTime(String flightNumber, LocalDateTime departureTime);
}
