package com.siwol025.flight_monitor.route.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RouteKeyTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 26);

    @Test
    void 노선키_소문자공항코드_대문자로정규화() {
        Locale original = Locale.getDefault();
        try {
            // 기본 로케일에 기대는 toUpperCase()라면 터키어에서 "icn"이 "İCN"(점 있는 I)이 된다
            Locale.setDefault(Locale.forLanguageTag("tr"));

            RouteKey key = new RouteKey(" icn ", "nrt", DEPARTURE_DATE, SeatGrade.ECONOMY);

            assertThat(key.departureAirport()).isEqualTo("ICN");
            assertThat(key.arrivalAirport()).isEqualTo("NRT");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void 노선키_출발지도착지동일_예외() {
        // 정규화 뒤에 같으면 같은 공항이다
        assertThatThrownBy(() -> new RouteKey("ICN", "icn", DEPARTURE_DATE, SeatGrade.ECONOMY))
                .isInstanceOfSatisfying(BadRequestException.class, e ->
                        assertThat(e.getErrorTag()).isEqualTo(ErrorTag.SAME_DEPARTURE_ARRIVAL_AIRPORT));
    }

    @Test
    void 노선키_과거출발일_예외() {
        // 한국 시각 10-26 08:00 = UTC 10-25 23:00. JVM 기본 시간대(컨테이너는 UTC)로 오늘을 구하면 어제(10-25)가 통과한다
        Clock seoulMorning = Clock.fixed(Instant.parse("2026-10-25T23:00:00Z"), ZoneId.of("Asia/Seoul"));

        assertThatThrownBy(() -> RouteKey.of("ICN", "NRT", LocalDate.of(2026, 10, 25), SeatGrade.ECONOMY, seoulMorning))
                .isInstanceOfSatisfying(BadRequestException.class, e ->
                        assertThat(e.getErrorTag()).isEqualTo(ErrorTag.PAST_DEPARTURE_DATE));
        assertThat(RouteKey.of("ICN", "NRT", LocalDate.of(2026, 10, 26), SeatGrade.ECONOMY, seoulMorning))
                .isEqualTo(new RouteKey("ICN", "NRT", LocalDate.of(2026, 10, 26), SeatGrade.ECONOMY));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"IC", "ICNN", "I1N"})
    void 노선키_공항코드형식오류_예외(String airportCode) {
        // 형식은 정규화 뒤 영문 대문자 3자
        assertThatThrownBy(() -> new RouteKey(airportCode, "NRT", DEPARTURE_DATE, SeatGrade.ECONOMY))
                .isInstanceOfSatisfying(BadRequestException.class, e ->
                        assertThat(e.getErrorTag()).isEqualTo(ErrorTag.INVALID_AIRPORT_CODE));
    }
}
