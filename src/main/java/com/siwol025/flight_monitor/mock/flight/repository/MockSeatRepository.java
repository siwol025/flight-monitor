package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.domain.flight.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MockSeatRepository extends JpaRepository<Seat, Long> {
    @Query("select s from Seat s join fetch s.flight where s.flight.id = :flightId order by s.seatNumber")
    List<Seat> findByFlightIdOrderBySeatNumber(@Param("flightId") Long flightId);
}
