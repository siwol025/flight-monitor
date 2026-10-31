package com.siwol025.flight_monitor.mock.duffel;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Duffel 주문(POST /air/orders)의 HTTP 계약. 좌석 차감은 주문 응답과 이어지는 검색 결과(공개 계약)로만 본다.
 * 서비스·리포지토리·한도는 실제 객체다.
 */
@DataJpaTest
@Import({MockDuffelOfferService.class, MockDuffelOrderService.class})
class MockDuffelOrderControllerTest {

    // 한도 창이 테스트 도중 넘어가지 않게 시각을 고정한다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T16:07:44Z"), ZoneOffset.UTC);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MockDuffelOfferService mockDuffelOfferService;

    @Autowired
    private MockDuffelOrderService mockDuffelOrderService;

    private String offerId;
    private Long seat1aId;
    private Long seat2aId;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockAirline koreanAir = em.persist(MockAirline.builder().airlineCode("KE").airlineName("Korean Air").build());
        MockAirport icn = em.persist(MockAirport.builder().airportCode("ICN").airportName("인천").build());
        MockAirport nrt = em.persist(MockAirport.builder().airportCode("NRT").airportName("나리타").build());
        MockFlight ke701 = MockFlight.builder()
                .flightNumber("KE701").airline(koreanAir).departureAirport(icn).arrivalAirport(nrt)
                .departureTime(LocalDateTime.of(2026, 11, 26, 9, 0)).arrivalTime(LocalDateTime.of(2026, 11, 26, 11, 20))
                .build();
        ke701.addFlightSeatPrice(FlightSeatPrice.builder().flight(ke701)
                .seatGrade(SeatGrade.ECONOMY).price(new BigDecimal("250000")).build());
        Seat seat1a = Seat.builder().flight(ke701).seatGrade(SeatGrade.ECONOMY).seatNumber("1A").isBooked(false).build();
        ke701.addSeat(seat1a);
        Seat seat2a = Seat.builder().flight(ke701).seatGrade(SeatGrade.ECONOMY).seatNumber("2A").isBooked(false).build();
        ke701.addSeat(seat2a);
        em.persist(ke701);
        em.flush();
        offerId = "off_mock_" + ke701.getFlightSeatPrices().getFirst().getId();
        seat1aId = seat1a.getId();
        seat2aId = seat2a.getId();
        em.clear();

        // 주문·검색이 같은 한도 예산을 쓴다(Duffel 한도는 API 키 단위)
        MockDuffelRateLimitEnforcer enforcer = new MockDuffelRateLimitEnforcer(30, Duration.ofSeconds(60));
        var clock = new StaticListableBeanFactory(Map.of("clock", CLOCK)).getBeanProvider(Clock.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MockDuffelOrderController(mockDuffelOrderService, enforcer, clock),
                new MockDuffelOfferController(mockDuffelOfferService, enforcer, clock)).build();
    }

    @Test
    void order_빈좌석있음_좌석하나예약_201() throws Exception {
        // 금액 표기가 달라도(250000 vs 250000.00) 같은 금액이다
        order(offerId, "250000", "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("ord_mock_" + seat1aId))
                .andExpect(jsonPath("$.data.booking_reference").value(matchesPattern("[0-9A-Z]{6}")))
                .andExpect(jsonPath("$.data.total_amount").value("250000.00"))
                .andExpect(jsonPath("$.data.total_currency").value("USD"))
                .andExpect(jsonPath("$.data.passengers[0].given_name").value("Gildong"))
                .andExpect(jsonPath("$.data.passengers[0].family_name").value("Hong"))
                .andExpect(jsonPath("$.data.passengers[0].born_on").value("1990-01-01"))
                .andExpect(jsonPath("$.data.passengers[0].email").value("gildong@test.com"))
                .andExpect(jsonPath("$.data.passengers[0].phone_number").value("+821012345678"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].origin.iata_code").value("ICN"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].marketing_carrier_flight_number").value("701"))
                .andExpect(jsonPath("$.data.slices[0].segments[0].departing_at").value("2026-11-26T09:00:00"));

        // 좌석 2개 중 1개만 차감되어 아직 검색된다
        search().andExpect(jsonPath("$.data.offers[0].id").value(offerId));
    }

    @Test
    void order_마지막좌석예약후_검색에서제외() throws Exception {
        order(offerId, "250000.00", "USD").andExpect(jsonPath("$.data.id").value("ord_mock_" + seat1aId));
        endRequest();

        order(offerId, "250000.00", "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("ord_mock_" + seat2aId));

        search().andExpect(jsonPath("$.data.offers").isEmpty());
        mockMvc.perform(get("/air/offers/{id}", offerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("offer_no_longer_available"));
    }

    @Test
    void order_매진_offer_no_longer_available() throws Exception {
        order(offerId, "250000.00", "USD").andExpect(status().isCreated());
        endRequest();
        order(offerId, "250000.00", "USD").andExpect(status().isCreated());
        endRequest();

        order(offerId, "250000.00", "USD")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("offer_no_longer_available"))
                .andExpect(jsonPath("$.meta.status").value(422));
    }

    @Test
    void order_금액불일치_price_changed_좌석차감안함() throws Exception {
        // 금액이 다름, 통화가 다름, 금액이 숫자가 아님
        String[][] mismatches = {{"240000.00", "USD"}, {"250000.00", "KRW"}, {"abc", "USD"}};
        for (String[] mismatch : mismatches) {
            order(offerId, mismatch[0], mismatch[1])
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("price_changed"))
                    .andExpect(jsonPath("$.meta.status").value(422));
            endRequest();
        }

        // 좌석이 차감되지 않았으므로 정상 주문은 여전히 첫 좌석을 받는다
        order(offerId, "250000.00", "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("ord_mock_" + seat1aId));
    }

    @Test
    void order_없는offer_not_found() throws Exception {
        // 없는 가격 행, 형식이 맞지 않는 ID
        for (String unknownOfferId : new String[]{"off_mock_999999", "off_abc"}) {
            order(unknownOfferId, "250000.00", "USD")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0].code").value("not_found"))
                    .andExpect(jsonPath("$.meta.status").value(404));
        }
    }

    private ResultActions order(String offerId, String amount, String currency) throws Exception {
        return mockMvc.perform(post("/air/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"data":{"type":"instant","selected_offers":["%s"],
                "payments":[{"type":"balance","amount":"%s","currency":"%s"}],
                "passengers":[{"id":"pas_1","title":"mr","gender":"m","given_name":"Gildong","family_name":"Hong",
                "born_on":"1990-01-01","email":"gildong@test.com","phone_number":"+821012345678"}]}}"""
                .formatted(offerId, amount, currency)));
    }

    // 운영에서는 요청마다 주문 트랜잭션이 커밋된다. 테스트는 한 트랜잭션이라 변경을 내보낸 뒤 비워 요청 경계를 흉내 낸다
    private void endRequest() {
        em.flush();
        em.clear();
    }

    private ResultActions search() throws Exception {
        endRequest();
        return mockMvc.perform(post("/air/offer_requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"data":{"slices":[{"origin":"ICN","destination":"NRT","departure_date":"2026-11-26"}],
                "passengers":[{"type":"adult"}],"cabin_class":"economy"}}"""));
    }
}
