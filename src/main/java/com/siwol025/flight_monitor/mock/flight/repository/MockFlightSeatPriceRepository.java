package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.domain.flight.FlightSeatPrice;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.JpqlQueryBuilder.Select;
import org.springframework.stereotype.Repository;

@Repository
public interface MockFlightSeatPriceRepository extends JpaRepository<FlightSeatPrice, Long> {
    Optional<FlightSeatPrice> findByFlightIdAndSeatGrade(Long flightId, SeatGrade seatGrade);

    @Query("Select distinct f FROM FlightSeatPrice f " +
            "JOIN FETCH f.flight"
    )
    List<FlightSeatPrice> findAllWithFlightNumber();
}
