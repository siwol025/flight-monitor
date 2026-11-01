package com.siwol025.flight_monitor.mock.seed;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * mock 인스턴스 기동 시 채우는 가짜 항공편 데이터 규모. Duffel test 모드처럼 가상 항공사·항공편을 미리 채워 둔다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mock.seed")
public class MockSeedProperties {

    /** 시딩 여부. 기본값은 끔이고 application.yml에서 켠다(시더 빈은 mock 프로필 전용) */
    private boolean enabled = false;

    private List<Airport> airports = List.of();

    /** Duffel test의 가상 항공사처럼 실제가 아닌 항공사 */
    private List<Airline> airlines = List.of();

    /** 오늘부터 채울 날짜 수 */
    private int days;

    /** 출발 ≠ 도착 공항 쌍 하나, 날짜 하나당 항공편 수 */
    private int flightsPerRoutePerDay;

    /** 등급별 가격 범위(USD 정수, 양 끝 포함) */
    private Map<SeatGrade, PriceRange> prices = Map.of();

    /** 항공편 하나의 등급별 좌석 수. 매진을 쉽게 만들 수 있도록 작게 둔다 */
    private Map<SeatGrade, Integer> seats = Map.of();

    /** 같은 시드면 같은 데이터 */
    private long randomSeed;

    public record Airport(String code, String name) {
    }

    public record Airline(String code, String name) {
    }

    public record PriceRange(int min, int max) {
    }
}
