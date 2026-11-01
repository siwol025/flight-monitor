package com.siwol025.flight_monitor.mock.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.domain.Seat;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockSeatRepository;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
class MockDataSeederTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockAirportRepository airportRepository;
    @Autowired private MockAirlineRepository airlineRepository;
    @Autowired private MockFlightRepository flightRepository;
    @Autowired private MockFlightSeatPriceRepository seatPriceRepository;
    @Autowired private MockSeatRepository seatRepository;

    @Test
    void seed_빈DB_설정대로_항공편가격좌석생성() {
        seeder(properties(), TODAY).seed();

        assertThat(airportRepository.findAll())
                .extracting(MockAirport::getAirportCode, MockAirport::getAirportName)
                .containsExactlyInAnyOrder(tuple("ICN", "인천"), tuple("NRT", "나리타"), tuple("KIX", "간사이"));
        assertThat(airlineRepository.findAll())
                .extracting(MockAirline::getAirlineCode, MockAirline::getAirlineName)
                .containsExactly(tuple("ZZ", "Duffel Airways"));

        // 출발≠도착 쌍 6 × 3일 × 노선·날짜당 2편 = 36편
        List<MockFlight> flights = flightRepository.findAll();
        assertThat(flights).hasSize(36);
        assertThat(flights.stream()
                .collect(Collectors.groupingBy(f -> f.getDepartureTime().toLocalDate(), Collectors.counting())))
                .isEqualTo(Map.of(TODAY, 12L, TODAY.plusDays(1), 12L, TODAY.plusDays(2), 12L));
        assertThat(flights.stream().collect(Collectors.groupingBy(
                        f -> f.getDepartureAirport().getAirportCode() + "-" + f.getArrivalAirport().getAirportCode(),
                        Collectors.counting())))
                .isEqualTo(Map.of("ICN-NRT", 6L, "ICN-KIX", 6L, "NRT-ICN", 6L,
                        "NRT-KIX", 6L, "KIX-ICN", 6L, "KIX-NRT", 6L));
        assertThat(flights).allSatisfy(f -> {
            assertThat(f.getAirline().getAirlineCode()).isEqualTo("ZZ");
            assertThat(f.getFlightNumber()).startsWith("ZZ");
            assertThat(f.getArrivalTime()).isAfter(f.getDepartureTime());
        });

        // 항공편마다 3등급 가격 하나씩, 등급별 범위 안
        List<FlightSeatPrice> prices = seatPriceRepository.findAll();
        assertThat(prices).hasSize(108);
        assertThat(prices.stream().collect(Collectors.groupingBy(p -> p.getFlight().getId(),
                        Collectors.mapping(FlightSeatPrice::getSeatGrade, Collectors.toSet()))))
                .allSatisfy((flightId, grades) -> assertThat(grades)
                        .containsExactlyInAnyOrder(SeatGrade.ECONOMY, SeatGrade.BUSINESS, SeatGrade.FIRST));
        assertThat(prices).allSatisfy(p -> {
            switch (p.getSeatGrade()) {
                case ECONOMY -> assertThat(p.getPrice()).isBetween(new BigDecimal("100"), new BigDecimal("200"));
                case BUSINESS -> assertThat(p.getPrice()).isBetween(new BigDecimal("500"), new BigDecimal("600"));
                case FIRST -> assertThat(p.getPrice()).isBetween(new BigDecimal("1000"), new BigDecimal("1100"));
            }
        });

        // 항공편마다 E2/B1/F1 = 4석, 모두 빈 좌석
        List<Seat> seats = seatRepository.findAll();
        assertThat(seats).hasSize(144);
        assertThat(seats).noneMatch(Seat::isBooked);
        assertThat(seats.stream().collect(Collectors.groupingBy(s -> s.getFlight().getId(),
                        Collectors.groupingBy(Seat::getSeatGrade, Collectors.counting()))))
                .allSatisfy((flightId, countByGrade) -> assertThat(countByGrade)
                        .isEqualTo(Map.of(SeatGrade.ECONOMY, 2L, SeatGrade.BUSINESS, 1L, SeatGrade.FIRST, 1L)));
    }

    @Test
    void seed_다시실행_중복생성안함() {
        seeder(properties(), TODAY).seed();
        seeder(properties(), TODAY).seed();

        assertThat(airportRepository.count()).isEqualTo(3);
        assertThat(airlineRepository.count()).isEqualTo(1);
        assertThat(flightRepository.count()).isEqualTo(36);
        assertThat(seatPriceRepository.count()).isEqualTo(108);
        assertThat(seatRepository.count()).isEqualTo(144);
    }

    @Test
    void seed_날짜가지나면_새날짜만추가() {
        seeder(properties(), TODAY).seed();
        Map<LocalDate, Set<Long>> before = flightIdsByDate();

        seeder(properties(), TODAY.plusDays(1)).seed();

        Map<LocalDate, Set<Long>> after = flightIdsByDate();
        assertThat(after.keySet())
                .containsExactlyInAnyOrder(TODAY, TODAY.plusDays(1), TODAY.plusDays(2), TODAY.plusDays(3));
        assertThat(after.get(TODAY.plusDays(3))).hasSize(12);
        // 이미 있던 날짜는 그대로
        assertThat(after.get(TODAY)).isEqualTo(before.get(TODAY));
        assertThat(after.get(TODAY.plusDays(1))).isEqualTo(before.get(TODAY.plusDays(1)));
        assertThat(after.get(TODAY.plusDays(2))).isEqualTo(before.get(TODAY.plusDays(2)));
        // 4일 × 12편 = 48편 → 가격 144, 좌석 192
        assertThat(seatPriceRepository.count()).isEqualTo(144);
        assertThat(seatRepository.count()).isEqualTo(192);
    }

    @Test
    void seed_비활성화_아무것도안만듦() {
        MockSeedProperties properties = properties();
        properties.setEnabled(false);

        seeder(properties, TODAY).seed();

        assertThat(airportRepository.count()).isZero();
        assertThat(airlineRepository.count()).isZero();
        assertThat(flightRepository.count()).isZero();
        assertThat(seatPriceRepository.count()).isZero();
        assertThat(seatRepository.count()).isZero();
    }

    @Test
    void seed_같은시드_같은데이터() {
        seeder(properties(), TODAY).seed();
        List<String> first = flightSnapshots(TODAY);

        // 항공편·가격·좌석을 비우고(공항·항공사는 남김) 같은 설정으로 다시 채운다
        seatRepository.deleteAllInBatch();
        seatPriceRepository.deleteAllInBatch();
        flightRepository.deleteAllInBatch();
        seeder(properties(), TODAY).seed();

        assertThat(flightSnapshots(TODAY)).hasSize(36).isEqualTo(first);

        // 기동 이력과 상관없이 같은 날짜는 같은 데이터: D에 채운 뒤 D+1에 다시 기동한 DB와 빈 DB에 D+1로 채운 DB 비교
        seeder(properties(), TODAY.plusDays(1)).seed();
        List<String> afterRollover = flightSnapshots(TODAY.plusDays(1));
        seatRepository.deleteAllInBatch();
        seatPriceRepository.deleteAllInBatch();
        flightRepository.deleteAllInBatch();
        seeder(properties(), TODAY.plusDays(1)).seed();

        assertThat(flightSnapshots(TODAY.plusDays(1))).hasSize(36).isEqualTo(afterRollover);
    }

    // from 이후 출발편의 편명·노선·시각·등급별 가격. ID는 다시 채우면 바뀌므로 넣지 않는다
    private List<String> flightSnapshots(LocalDate from) {
        Map<Long, String> pricesByFlight = seatPriceRepository.findAll().stream().collect(Collectors.groupingBy(
                p -> p.getFlight().getId(),
                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                        .sorted(Comparator.comparing(FlightSeatPrice::getSeatGrade))
                        .map(p -> p.getSeatGrade() + "=" + p.getPrice().toPlainString())
                        .collect(Collectors.joining(",")))));
        return flightRepository.findAll().stream()
                .filter(f -> !f.getDepartureTime().toLocalDate().isBefore(from))
                .map(f -> String.join(" ", f.getFlightNumber(), f.getDepartureAirport().getAirportCode(),
                        f.getArrivalAirport().getAirportCode(), f.getDepartureTime().toString(),
                        f.getArrivalTime().toString(), pricesByFlight.get(f.getId())))
                .sorted()
                .toList();
    }

    private Map<LocalDate, Set<Long>> flightIdsByDate() {
        return flightRepository.findAll().stream().collect(Collectors.groupingBy(
                f -> f.getDepartureTime().toLocalDate(), Collectors.mapping(MockFlight::getId, Collectors.toSet())));
    }

    private MockDataSeeder seeder(MockSeedProperties properties, LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(9, 0).atZone(SEOUL).toInstant(), SEOUL);
        return new MockDataSeeder(new JdbcTemplate(dataSource), new TransactionTemplate(transactionManager),
                properties, clock);
    }

    private static MockSeedProperties properties() {
        MockSeedProperties properties = new MockSeedProperties();
        properties.setEnabled(true);
        properties.setAirports(List.of(new MockSeedProperties.Airport("ICN", "인천"),
                new MockSeedProperties.Airport("NRT", "나리타"), new MockSeedProperties.Airport("KIX", "간사이")));
        properties.setAirlines(List.of(new MockSeedProperties.Airline("ZZ", "Duffel Airways")));
        properties.setDays(3);
        properties.setFlightsPerRoutePerDay(2);
        properties.setPrices(Map.of(
                SeatGrade.ECONOMY, new MockSeedProperties.PriceRange(100, 200),
                SeatGrade.BUSINESS, new MockSeedProperties.PriceRange(500, 600),
                SeatGrade.FIRST, new MockSeedProperties.PriceRange(1000, 1100)));
        properties.setSeats(Map.of(SeatGrade.ECONOMY, 2, SeatGrade.BUSINESS, 1, SeatGrade.FIRST, 1));
        properties.setRandomSeed(42L);
        return properties;
    }
}
