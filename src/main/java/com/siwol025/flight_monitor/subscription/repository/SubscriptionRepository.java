package com.siwol025.flight_monitor.subscription.repository;

import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query(
            "SELECT s FROM Subscription s " +
            "JOIN FETCH s.flight " +
            "WHERE s.user.id = :userId " +
            "ORDER BY s.createdAt desc"
    )
    List<Subscription> findAllByUserId(Long userId);

    @Query(
            "SELECT s " +
            "FROM Subscription s " +
            "JOIN FETCH s.flight " +
            "WHERE s.id = :id"
    )
    Optional<Subscription> findByIdWithFlight(@Param("id") Long id);

    boolean existsByUserIdAndFlightIdAndSeatGrade(Long userId, Long flightId, SeatGrade seatGrade);

    @Query(
            "SELECT DISTINCT " +
                "new com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto(s.flight.flightId, s.seatGrade) " +
            "FROM Subscription s " +
            "JOIN s.flight f " +
            "WHERE f.departureTime > CURRENT_TIMESTAMP " +
            "AND s.status = :status"
    )
    List<FlightMonitorTaskDto> findActiveFlightIdsAndSeatGrade(SubscriptionStatus status);

    @Query(
            "SELECT DISTINCT " +
                "new com.siwol025.flight_monitor.user.dto.UserEmailDto(s.user.email) " +
            "FROM Subscription s " +
            "JOIN s.flight f " +
            "WHERE f.flightId = :flightId"
    )
    List<UserEmailDto> findSubscriberByFlightId(Long flightId);

    @Query(
            "SELECT DISTINCT " +
                "new com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto(s.user.email, s.targetPrice, s.dropThresholdPercent) " +
            "FROM Subscription s " +
            "JOIN s.flight f " +
            "WHERE f.flightId = :flightId " +
            "AND s.status = :status"
    )
    List<SubscriberWithConditionDto> findSubscribersWithConditionByFlightId(@Param("flightId") Long flightId, @Param("status") SubscriptionStatus status);

    @Query(
            "SELECT s " +
            "FROM Subscription s " +
            "JOIN FETCH s.flight f " +
            "WHERE s.status = :status " +
            "AND f.departureTime < CURRENT_TIMESTAMP"
    )
    List<Subscription> findExpirableSubscriptions(@Param("status") SubscriptionStatus status);
}
