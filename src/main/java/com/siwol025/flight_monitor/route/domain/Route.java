package com.siwol025.flight_monitor.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구독 대상인 노선(편도, 출발 공항·도착 공항·출발일·좌석등급). 같은 노선의 구독자는 이 한 행을 공유한다.
 */
@Entity
@Table(name = "routes", uniqueConstraints = @UniqueConstraint(name = "uk_routes_key",
        columnNames = {"departure_airport", "arrival_airport", "departure_date", "seat_grade"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "departure_airport", nullable = false, length = 3)
    private String departureAirport;

    @Column(name = "arrival_airport", nullable = false, length = 3)
    private String arrivalAirport;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_grade", nullable = false)
    private SeatGrade seatGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RouteStatus status;

    public Route(RouteKey key) {
        this.departureAirport = key.departureAirport();
        this.arrivalAirport = key.arrivalAirport();
        this.departureDate = key.departureDate();
        this.seatGrade = key.seatGrade();
        this.status = RouteStatus.ACTIVE;
    }

    // 지난 출발일의 노선도 키로 바꿀 수 있어야 하므로 과거 출발일 검사가 없는 정식 생성자를 쓴다
    public RouteKey toKey() {
        return new RouteKey(departureAirport, arrivalAirport, departureDate, seatGrade);
    }

    public void markInvalid() {
        this.status = RouteStatus.INVALID;
    }
}
