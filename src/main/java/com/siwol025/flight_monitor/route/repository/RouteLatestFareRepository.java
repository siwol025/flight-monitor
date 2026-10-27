package com.siwol025.flight_monitor.route.repository;

import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RouteLatestFareRepository extends JpaRepository<RouteLatestFare, Long> {

    Optional<RouteLatestFare> findByRouteId(Long routeId);

    List<RouteLatestFare> findAllByRouteIdIn(Collection<Long> routeIds);
}
