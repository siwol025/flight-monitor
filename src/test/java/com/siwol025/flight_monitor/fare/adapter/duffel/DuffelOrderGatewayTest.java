package com.siwol025.flight_monitor.fare.adapter.duffel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.fare.FlightOrder;
import com.siwol025.flight_monitor.fare.FlightOrderException;
import com.siwol025.flight_monitor.fare.FlightOrderGateway;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.HoldOrderCommand;
import com.siwol025.flight_monitor.fare.OrderFailureKind;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Duffel hold 주문·조회·결제 어댑터. 업체는 FakeDuffelServer(실제 소켓)이고 응답 본문은 실측 raw를 쓴다.
 */
class DuffelOrderGatewayTest {

    private static final Passenger PASSENGER = new Passenger(PassengerTitle.MR, "Tony", "Stark", Gender.M,
            LocalDate.of(1980, 7, 24), "tony@example.com", "+442080160508");

    private static final HoldOrderCommand HOLD_COMMAND = new HoldOrderCommand("off_034s2kfz9DJGI85S3wAU0O",
            "pas_0000BC7e5QuMWIXPGop3iK", PASSENGER, "42");

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
                        "fare-provider.duffel.base-url=http://duffel.invalid");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void createHoldOrder_본문_hold_승객ID_metadata_payments없음() throws IOException {
        server.respondGzip(201, Map.of(), realBody("s03b_h1_hold_order.json"));

        contextRunner.run(context -> {
            context.getBean(FlightOrderGateway.class).createHoldOrder(HOLD_COMMAND);

            FakeDuffelServer.RecordedRequest request = server.lastRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/air/orders");
            // hold는 payments를 빼야 하고(넣으면 422), 승객 ID는 offer가 준 값 그대로, 예매 ID는 metadata 문자열
            assertThat(new ObjectMapper().readTree(request.body())).isEqualTo(new ObjectMapper().readTree("""
                    {"data":{"type":"hold","selected_offers":["off_034s2kfz9DJGI85S3wAU0O"],
                    "passengers":[{"id":"pas_0000BC7e5QuMWIXPGop3iK","title":"mr","given_name":"Tony",
                    "family_name":"Stark","gender":"m","born_on":"1980-07-24","email":"tony@example.com",
                    "phone_number":"+442080160508"}],
                    "metadata":{"booking_id":"42"}}}"""));
        });
    }

    @Test
    void createHoldOrder_201() throws IOException {
        // 실측 hold 201: 미결제(paid_at null), 결제 기한, 미발권(documents 빈 목록). metadata는 응답에 없었다
        server.respondGzip(201, Map.of(), realBody("s03b_h1_hold_order.json"));

        contextRunner.run(context -> {
            FlightOrder order = context.getBean(FlightOrderGateway.class).createHoldOrder(HOLD_COMMAND);

            assertThat(order).isEqualTo(new FlightOrder("ord_0000BC7e5XO0QzobMUjokT", "off_034s2kfz9DJGI85S3wAU0O",
                    "VNMYQL", new BigDecimal("31.18"), "USD", Instant.parse("2026-11-08T01:10:25Z"), null,
                    List.of(), null));
        });
    }

    @Test
    void findOrder_결제후_paidAt과항공권번호() throws IOException {
        server.respondGzip(200, Map.of(), realBody("s03b_p4_get_order_immediately_after_payment.json"));

        contextRunner.run(context -> {
            FlightOrder order = context.getBean(FlightOrderGateway.class).findOrder("ord_0000BC7e5XO0QzobMUjokT")
                    .orElseThrow();

            assertThat(order.paidAt()).isEqualTo(Instant.parse("2026-11-05T01:10:28Z"));
            assertThat(order.paymentRequiredBy()).isNull();
            assertThat(order.ticketNumbers()).containsExactly("0123456789");
            assertThat(server.lastRequest().method()).isEqualTo("GET");
            assertThat(server.lastRequest().path()).isEqualTo("/air/orders/ord_0000BC7e5XO0QzobMUjokT");
        });
    }

    @Test
    void findOrder_404_빈결과() {
        server.respond(404, Map.of(), errorBody(404, "invalid_request_error", "not_found"));

        contextRunner.run(context ->
                assertThat(context.getBean(FlightOrderGateway.class).findOrder("ord_unknown")).isEmpty());
    }

    @Test
    void findOrdersByOffer_한건과빈목록() throws IOException {
        // 결제·발권된 주문 1건이 있는 offer, 주문이 없는 offer
        server.enqueue("GET", "/air/orders", 200, realBody("s05_list_orders_by_offer_id.json"), Duration.ZERO);
        server.enqueue("GET", "/air/orders", 200, realBody("s05_list_orders_by_offer_id_unknown.json"), Duration.ZERO);

        contextRunner.run(context -> {
            FlightOrderGateway gateway = context.getBean(FlightOrderGateway.class);

            assertThat(gateway.findOrdersByOffer("off_034s2ihnp2h32TvfLm45ey")).containsExactly(new FlightOrder(
                    "ord_0000BC7dyMfgw3bZU8e5HM", "off_034s2ihnp2h32TvfLm45ey", "MSISKY", new BigDecimal("73.01"),
                    "USD", null, Instant.parse("2026-11-05T01:09:09Z"), List.of("1"), null));
            assertThat(gateway.findOrdersByOffer("off_0000000000000000000000")).isEmpty();
            assertThat(server.requests()).extracting(FakeDuffelServer.RecordedRequest::query).containsExactly(
                    "offer_id=off_034s2ihnp2h32TvfLm45ey", "offer_id=off_0000000000000000000000");
        });
    }

    @Test
    void payHeldOrder_본문_balance_금액표기그대로() throws IOException {
        server.respondGzip(201, Map.of(), realBody("s03b_p2b_payment_amount_format_3dp.json"));

        contextRunner.run(context -> {
            context.getBean(FlightOrderGateway.class).payHeldOrder("ord_0000BC7e5XO0QzobMUjokT",
                    new BigDecimal("31.180"), "USD", "booking-42");

            FakeDuffelServer.RecordedRequest request = server.lastRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/air/payments");
            assertThat(request.headers().getFirst("x-client-correlation-id")).isEqualTo("booking-42");
            // 금액은 받은 BigDecimal 표기 그대로(자릿수를 바꾸지 않는다) 문자열로 보낸다
            assertThat(new ObjectMapper().readTree(request.body())).isEqualTo(new ObjectMapper().readTree("""
                    {"data":{"order_id":"ord_0000BC7e5XO0QzobMUjokT",
                    "payment":{"type":"balance","amount":"31.180","currency":"USD"}}}"""));
        });
    }

    @Test
    void payHeldOrder_201() throws IOException {
        // 실측: 금액 표기 자릿수가 달라도(31.180) 같은 값이면 Duffel이 받는다
        server.respondGzip(201, Map.of(), realBody("s03b_p2b_payment_amount_format_3dp.json"));

        contextRunner.run(context -> {
            context.getBean(FlightOrderGateway.class).payHeldOrder("ord_0000BC7e5XO0QzobMUjokT",
                    new BigDecimal("31.18"), "USD", "booking-42");

            assertThat(server.requests()).hasSize(1);
        });
    }

    @Test
    void payHeldOrder_읽기타임아웃_UNKNOWN() throws IOException {
        // 결제 응답 타임아웃만 짧게: 조회 타임아웃(기본 15초)이 아니라 결제 타임아웃으로 끊는다
        server.enqueue("POST", "/air/payments", 201, realBody("s03b_p2b_payment_amount_format_3dp.json"),
                Duration.ofSeconds(2));

        contextRunner.withPropertyValues("fare-provider.duffel-booking.payment-read-timeout=300ms")
                .run(context -> {
                    assertThatThrownBy(() -> context.getBean(FlightOrderGateway.class)
                            .payHeldOrder("ord_1", new BigDecimal("31.18"), "USD", "booking-42"))
                            .isInstanceOfSatisfying(FlightOrderException.class,
                                    e -> assertThat(e.getKind()).isEqualTo(OrderFailureKind.UNKNOWN));
                    // 결과를 모르면 다시 보내지 않는다
                    assertThat(server.requests()).hasSize(1);
                });
    }

    @Test
    void payHeldOrder_502_UNKNOWN() {
        server.respond(502, Map.of(), errorBody(502, "airline_error", "airline_unknown"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(FlightOrderGateway.class)
                .payHeldOrder("ord_1", new BigDecimal("31.18"), "USD", "booking-42"))
                .isInstanceOfSatisfying(FlightOrderException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(OrderFailureKind.UNKNOWN);
                    assertThat(e.getCode()).isEqualTo("airline_unknown");
                }));
    }

    @Test
    void payHeldOrder_503_NOT_CREATED() {
        server.respond(503, Map.of(), errorBody(503, "api_error", "service_unavailable"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(FlightOrderGateway.class)
                .payHeldOrder("ord_1", new BigDecimal("31.18"), "USD", "booking-42"))
                .isInstanceOfSatisfying(FlightOrderException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OrderFailureKind.NOT_CREATED)));
    }

    @Test
    void payHeldOrder_연결실패_NOT_SENT() throws IOException {
        // 아무도 듣지 않는 포트: 연결이 거부돼 요청이 나가지 않는다
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        contextRunner.withPropertyValues("fare-provider.duffel-booking.base-url=http://127.0.0.1:" + closedPort)
                .run(context -> assertThatThrownBy(() -> context.getBean(FlightOrderGateway.class)
                        .payHeldOrder("ord_1", new BigDecimal("31.18"), "USD", "booking-42"))
                        .isInstanceOfSatisfying(FlightOrderException.class,
                                e -> assertThat(e.getKind()).isEqualTo(OrderFailureKind.NOT_SENT)));
    }

    // 13장 Duffel 표. status·code 쌍은 실측 raw(docs/experiments/duffel-test-mode/raw/)의 오류 응답이다
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "500, internal_server_error, UNKNOWN",
            "502, airline_unknown, UNKNOWN",                                     // s06_order_instant_lhr_lgw
            "504, gateway_timeout_error, UNKNOWN",                               // s06_search_stn_lhr_timeout
            "502, null, UNKNOWN",                                                // 본문이 JSON이 아닌 프록시 응답
            "429, rate_limit_exceeded, RATE_LIMITED",
            "503, service_unavailable, NOT_CREATED",
            "422, offer_no_longer_available, OFFER_UNAVAILABLE",                 // s06_order_hold_lhr_stn
            "422, not_found, OFFER_UNAVAILABLE",                                 // s07_order_unknown_offer
            "422, expired, OFFER_EXPIRED",                                       // s09a_order_expired_offer_hold
            "422, offer_request_already_booked, OFFER_ALREADY_BOOKED",           // s02_c1_reorder_same_offer
            "422, validation_required, REJECTED",                                // s02_a1_order_instant_without_passenger_id
            "422, payment_amount_does_not_match_order_amount, REJECTED",         // s03b_p1_payment_wrong_amount
            "422, insufficient_balance, REJECTED",                               // s06_order_instant_lgw_stn
            "422, order_type_not_eligible_for_payment, REJECTED",                // s03b_p7_payment_twice
            "401, access_token_not_found, CONFIGURATION",                        // s07_auth_fake_token
            "403, unavailable_feature, CONFIGURATION",                           // s07_3ds_session_empty
            "400, missing_version_header, CONFIGURATION",                        // s07_missing_version
            "406, unsupported_format, CONFIGURATION"                             // s07_bad_accept
    })
    void classify_실측code별(int status, String code, OrderFailureKind expected) {
        assertThat(DuffelErrorClassifier.classify(status, code)).isEqualTo(expected);
    }

    // 실측 raw 응답 본문(docs/experiments/duffel-test-mode/raw/의 response.body)
    private static byte[] realBody(String fileName) throws IOException {
        try (InputStream in = DuffelOrderGatewayTest.class.getResourceAsStream("/duffel/real/" + fileName)) {
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
