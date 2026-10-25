package com.siwol025.flight_monitor.mock.duffel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(MockDuffelOfferService.class)
class MockDuffelOfferServiceTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 11, 26);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MockDuffelOfferService mockDuffelOfferService;

    private MockAirline koreanAir;
    private MockAirport icn;
    private MockAirport nrt;
    private MockAirport kix;

    @BeforeEach
    void setUp() {
        koreanAir = em.persist(MockAirline.builder().airlineCode("KE").airlineName("Korean Air").build());
        icn = em.persist(MockAirport.builder().airportCode("ICN").airportName("인천").build());
        nrt = em.persist(MockAirport.builder().airportCode("NRT").airportName("나리타").build());
        kix = em.persist(MockAirport.builder().airportCode("KIX").airportName("간사이").build());
    }

    @Test
    void 노선날짜등급으로_mock항공편필터_Duffel형식운임목록반환() {
        // 포함: 출발일 00:00, 23:59 출발
        persistFlight("KE701", icn, nrt, LocalDateTime.of(2026, 11, 26, 0, 0), LocalDateTime.of(2026, 11, 26, 2, 20),
                SeatGrade.ECONOMY, "250000", SeatGrade.BUSINESS, "900000");
        persistFlight("KE703", icn, nrt, LocalDateTime.of(2026, 11, 26, 23, 59), LocalDateTime.of(2026, 11, 27, 2, 10),
                SeatGrade.ECONOMY, "280000");
        // 제외: 다음 날 00:00, 전날 23:59, 다른 노선, 이코노미 가격 없음
        persistFlight("KE705", icn, nrt, LocalDateTime.of(2026, 11, 27, 0, 0), LocalDateTime.of(2026, 11, 27, 2, 20),
                SeatGrade.ECONOMY, "200000");
        persistFlight("KE699", icn, nrt, LocalDateTime.of(2026, 11, 25, 23, 59), LocalDateTime.of(2026, 11, 26, 2, 10),
                SeatGrade.ECONOMY, "210000");
        persistFlight("KE721", icn, kix, LocalDateTime.of(2026, 11, 26, 10, 0), LocalDateTime.of(2026, 11, 26, 12, 0),
                SeatGrade.ECONOMY, "150000");
        persistFlight("KE707", icn, nrt, LocalDateTime.of(2026, 11, 26, 12, 0), LocalDateTime.of(2026, 11, 26, 14, 20),
                SeatGrade.BUSINESS, "950000");
        em.flush();
        em.clear();

        MockDuffelOfferResponse response = mockDuffelOfferService.search(request("ICN", "NRT", DEPARTURE_DATE, "economy"));

        assertThat(response.data().offers())
                .extracting(offer -> offer.slices().getFirst().segments().getFirst().marketingCarrierFlightNumber(),
                        MockDuffelOfferResponse.Offer::totalAmount, MockDuffelOfferResponse.Offer::totalCurrency)
                .containsExactlyInAnyOrder(tuple("701", "250000.00", "USD"), tuple("703", "280000.00", "USD"));
        MockDuffelOfferResponse.Segment ke701 = response.data().offers().stream()
                .map(offer -> offer.slices().getFirst().segments().getFirst())
                .filter(segment -> segment.marketingCarrierFlightNumber().equals("701"))
                .findFirst().orElseThrow();
        assertThat(ke701).isEqualTo(new MockDuffelOfferResponse.Segment(
                new MockDuffelOfferResponse.Place("ICN"), new MockDuffelOfferResponse.Place("NRT"),
                new MockDuffelOfferResponse.Carrier("KE", "Korean Air"), "701",
                "2026-11-26T00:00:00", "2026-11-26T02:20:00"));
    }

    @Test
    void 해당노선항공편없음_빈offers반환() {
        persistFlight("KE721", icn, kix, LocalDateTime.of(2026, 11, 26, 10, 0), LocalDateTime.of(2026, 11, 26, 12, 0),
                SeatGrade.ECONOMY, "150000");
        em.flush();
        em.clear();

        MockDuffelOfferResponse response = mockDuffelOfferService.search(request("ICN", "NRT", DEPARTURE_DATE, "economy"));

        assertThat(response.data().offers()).isEmpty();
    }

    private void persistFlight(String flightNumber, MockAirport departure, MockAirport arrival,
                               LocalDateTime departureTime, LocalDateTime arrivalTime, Object... gradeAndPrices) {
        MockFlight flight = MockFlight.builder()
                .flightNumber(flightNumber).airline(koreanAir)
                .departureAirport(departure).arrivalAirport(arrival)
                .departureTime(departureTime).arrivalTime(arrivalTime)
                .build();
        for (int i = 0; i < gradeAndPrices.length; i += 2) {
            flight.addFlightSeatPrice(FlightSeatPrice.builder().flight(flight)
                    .seatGrade((SeatGrade) gradeAndPrices[i]).price(new BigDecimal((String) gradeAndPrices[i + 1]))
                    .build());
        }
        em.persist(flight);
    }

    private static MockDuffelOfferRequest request(String origin, String destination, LocalDate date, String cabinClass) {
        return new MockDuffelOfferRequest(new MockDuffelOfferRequest.Data(
                List.of(new MockDuffelOfferRequest.Slice(origin, destination, date)), cabinClass));
    }
}
