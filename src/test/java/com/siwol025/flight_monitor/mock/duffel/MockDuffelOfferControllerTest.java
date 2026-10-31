package com.siwol025.flight_monitor.mock.duffel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.domain.Seat;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Duffel 단건 offer 조회(GET /air/offers/{id})의 HTTP 계약: 상태 코드·오류 code·본문 형식. 서비스·리포지토리·한도는 실제 객체다.
 */
@DataJpaTest
@Import(MockDuffelOfferService.class)
class MockDuffelOfferControllerTest {

    // 한도 창이 테스트 도중 넘어가지 않게 시각을 고정한다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T16:07:44Z"), ZoneOffset.UTC);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MockDuffelOfferService mockDuffelOfferService;

    private MockFlight ke701;

    @BeforeEach
    void setUp() {
        MockAirline koreanAir = em.persist(MockAirline.builder().airlineCode("KE").airlineName("Korean Air").build());
        MockAirport icn = em.persist(MockAirport.builder().airportCode("ICN").airportName("인천").build());
        MockAirport nrt = em.persist(MockAirport.builder().airportCode("NRT").airportName("나리타").build());
        ke701 = MockFlight.builder()
                .flightNumber("KE701").airline(koreanAir).departureAirport(icn).arrivalAirport(nrt)
                .departureTime(LocalDateTime.of(2026, 11, 26, 9, 0)).arrivalTime(LocalDateTime.of(2026, 11, 26, 11, 20))
                .build();
        ke701.addFlightSeatPrice(FlightSeatPrice.builder().flight(ke701)
                .seatGrade(SeatGrade.ECONOMY).price(new BigDecimal("250000")).build());
        ke701.addSeat(Seat.builder().flight(ke701).seatGrade(SeatGrade.ECONOMY).seatNumber("1A").isBooked(false).build());
        em.persist(ke701);
        em.flush();
    }

    @Test
    void findOffer_예매가능_offer반환() throws Exception {
        String offerId = "off_mock_" + ke701.getFlightSeatPrices().getFirst().getId();
        em.clear();

        mockMvc(30).perform(get("/air/offers/{id}", offerId))
                .andExpect(status().isOk())
                .andExpect(header().string("ratelimit-limit", "30"))
                .andExpect(jsonPath("$.data.id").value(offerId))
                .andExpect(jsonPath("$.data.total_amount").value("250000.00"))
                .andExpect(jsonPath("$.data.total_currency").value("USD"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].origin.iata_code").value("ICN"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].destination.iata_code").value("NRT"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].marketing_carrier.iata_code").value("KE"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].marketing_carrier.name").value("Korean Air"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].marketing_carrier_flight_number").value("701"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].departing_at").value("2026-11-26T09:00:00"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].arriving_at").value("2026-11-26T11:20:00"));
    }

    @Test
    void findOffer_매진_offer_no_longer_available() throws Exception {
        String offerId = "off_mock_" + ke701.getFlightSeatPrices().getFirst().getId();
        ke701.getSeats().forEach(Seat::reserve);
        em.flush();
        em.clear();

        mockMvc(30).perform(get("/air/offers/{id}", offerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("ratelimit-limit", "30"))
                .andExpect(jsonPath("$.errors[0].type").value("invalid_state_error"))
                .andExpect(jsonPath("$.errors[0].code").value("offer_no_longer_available"))
                .andExpect(jsonPath("$.meta.status").value(422));
    }

    @Test
    void findOffer_없는ID_not_found() throws Exception {
        em.clear();
        MockMvc mockMvc = mockMvc(30);

        // 없는 가격 행, 접두사가 다른 ID, 숫자가 아닌 ID
        for (String offerId : new String[]{"off_mock_999999", "off_abc", "off_mock_x"}) {
            mockMvc.perform(get("/air/offers/{id}", offerId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0].type").value("invalid_request_error"))
                    .andExpect(jsonPath("$.errors[0].code").value("not_found"))
                    .andExpect(jsonPath("$.meta.status").value(404));
        }
    }

    @Test
    void findOffer_한도초과_429() throws Exception {
        String offerId = "off_mock_" + ke701.getFlightSeatPrices().getFirst().getId();
        em.clear();
        MockMvc mockMvc = mockMvc(1);

        // 검색과 단건 조회가 같은 예산을 쓴다(Duffel 한도는 API 키 단위): 검색 1건으로 한도 1을 다 쓴다
        mockMvc.perform(post("/air/offer_requests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"data":{"slices":[{"origin":"ICN","destination":"NRT","departure_date":"2026-11-26"}],
                        "passengers":[{"type":"adult"}],"cabin_class":"economy"}}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/air/offers/{id}", offerId))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("ratelimit-limit", "1"))
                .andExpect(header().string("ratelimit-remaining", "0"))
                .andExpect(header().string("ratelimit-reset", "Sun, 25 Oct 2026 16:08:00 GMT"))
                .andExpect(jsonPath("$.errors[0].code").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.meta.status").value(429));
    }

    private MockMvc mockMvc(int rateLimit) {
        MockDuffelOfferController controller = new MockDuffelOfferController(mockDuffelOfferService,
                new MockDuffelRateLimitEnforcer(rateLimit, Duration.ofSeconds(60)),
                new StaticListableBeanFactory(Map.of("clock", CLOCK)).getBeanProvider(Clock.class));
        return MockMvcBuilders.standaloneSetup(controller).build();
    }
}
