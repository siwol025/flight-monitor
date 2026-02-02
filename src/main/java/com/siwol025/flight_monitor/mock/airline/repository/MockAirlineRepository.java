package com.siwol025.flight_monitor.mock.airline.repository;

import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockAirlineRepository extends JpaRepository<MockAirline, Long> {

    Optional<MockAirline> findByAirlineCode(String code);
}
