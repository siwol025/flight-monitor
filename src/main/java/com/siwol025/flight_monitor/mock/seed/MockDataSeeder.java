package com.siwol025.flight_monitor.mock.seed;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * mock 인스턴스가 기동할 때 오늘부터 N일의 가짜 항공편·가격·좌석을 채운다. 대량 삽입이라 JPA 대신 JDBC 배치로 넣고,
 * 날짜 하나를 트랜잭션 하나로 묶는다.
 */
@Component
@Profile("mock")
public class MockDataSeeder implements ApplicationRunner {

    // 출발 06:00~22:00, 비행시간 1~12시간 (모두 5분 단위)
    private static final int FIRST_DEPARTURE_MINUTE = 6 * 60;
    private static final int DEPARTURE_SLOTS = (22 - 6) * 60 / 5 + 1;
    private static final int MIN_DURATION_MINUTES = 60;
    private static final int DURATION_SLOTS = (12 * 60 - 60) / 5 + 1;
    private static final int FIRST_FLIGHT_NUMBER = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MockSeedProperties properties;
    private final Clock clock;

    public MockDataSeeder(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                          MockSeedProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        if (!properties.isEnabled()) {
            return;
        }
        Map<String, Long> airportIds = insertAirports();
        Map<String, Long> airlineIds = insertAirlines();
        LocalDate today = LocalDate.now(clock);
        Set<LocalDate> seededDates = datesWithFlights(today, today.plusDays(properties.getDays()));
        for (int day = 0; day < properties.getDays(); day++) {
            LocalDate date = today.plusDays(day);
            if (seededDates.contains(date)) {
                continue;
            }
            transactionTemplate.executeWithoutResult(status -> seedDate(date, airportIds, airlineIds));
        }
    }

    private Map<String, Long> insertAirports() {
        Map<String, Long> existing = idsByCode("SELECT id, airport_code AS code FROM mock_airports");
        for (MockSeedProperties.Airport airport : properties.getAirports()) {
            if (existing.containsKey(airport.code())) {
                continue;
            }
            jdbcTemplate.update("INSERT INTO mock_airports (airport_code, airport_name) VALUES (?, ?)",
                    airport.code(), airport.name());
        }
        return idsByCode("SELECT id, airport_code AS code FROM mock_airports");
    }

    private Map<String, Long> insertAirlines() {
        Map<String, Long> existing = idsByCode("SELECT id, airline_code AS code FROM mock_airlines");
        for (MockSeedProperties.Airline airline : properties.getAirlines()) {
            if (existing.containsKey(airline.code())) {
                continue;
            }
            jdbcTemplate.update("INSERT INTO mock_airlines (airline_code, airline_name) VALUES (?, ?)",
                    airline.code(), airline.name());
        }
        return idsByCode("SELECT id, airline_code AS code FROM mock_airlines");
    }

    private Map<String, Long> idsByCode(String sql) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> Map.entry(rs.getString("code"), rs.getLong("id"))).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
    }

    // 항공편이 하나라도 있는 날짜(관리 API로 수동 등록한 날짜 포함)는 건드리지 않는다
    private Set<LocalDate> datesWithFlights(LocalDate from, LocalDate toExclusive) {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT CAST(departure_time AS DATE) FROM mock_flights "
                        + "WHERE departure_time >= ? AND departure_time < ?",
                LocalDate.class, from.atStartOfDay(), toExclusive.atStartOfDay()));
    }

    private void seedDate(LocalDate date, Map<String, Long> airportIds, Map<String, Long> airlineIds) {
        // 난수를 날짜마다 (시드, 날짜)로 새로 만든다. 언제 기동했든(어떤 날짜가 이미 있었든) 같은 날짜는 같은 데이터가 된다
        Random random = new Random(31 * properties.getRandomSeed() + date.toEpochDay());
        List<Object[]> flights = new ArrayList<>();
        int flightNumber = FIRST_FLIGHT_NUMBER;
        for (MockSeedProperties.Airport departure : properties.getAirports()) {
            for (MockSeedProperties.Airport arrival : properties.getAirports()) {
                if (departure.code().equals(arrival.code())) {
                    continue;
                }
                for (int i = 0; i < properties.getFlightsPerRoutePerDay(); i++) {
                    MockSeedProperties.Airline airline = properties.getAirlines()
                            .get(random.nextInt(properties.getAirlines().size()));
                    LocalDateTime departureTime = date.atStartOfDay()
                            .plusMinutes(FIRST_DEPARTURE_MINUTE + random.nextInt(DEPARTURE_SLOTS) * 5L);
                    LocalDateTime arrivalTime = departureTime
                            .plusMinutes(MIN_DURATION_MINUTES + random.nextInt(DURATION_SLOTS) * 5L);
                    flights.add(new Object[]{airline.code() + flightNumber++, airlineIds.get(airline.code()),
                            airportIds.get(departure.code()), airportIds.get(arrival.code()), departureTime, arrivalTime});
                }
            }
        }
        jdbcTemplate.batchUpdate("INSERT INTO mock_flights (flight_number, airline_id, departure_airport_id, "
                + "arrival_airport_id, departure_time, arrival_time) VALUES (?, ?, ?, ?, ?, ?)", flights);

        // 배치의 생성 키는 드라이버마다 다르게 돌려주므로, 비어 있던 날짜 안에서 유일한 편명으로 다시 읽어 ID를 찾는다
        Map<String, Long> flightIds = jdbcTemplate.query(
                "SELECT id, flight_number FROM mock_flights WHERE departure_time >= ? AND departure_time < ?",
                (rs, rowNum) -> Map.entry(rs.getString("flight_number"), rs.getLong("id")),
                date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<Object[]> prices = new ArrayList<>();
        List<Object[]> seats = new ArrayList<>();
        for (Object[] flight : flights) {
            Long flightId = flightIds.get((String) flight[0]);
            for (SeatGrade grade : SeatGrade.values()) {
                MockSeedProperties.PriceRange range = properties.getPrices().get(grade);
                prices.add(new Object[]{flightId, grade.name(), range.min() + random.nextInt(range.max() - range.min() + 1)});
                int seatCount = properties.getSeats().getOrDefault(grade, 0);
                for (int seat = 1; seat <= seatCount; seat++) {
                    seats.add(new Object[]{flightId, grade.name().charAt(0) + String.valueOf(seat), grade.name(), false});
                }
            }
        }
        jdbcTemplate.batchUpdate("INSERT INTO mock_flight_seat_prices (flight_id, grade_name, price) VALUES (?, ?, ?)",
                prices);
        jdbcTemplate.batchUpdate("INSERT INTO mock_seats (flight_id, seat_number, grade_name, is_booked) "
                + "VALUES (?, ?, ?, ?)", seats);
    }
}
