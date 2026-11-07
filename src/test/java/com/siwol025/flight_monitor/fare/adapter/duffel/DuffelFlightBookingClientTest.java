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
import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.fare.OfferTerms;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.fare.PlacedOrder;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.io.IOException;
import java.io.InputStream;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 항공권 offer 검색·단건 조회·주문 어댑터. 업체는 FakeDuffelServer(실제 소켓)이고, 그 밖에는 목을 쓰지 않는다.
 */
class DuffelFlightBookingClientTest {

    private static final RouteKey ICN_NRT_ECONOMY =
            new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY);

    private static final Passenger PASSENGER = new Passenger(PassengerTitle.MR, "Gildong", "Hong", Gender.M,
            LocalDate.of(1990, 1, 1), "gildong@test.com", "+821012345678");

    // 아래 offerJson처럼 주문 조건 필드가 없는 응답
    private static final OfferTerms NO_TERMS = new OfferTerms(List.of(), false, null, null);

    private FakeDuffelServer server;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeDuffelServer();
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(DuffelHttpClientConfig.class)
                .withPropertyValues(
                        "fare-provider.duffel-booking.base-url=" + server.baseUrl(),
                        "fare-provider.duffel-booking.api-key=duffel_test_token",
                        // 감시 설정은 예매 호출에 쓰이지 않는다(.invalid는 절대 해석되지 않는 예약 도메인)
                        "fare-provider.duffel.base-url=http://duffel.invalid");
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
    void bookingClient_예매설정의base_url과키로_호출() throws IOException {
        server.respond(200, Map.of(), "{\"data\":"
                + offerJson("off_a", "250.50", "1001", "2026-11-26T09:00:00", "2026-11-26T11:20:00") + "}");
        try (FakeDuffelServer monitorServer = new FakeDuffelServer()) {
            contextRunner.withPropertyValues(
                            "fare-provider.duffel.base-url=" + monitorServer.baseUrl(),
                            "fare-provider.duffel.api-key=monitor_token")
                    .run(context -> {
                        context.getBean(FlightOfferFinder.class).findOffer("off_a");

                        assertThat(server.lastRequest().headers().getFirst("Authorization"))
                                .isEqualTo("Bearer duffel_test_token");
                        assertThat(server.lastRequest().headers().getFirst("Duffel-Version")).isEqualTo("v2");
                        assertThat(monitorServer.lastRequest()).isNull();
                    });
        }
    }

    @Test
    void lowestFareFinder_감시설정그대로() throws IOException {
        try (FakeDuffelServer monitorServer = new FakeDuffelServer()) {
            contextRunner.withPropertyValues(
                            "fare-provider.duffel.base-url=" + monitorServer.baseUrl(),
                            "fare-provider.duffel.api-key=monitor_token")
                    .run(context -> {
                        context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

                        assertThat(monitorServer.lastRequest().path()).isEqualTo("/air/offer_requests");
                        assertThat(monitorServer.lastRequest().headers().getFirst("Authorization"))
                                .isEqualTo("Bearer monitor_token");
                        assertThat(server.lastRequest()).isNull();
                    });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "duffel_live_token", "monitor_token"})
    void bookingClient_키가duffel_test아니면_기동실패(String apiKey) {
        // 예매는 실제 업체에 주문·결제를 보내므로 테스트 모드 키가 아니면 띄우지 않는다
        contextRunner.withPropertyValues("fare-provider.duffel-booking.api-key=" + apiKey)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("fare-provider.duffel-booking.api-key")
                        .hasMessageContaining("duffel_test_"));
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
                            LocalDateTime.of(2026, 11, 26, 9, 0), LocalDateTime.of(2026, 11, 26, 11, 20), NO_TERMS),
                    new FlightOffer("off_b", new BigDecimal("300.00"), "USD", "ICN", "NRT", SeatGrade.ECONOMY,
                            "ZZ", "Duffel Airways", "1002",
                            LocalDateTime.of(2026, 11, 26, 13, 0), LocalDateTime.of(2026, 11, 26, 15, 20), NO_TERMS));
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
                    LocalDateTime.of(2026, 11, 26, 9, 0), LocalDateTime.of(2026, 11, 26, 11, 20), NO_TERMS));
            assertThat(server.lastRequest().method()).isEqualTo("GET");
            assertThat(server.lastRequest().path()).isEqualTo("/air/offers/off_a");
            assertThat(server.lastRequest().headers().getFirst("Authorization")).isEqualTo("Bearer duffel_test_token");
            assertThat(server.lastRequest().headers().getFirst("Duffel-Version")).isEqualTo("v2");
        });
    }

    @Test
    void findOffer_실측응답_승객ID와결제조건() throws IOException {
        // 실제 Duffel 테스트 모드 GET /air/offers/{id} 응답(ICN→NRT, ZZ hold 가능 offer)
        server.respondGzip(200, Map.of("x-request-id", "GOUAzVFl_5QHVFAAyDWB"), realBody("s01_get_offer.json"));

        contextRunner.run(context -> {
            FlightOffer offer = context.getBean(FlightOfferFinder.class).findOffer("off_034s2hWeuEx8vjWKm7mIaG")
                    .orElseThrow();

            assertThat(offer.terms()).isEqualTo(new OfferTerms(List.of("pas_0000BC7dtyHreavahTPDNK"), false,
                    Instant.parse("2026-11-05T01:38:21.510646Z"), "ZZ"));
            assertThat(offer.amount()).isEqualByComparingTo("76.04");
            assertThat(offer.flightNumber()).isEqualTo("6258");
        });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void errorBody_source문자열이어도_code와requestId(CapturedOutput output) throws IOException {
        // 실측 422: errors[].source가 문자열("duffel_airways")이다
        server.respondGzip(422, Map.of("x-request-id", "GOUBDY78pg3gHLwAEk-X"), realBody("s06_get_offer_lgw_lhr.json"));

        contextRunner.run(context -> {
            assertThat(context.getBean(FlightOfferFinder.class).findOffer("off_034s2ofz1gfLEhp6mR1YWG")).isEmpty();

            assertThat(output.getOut()).contains("WARN").contains("status=422").contains("type=airline_error")
                    .contains("code=offer_no_longer_available").contains("requestId=GOUBDY78pg3gHLwAEk-X");
        });
    }

    @Test
    void findOffer_비2xx_예외에code와requestId() {
        server.respond(500, Map.of("x-request-id", "FakeRequestId"), errorBody(500, "api_error", "internal_server_error"));

        contextRunner.run(context -> assertThatThrownBy(
                () -> context.getBean(FlightOfferFinder.class).findOffer("off_a"))
                .isInstanceOf(FareProviderUnavailableException.class)
                .hasMessageContaining("code=internal_server_error")
                .hasMessageContaining("requestId=FakeRequestId"));
    }

    @Test
    void findOffer_본문없는비2xx_헤더의requestId() {
        // 업체 앞단(프록시)이 JSON이 아닌 본문을 줄 때도 x-request-id로 추적한다
        server.respond(502, Map.of("x-request-id", "HeaderRequestId"), "<html>Bad Gateway</html>");

        contextRunner.run(context -> assertThatThrownBy(
                () -> context.getBean(FlightOfferFinder.class).findOffer("off_a"))
                .isInstanceOf(FareProviderUnavailableException.class)
                .hasMessageContaining("requestId=HeaderRequestId"));
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

    // 실측 raw 응답 본문(docs/experiments/duffel-test-mode/raw/의 response.body)
    private static byte[] realBody(String fileName) throws IOException {
        try (InputStream in = DuffelFlightBookingClientTest.class.getResourceAsStream("/duffel/real/" + fileName)) {
            return in.readAllBytes();
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
