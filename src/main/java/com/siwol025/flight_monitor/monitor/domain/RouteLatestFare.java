package com.siwol025.flight_monitor.monitor.domain;

import com.siwol025.flight_monitor.route.domain.Route;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 노선별 최신 최저가. 이전 가격의 단일 저장소이자 구독 조회·알림에 쓰는 마지막 관측값이다(노선당 1행).
 */
@Entity
@Table(name = "route_latest_fares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteLatestFare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false, unique = true)
    private Route route;

    // scale을 비우면 생성 DDL이 scale 0이 되어 소수가 잘린다
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    // mock 항공사 코드 컬럼 길이(10)와 맞춘다. 실제 IATA 코드는 2자
    @Column(name = "carrier_code", nullable = false, length = 10)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false)
    private String carrierName;

    @Column(name = "flight_number", nullable = false, length = 20)
    private String flightNumber;

    // 업체가 준 현지 시각 그대로(오프셋 없음)
    @Column(name = "departing_at", nullable = false)
    private LocalDateTime departingAt;

    @Column(name = "arriving_at", nullable = false)
    private LocalDateTime arrivingAt;

    // 관측 시점은 시간대와 무관하다(dev는 KST, 컨테이너는 UTC여도 같은 순간을 뜻한다)
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Builder
    public RouteLatestFare(Route route, BigDecimal amount, String currency, String carrierCode, String carrierName,
                           String flightNumber, LocalDateTime departingAt, LocalDateTime arrivingAt,
                           Instant observedAt) {
        this.route = route;
        this.amount = amount;
        this.currency = currency;
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.flightNumber = flightNumber;
        this.departingAt = departingAt;
        this.arrivingAt = arrivingAt;
        this.observedAt = observedAt;
    }
}
