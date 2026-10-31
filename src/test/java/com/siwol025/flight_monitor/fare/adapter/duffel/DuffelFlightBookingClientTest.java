package com.siwol025.flight_monitor.fare.adapter.duffel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.fare.FareProviderConfigurationException;
import com.siwol025.flight_monitor.fare.FareProviderUnavailableException;
import com.siwol025.flight_monitor.fare.FareRateLimitedException;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.FlightOfferPriceChangedException;
import com.siwol025.flight_monitor.fare.FlightOfferUnavailableException;
import com.siwol025.flight_monitor.fare.FlightOrderPlacer;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.fare.PlacedOrder;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 항공권 offer 검색·단건 조회·주문 어댑터. 업체는 FakeDuffelServer(실제 소켓)이고, 그 밖에는 목을 쓰지 않는다.
 */
class DuffelFlightBookingClientTest {

    private static final RouteKey ICN_NRT_ECONOMY =
            new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY);

    private static final Passenger PASSENGER = new Passenger(PassengerTitle.MR, "Gildong", "Hong", Gender.M,
            LocalDate.of(1990, 1, 1), "gildong@test.com", "+821012345678");

    private FakeDuffelServer server;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeDuffelServer();
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(DuffelHttpClientConfig.class)
                .withPropertyValues(
                        "fare-provider.duffel.base-url=" + server.baseUrl(),
                        "fare-provider.duffel.api-key=duffel_test_token");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    // Duffel offer 한 건(편도 1구간). 필드명과 값 형식은 실제 응답과 같다
    private static String offerJson(String id, String amount, String flightNumber, String departingAt,
                                    String arrivingAt) {
        return """
                {"id":"%s","total_amount":"%s","total_currency":"USD",
                "slices":[{"segments":[{"origin":{"iata_code":"ICN"},"destination":{"iata_code":"NRT"},
                "marketing_carrier":{"iata_code":"ZZ","name":"Duffel Airways"},
                "marketing_carrier_flight_number":"%s","departing_at":"%s","arriving_at":"%s",
                "passengers":[{"cabin_class":"economy"}]}]}]}"""
                .formatted(id, amount, flightNumber, departingAt, arrivingAt);
    }

    @Test
    void searchOffers_여러offer_가격오름차순() {
        // 비싼 것이 먼저 오고 금액 표기 자릿수가 다르다
        server.respond(201, Map.of(), "{\"data\":{\"offers\":["
                + offerJson("off_b", "300.00", "1002", "2026-11-26T13:00:00", "2026-11-26T15:20:00") + ","
                + offerJson("off_a", "250.5", "1001", "2026-11-26T09:00:00", "2026-11-26T11:20:00") + "]}}");

        contextRunner.run(context -> {
            List<FlightOffer> offers = context.getBean(FlightOfferFinder.class).searchOffers(ICN_NRT_ECONOMY);

            assertThat(offers).containsExactly(
                    new FlightOffer("off_a", new BigDecimal("250.5"), "USD", "ICN", "NRT", SeatGrade.ECONOMY,
                            "ZZ", "Duffel Airways", "1001",
                            LocalDateTime.of(2026, 11, 26, 9, 0), LocalDateTime.of(2026, 11, 26, 11, 20)),
                    new FlightOffer("off_b", new BigDecimal("300.00"), "USD", "ICN", "NRT", SeatGrade.ECONOMY,
                            "ZZ", "Duffel Airways", "1002",
                            LocalDateTime.of(2026, 11, 26, 13, 0), LocalDateTime.of(2026, 11, 26, 15, 20)));
            assertThat(server.lastRequest().method()).isEqualTo("POST");
            assertThat(server.lastRequest().path()).isEqualTo("/air/offer_requests");
            assertThat(server.lastRequest().query()).contains("return_offers=true");
        });
    }

    @Test
    void searchOffers_422_InvalidRouteException() {
        server.respond(422, Map.of(), errorBody(422, "validation_error", "invalid_airport"));

        contextRunner.run(context -> assertThatThrownBy(
                () -> context.getBean(FlightOfferFinder.class).searchOffers(ICN_NRT_ECONOMY))
                .isInstanceOf(InvalidRouteException.class));
    }

    @Test
    void findOffer_200_offer반환() {
        server.respond(200, Map.of(), "{\"data\":"
                + offerJson("off_a", "250.50", "1001", "2026-11-26T09:00:00", "2026-11-26T11:20:00") + "}");

        contextRunner.run(context -> {
            Optional<FlightOffer> offer = context.getBean(FlightOfferFinder.class).findOffer("off_a");

            assertThat(offer).contains(new FlightOffer("off_a", new BigDecimal("250.50"), "USD", "ICN", "NRT",
                    SeatGrade.ECONOMY, "ZZ", "Duffel Airways", "1001",
                    LocalDateTime.of(2026, 11, 26, 9, 0), LocalDateTime.of(2026, 11, 26, 11, 20)));
            assertThat(server.lastRequest().method()).isEqualTo("GET");
            assertThat(server.lastRequest().path()).isEqualTo("/air/offers/off_a");
            assertThat(server.lastRequest().headers().getFirst("Authorization")).isEqualTo("Bearer duffel_test_token");
            assertThat(server.lastRequest().headers().getFirst("Duffel-Version")).isEqualTo("v2");
        });
    }

    @Test
    void findOffer_422_빈결과() {
        server.respond(422, Map.of(), errorBody(422, "invalid_state_error", "offer_no_longer_available"));

        contextRunner.run(context ->
                assertThat(context.getBean(FlightOfferFinder.class).findOffer("off_a")).isEmpty());
    }

    @Test
    void findOffer_404_빈결과() {
        server.respond(404, Map.of(), errorBody(404, "invalid_request_error", "not_found"));

        contextRunner.run(context ->
                assertThat(context.getBean(FlightOfferFinder.class).findOffer("off_unknown")).isEmpty());
    }

    @Test
    void findOffer_429_FareRateLimitedException() {
        server.respond(429, Map.of("ratelimit-reset", "Sun, 25 Oct 2026 16:08:00 GMT"),
                errorBody(429, "rate_limit_error", "rate_limit_exceeded"));

        contextRunner.run(context -> assertThatThrownBy(
                () -> context.getBean(FlightOfferFinder.class).findOffer("off_a"))
                .isInstanceOfSatisfying(FareRateLimitedException.class, e ->
                        assertThat(e.getResetAt()).isEqualTo(Instant.parse("2026-10-25T16:08:00Z"))));
    }

    @Test
    void findOffer_5xx_FareProviderUnavailableException() {
        server.respond(503, Map.of(), errorBody(503, "api_error", "service_unavailable"));

        contextRunner.run(context -> assertThatThrownBy(
                () -> context.getBean(FlightOfferFinder.class).findOffer("off_a"))
                .isInstanceOf(FareProviderUnavailableException.class));
    }

    @Test
    void placeOrder_201_주문결과() {
        server.respond(201, Map.of(), """
                {"data":{"id":"ord_0000AbCdEf","booking_reference":"RZPNX8","total_amount":"250.50",
                "total_currency":"USD","passengers":[],"slices":[]}}""");

        contextRunner.run(context -> {
            PlacedOrder order = context.getBean(FlightOrderPlacer.class)
                    .placeOrder("off_a", new BigDecimal("250.50"), "USD", PASSENGER);

            assertThat(order).isEqualTo(new PlacedOrder("ord_0000AbCdEf", "RZPNX8", new BigDecimal("250.50"), "USD"));
        });
    }

    @Test
    void placeOrder_요청본문_Duffel주문형식() {
        server.respond(201, Map.of(), """
                {"data":{"id":"ord_1","booking_reference":"RZPNX8","total_amount":"250.50","total_currency":"USD"}}""");

        contextRunner.run(context -> {
            context.getBean(FlightOrderPlacer.class).placeOrder("off_a", new BigDecimal("250.50"), "USD", PASSENGER);

            FakeDuffelServer.RecordedRequest request = server.lastRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/air/orders");
            // 금액은 Duffel처럼 문자열, 호칭·성별은 소문자, 결제는 잔액(balance) 즉시 주문(instant)
            assertThat(new ObjectMapper().readTree(request.body())).isEqualTo(new ObjectMapper().readTree("""
                    {"data":{"type":"instant","selected_offers":["off_a"],
                    "payments":[{"type":"balance","amount":"250.50","currency":"USD"}],
                    "passengers":[{"title":"mr","given_name":"Gildong","family_name":"Hong","gender":"m",
                    "born_on":"1990-01-01","email":"gildong@test.com","phone_number":"+821012345678"}]}}"""));
        });
    }

    @Test
    void placeOrder_422_offer_no_longer_available_예매불가예외() {
        server.respond(422, Map.of(), errorBody(422, "invalid_state_error", "offer_no_longer_available"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(FlightOrderPlacer.class)
                .placeOrder("off_a", new BigDecimal("250.50"), "USD", PASSENGER))
                .isInstanceOf(FlightOfferUnavailableException.class));
    }

    @Test
    void placeOrder_422_price_changed_가격변동예외() {
        server.respond(422, Map.of(), errorBody(422, "invalid_state_error", "price_changed"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(FlightOrderPlacer.class)
                .placeOrder("off_a", new BigDecimal("250.50"), "USD", PASSENGER))
                .isInstanceOf(FlightOfferPriceChangedException.class));
    }

    @Test
    void placeOrder_404_예매불가예외() {
        server.respond(404, Map.of(), errorBody(404, "invalid_request_error", "not_found"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(FlightOrderPlacer.class)
                .placeOrder("off_unknown", new BigDecimal("250.50"), "USD", PASSENGER))
                .isInstanceOf(FlightOfferUnavailableException.class));
    }

    @Test
    void placeOrder_422_모르는code_FareProviderConfigurationException() {
        // 모르는 code, 해석할 수 없는 본문
        for (String body : new String[]{errorBody(422, "validation_error", "invalid_phone_number"), "not json"}) {
            server.respond(422, Map.of(), body);

            contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(FlightOrderPlacer.class)
                    .placeOrder("off_a", new BigDecimal("250.50"), "USD", PASSENGER))
                    .isInstanceOf(FareProviderConfigurationException.class)
                    .hasMessageContaining(body));
        }
    }

    // Duffel 오류 본문 형식: errors[](type·code·title·message) + meta(request_id·status)
    private static String errorBody(int status, String type, String code) {
        return """
                {"errors":[{"type":"%s","code":"%s","title":"Error","message":"Error message.",
                "documentation_url":"https://duffel.com/docs/api/overview/errors"}],
                "meta":{"request_id":"FakeRequestId","status":%d}}""".formatted(type, code, status);
    }
}
