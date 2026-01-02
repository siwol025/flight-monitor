package com.siwol025.flight_monitor.mock.airport.repository;

import com.siwol025.flight_monitor.domain.airport.Airport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockAirportRepository extends JpaRepository<Airport, Long> {
    Optional<Airport> findByAirportCode(String AirportCode);
}
