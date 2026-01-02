package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.domain.flight.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockFlightRepository extends JpaRepository<Flight, Long> {
}
