package com.siwol025.flight_monitor.route.domain;

import java.time.LocalDate;

/**
 * 노선 키: 출발 공항, 도착 공항, 출발일, 좌석등급. 외부 운임 조회의 입력이다(편도, 검색 조건 없음).
 */
public record RouteKey(String departureAirport, String arrivalAirport, LocalDate departureDate, SeatGrade seatGrade) {
}
