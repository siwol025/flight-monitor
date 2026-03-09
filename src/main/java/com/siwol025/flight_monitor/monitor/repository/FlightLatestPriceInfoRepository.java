package com.siwol025.flight_monitor.monitor.repository;

import com.siwol025.flight_monitor.monitor.domain.FlightLatestPriceInfo;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlightLatestPriceInfoRepository extends JpaRepository<FlightLatestPriceInfo, Long> {

    @Query(
            "SELECT p " +
            "FROM FlightLatestPriceInfo p " +
            "JOIN p.flight f " +
            "WHERE f.flightId = :flightId " +
            "AND p.seatGrade = :seatGrade"
    )
    Optional<FlightLatestPriceInfo> findByFlightIdAndSeatGrade(Long flightId, SeatGrade seatGrade);
}
