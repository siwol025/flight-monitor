package com.siwol025.flight_monitor.route.domain;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 노선 키: 출발 공항, 도착 공항, 출발일, 좌석등급. 외부 운임 조회의 입력이다(편도, 검색 조건 없음).
 * 공항 코드는 만드는 순간 정규화하므로 모든 경로가 같은 노선을 같은 키로 본다.
 */
public record RouteKey(String departureAirport, String arrivalAirport, LocalDate departureDate, SeatGrade seatGrade) {

    private static final Pattern AIRPORT_CODE = Pattern.compile("[A-Z]{3}");

    public RouteKey {
        departureAirport = airportCode(departureAirport);
        arrivalAirport = airportCode(arrivalAirport);
        // 그대로 두면 업체가 422로 거부할 때까지 호출 예산을 쓴다
        if (departureAirport.equals(arrivalAirport)) {
            throw new BadRequestException(ErrorTag.SAME_DEPARTURE_ARRIVAL_AIRPORT);
        }
    }

    /**
     * 새 입력(구독 등록) 전용 팩터리: 출발일이 오늘보다 앞서면 거부한다. 오늘은 clock의 시간대(Asia/Seoul) 기준 날짜다.
     * DB에 있는 노선은 날짜가 지났을 수 있으므로 키로 바꿀 때 정식 생성자를 쓴다.
     */
    public static RouteKey of(String departureAirport, String arrivalAirport, LocalDate departureDate,
                              SeatGrade seatGrade, Clock clock) {
        RouteKey key = new RouteKey(departureAirport, arrivalAirport, departureDate, seatGrade);
        if (departureDate.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException(ErrorTag.PAST_DEPARTURE_DATE);
        }
        return key;
    }

    // 정규화(trim → 대문자) 뒤 영문 대문자 3자인지 본다. 기본 로케일에 기대지 않는다
    // (터키어 로케일에서 "icn".toUpperCase()는 "İCN"이 된다)
    private static String airportCode(String raw) {
        if (raw == null) {
            throw new BadRequestException(ErrorTag.INVALID_AIRPORT_CODE);
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!AIRPORT_CODE.matcher(normalized).matches()) {
            throw new BadRequestException(ErrorTag.INVALID_AIRPORT_CODE);
        }
        return normalized;
    }
}
