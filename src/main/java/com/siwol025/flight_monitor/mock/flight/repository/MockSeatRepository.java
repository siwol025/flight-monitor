package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.domain.flight.Seat;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MockSeatRepository extends JpaRepository<Seat, Long> {
    @Query("SELECT distinct s FROM Seat s "
            + "JOIN FETCH s.flight "
            + "WHERE s.flight.id = :flightId "
            + "ORDER BY s.seatNumber")
    List<Seat> findSeatsByFlightIdOrderBySeatNumber(Long flightId);

    @Query("SELECT distinct s FROM Seat s "
            + "JOIN FETCH s.flight "
            + "WHERE s.flight.id = :flightId "
            + "AND s.seatGrade = :seatGrade "
            + "ORDER BY s.seatNumber")
    List<Seat> findSeatsByFlightIdAndSeatGrade(Long flightId, SeatGrade seatGrade);
}
