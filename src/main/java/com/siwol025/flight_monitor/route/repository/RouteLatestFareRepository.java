package com.siwol025.flight_monitor.route.repository;

import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RouteLatestFareRepository extends JpaRepository<RouteLatestFare, Long> {

    Optional<RouteLatestFare> findByRouteId(Long routeId);

    List<RouteLatestFare> findAllByRouteIdIn(Collection<Long> routeIds);

    /**
     * 최신가가 아직 기대한 금액·관측 시각 그대로일 때만 새 관측으로 바꾼다(그사이 다른 관측이 반영됐으면 0행).
     *
     * @return 바뀐 행 수(0 또는 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RouteLatestFare f SET f.amount = :amount, f.currency = :currency, f.carrierCode = :carrierCode, "
            + "f.carrierName = :carrierName, f.flightNumber = :flightNumber, f.departingAt = :departingAt, "
            + "f.arrivingAt = :arrivingAt, f.observedAt = :observedAt "
            + "WHERE f.route.id = :routeId AND f.amount = :expectedAmount AND f.observedAt = :expectedObservedAt")
    int updateIfUnchanged(@Param("routeId") Long routeId,
                          @Param("expectedAmount") BigDecimal expectedAmount,
                          @Param("expectedObservedAt") Instant expectedObservedAt,
                          @Param("amount") BigDecimal amount,
                          @Param("currency") String currency,
                          @Param("carrierCode") String carrierCode,
                          @Param("carrierName") String carrierName,
                          @Param("flightNumber") String flightNumber,
                          @Param("departingAt") LocalDateTime departingAt,
                          @Param("arrivingAt") LocalDateTime arrivingAt,
                          @Param("observedAt") Instant observedAt);
}
