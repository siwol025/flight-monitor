package com.siwol025.flight_monitor.fare.adapter.duffel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siwol025.flight_monitor.fare.FareRateLimitedException;
import com.siwol025.flight_monitor.fare.LowestFare;
import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.duffel.MockDuffelOfferController;
import com.siwol025.flight_monitor.mock.duffel.MockDuffelOfferService;
import com.siwol025.flight_monitor.mock.duffel.MockDuffelRateLimitEnforcer;
import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/**
 * 와이어 호환: 운영 어댑터와 mock 엔드포인트가 실제 HTTP로만 대화한다(운영의 앱 ↔ flight-mock-api와 같은 구성).
 * 서버는 mock 엔드포인트만 올린 최소 앱(랜덤 포트, 테스트 yml의 H2), 클라이언트는 어댑터 설정만 올린 별도 컨텍스트다.
 */
@SpringBootTest(classes = DuffelWireCompatibilityTest.MockServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DuffelWireCompatibilityTest {

    private static final RouteKey ICN_NRT_ECONOMY =
            new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY);

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {MockFlight.class, MockAirline.class, MockAirport.class})
    @EnableJpaRepositories(basePackageClasses = {MockFlightRepository.class, MockAirlineRepository.class,
            MockAirportRepository.class})
    @Import({MockDuffelOfferController.class, MockDuffelOfferService.class, MockDuffelRateLimitEnforcer.class})
    static class MockServerApplication {

        // 한도 창이 테스트 도중 넘어가지 않게 시각을 고정한다(16:07:44가 속한 창은 16:08:00에 끝난다)
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-25T16:07:44Z"), ZoneOffset.UTC);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MockAirlineRepository mockAirlineRepository;

    @Autowired
    private MockAirportRepository mockAirportRepository;

    @Autowired
    private MockFlightRepository mockFlightRepository;

    @Autowired
    private MockFlightSeatPriceRepository mockFlightSeatPriceRepository;

    @AfterEach
    void tearDown() {
        // 랜덤 포트 서버는 트랜잭션 롤백이 안 되므로 FK 순서대로 지운다(가격 → 항공편 → 공항·항공사)
        mockFlightSeatPriceRepository.deleteAllInBatch();
        mockFlightRepository.deleteAllInBatch();
        mockAirportRepository.deleteAllInBatch();
        mockAirlineRepository.deleteAllInBatch();
    }

    // compose처럼 토큰 없이(빈 Bearer) 부른다 — mock은 Authorization을 검사하지 않는다
    private ApplicationContextRunner adapterContext(String baseUrl) {
        return new ApplicationContextRunner()
                .withUserConfiguration(DuffelHttpClientConfig.class)
                .withPropertyValues("fare-provider.duffel.base-url=" + baseUrl, "fare-provider.duffel.api-key=");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 와이어호환_어댑터가_mock엔드포인트에서_최저가조회성공(CapturedOutput output) {
        MockAirline koreanAir = mockAirlineRepository.save(
                MockAirline.builder().airlineCode("KE").airlineName("Korean Air").build());
        MockAirport icn = mockAirportRepository.save(MockAirport.builder().airportCode("ICN").airportName("인천").build());
        MockAirport nrt = mockAirportRepository.save(MockAirport.builder().airportCode("NRT").airportName("나리타").build());
        saveFlight("KE701", koreanAir, icn, nrt, LocalDateTime.of(2026, 11, 26, 9, 0),
                LocalDateTime.of(2026, 11, 26, 11, 20), "300000");
        saveFlight("KE703", koreanAir, icn, nrt, LocalDateTime.of(2026, 11, 26, 13, 0),
                LocalDateTime.of(2026, 11, 26, 15, 20), "250000");

        adapterContext("http://localhost:" + port).run(context -> {
            Optional<LowestFare> lowestFare = context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

            assertThat(lowestFare).contains(new LowestFare(new BigDecimal("250000.00"), "USD", "KE", "Korean Air",
                    "703", LocalDateTime.of(2026, 11, 26, 13, 0), LocalDateTime.of(2026, 11, 26, 15, 20)));
            // mock 기본 한도(30) = 어댑터 expected-rate-limit(30)이라 경고가 없어야 한다
            assertThat(output.getOut()).doesNotContain("expected-rate-limit");
        });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 와이어호환_어댑터가_실제Duffel응답파일을_해석성공(CapturedOutput output) throws IOException {
        // 관문 G에 기록된 실제 응답 헤더 그대로 + gzip 본문. 어댑터 기본값 expected-rate-limit(30)이
        // 실제 업체 헤더와 맞는지도 함께 고정한다(누가 기본값을 바꾸면 경고가 떠서 이 테스트가 잡는다)
        byte[] realResponse;
        try (InputStream in = getClass().getResourceAsStream("/duffel/real-offer-request-response.json")) {
            realResponse = in.readAllBytes();
        }
        try (FakeDuffelServer realDuffel = new FakeDuffelServer()) {
            realDuffel.respondGzip(201, Map.of(
                    "ratelimit-limit", "30",
                    "ratelimit-remaining", "29",
                    "ratelimit-reset", "Sun, 25 Oct 2026 16:08:00 GMT",
                    "x-request-id", "GOHRfaiFAMRJvLwAHC0R"), realResponse);

            adapterContext(realDuffel.baseUrl()).run(context -> {
                Optional<LowestFare> lowestFare = context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

                assertThat(lowestFare).contains(new LowestFare(new BigDecimal("72.40"), "USD", "ZZ", "Duffel Airways",
                        "1079", LocalDateTime.of(2026, 11, 26, 19, 21), LocalDateTime.of(2026, 11, 26, 21, 36)));
                assertThat(output.getOut()).doesNotContain("expected-rate-limit");
            });
        }
    }

    @Nested
    @TestPropertySource(properties = "mock.duffel.rate-limit.limit=1")
    class 한도1건 {

        @LocalServerPort
        private int limitedPort;

        @Test
        void 와이어호환_mock의429를_어댑터가_한도초과예외로변환() {
            adapterContext("http://localhost:" + limitedPort).run(context -> {
                LowestFareFinder finder = context.getBean(LowestFareFinder.class);
                finder.findLowestFare(ICN_NRT_ECONOMY);

                // 고정 시각 16:07:44가 속한 창은 16:08:00에 끝난다
                assertThatThrownBy(() -> finder.findLowestFare(ICN_NRT_ECONOMY))
                        .isInstanceOfSatisfying(FareRateLimitedException.class, e ->
                                assertThat(e.getResetAt()).isEqualTo(Instant.parse("2026-10-25T16:08:00Z")));
            });
        }
    }

    private void saveFlight(String flightNumber, MockAirline airline, MockAirport departure, MockAirport arrival,
                            LocalDateTime departureTime, LocalDateTime arrivalTime, String economyPrice) {
        MockFlight flight = MockFlight.builder()
                .flightNumber(flightNumber).airline(airline)
                .departureAirport(departure).arrivalAirport(arrival)
                .departureTime(departureTime).arrivalTime(arrivalTime)
                .build();
        flight.addFlightSeatPrice(FlightSeatPrice.builder().flight(flight)
                .seatGrade(SeatGrade.ECONOMY).price(new BigDecimal(economyPrice)).build());
        mockFlightRepository.save(flight);
    }
}
