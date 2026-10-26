package com.siwol025.flight_monitor.monitor.repository;

import com.siwol025.flight_monitor.monitor.domain.RouteLatestFare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RouteLatestFareRepository extends JpaRepository<RouteLatestFare, Long> {
}
