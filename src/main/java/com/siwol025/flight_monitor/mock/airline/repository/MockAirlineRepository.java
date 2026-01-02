package com.siwol025.flight_monitor.mock.airline.repository;

import com.siwol025.flight_monitor.domain.airline.Airline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockAirlineRepository extends JpaRepository<Airline, Long> {
}
