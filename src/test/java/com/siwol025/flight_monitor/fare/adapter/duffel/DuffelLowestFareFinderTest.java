package com.siwol025.flight_monitor.fare.adapter.duffel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siwol025.flight_monitor.fare.FareProviderConfigurationException;
import com.siwol025.flight_monitor.fare.FareProviderUnavailableException;
import com.siwol025.flight_monitor.fare.FareRateLimitedException;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.fare.LowestFare;
import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

class DuffelLowestFareFinderTest {

    private static final RouteKey ICN_NRT_ECONOMY =
            new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY);

    // Duffel 오류 본문 형식: errors[](type·code·title·message) + meta(request_id·status)
    private static final String RATE_LIMIT_ERROR_BODY = """
            {"errors":[{"type":"rate_limit_error","code":"rate_limit_exceeded","title":"Too many requests",
            "message":"You have exceeded the rate limit.","documentation_url":"https://duffel.com/docs/api/overview/errors"}],
            "meta":{"request_id":"FakeRequestId","status":429}}""";

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

    private static String errorBody(int status, String type, String code) {
        return """
                {"errors":[{"type":"%s","code":"%s","title":"Error","message":"Error message.",
                "documentation_url":"https://duffel.com/docs/api/overview/errors"}],
                "meta":{"request_id":"FakeRequestId","status":%d}}""".formatted(type, code, status);
    }

    @Test
    void 요청변환_노선키_slices와cabinClass와supplierTimeout으로_변환됨() {
        contextRunner.run(context -> {
            context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

            FakeDuffelServer.RecordedRequest request = server.lastRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/air/offer_requests");
            // supplier_timeout(밀리초)과 return_offers는 본문이 아니라 쿼리 매개변수다(관문 G)
            assertThat(request.query().split("&"))
                    .containsExactlyInAnyOrder("return_offers=true", "supplier_timeout=10000");
            // 기대 본문 = 관문 G에서 실제 Duffel에 보낸 요청 본문
            JSONAssert.assertEquals("""
                    {"data":{"slices":[{"origin":"ICN","destination":"NRT","departure_date":"2026-11-26"}],
                    "passengers":[{"type":"adult"}],"cabin_class":"economy"}}""",
                    request.body(), JSONCompareMode.STRICT);
        });
    }

    @Test
    void 요청헤더_인증토큰과_DuffelVersion_포함됨() {
        contextRunner.run(context -> {
            context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

            FakeDuffelServer.RecordedRequest request = server.lastRequest();
            assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer duffel_test_token");
            assertThat(request.headers().getFirst("Duffel-Version")).isEqualTo("v2");
            assertThat(request.headers().getFirst("Accept-Encoding")).contains("gzip");
        });
    }

    @Test
    void 실제응답_여러운임중_최저가선택_항공편정보매핑됨() throws IOException {
        // 관문 G에서 저장한 실제 응답(ICN→NRT, 운임 51건)을 실제 Duffel처럼 gzip으로 내려보낸다
        byte[] realResponse;
        try (InputStream in = getClass().getResourceAsStream("/duffel/real-offer-request-response.json")) {
            realResponse = in.readAllBytes();
        }
        server.respondGzip(201, Map.of("ratelimit-limit", "30"), realResponse);

        contextRunner.run(context -> {
            Optional<LowestFare> lowestFare = context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

            // 기대값은 응답 파일을 따로 확인한 값: 51건 중 최저가는 1건뿐인 72.40 USD
            assertThat(lowestFare).contains(new LowestFare(new BigDecimal("72.40"), "USD", "ZZ", "Duffel Airways",
                    "1079", LocalDateTime.of(2026, 11, 26, 19, 21), LocalDateTime.of(2026, 11, 26, 21, 36)));
        });
    }

    @Test
    void 응답_최저가가경유편_도착시각은마지막구간() {
        // 최저가(95.50)가 2구간 운임: 항공사·편명·출발은 첫 구간, 도착은 마지막 구간
        server.respond(201, Map.of(), """
                {"data":{"offers":[
                  {"total_amount":"120.00","total_currency":"USD","slices":[{"segments":[
                    {"marketing_carrier":{"iata_code":"KE","name":"Korean Air"},"marketing_carrier_flight_number":"701",
                     "departing_at":"2026-11-26T09:00:00","arriving_at":"2026-11-26T11:20:00"}]}]},
                  {"total_amount":"95.50","total_currency":"USD","slices":[{"segments":[
                    {"marketing_carrier":{"iata_code":"MU","name":"China Eastern"},"marketing_carrier_flight_number":"5042",
                     "departing_at":"2026-11-26T08:10:00","arriving_at":"2026-11-26T09:30:00"},
                    {"marketing_carrier":{"iata_code":"MU","name":"China Eastern"},"marketing_carrier_flight_number":"523",
                     "departing_at":"2026-11-26T12:40:00","arriving_at":"2026-11-26T17:05:00"}]}]}
                ]}}""");

        contextRunner.run(context -> {
            Optional<LowestFare> lowestFare = context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

            assertThat(lowestFare).contains(new LowestFare(new BigDecimal("95.50"), "USD", "MU", "China Eastern",
                    "5042", LocalDateTime.of(2026, 11, 26, 8, 10), LocalDateTime.of(2026, 11, 26, 17, 5)));
        });
    }

    @Test
    void 응답_운임없음_빈결과() {
        server.respond(201, Map.of(), "{\"data\":{\"offers\":[]}}");

        contextRunner.run(context ->
                assertThat(context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY)).isEmpty());
    }

    @Test
    void 응답429_resetAt파싱_한도초과예외() {
        server.respond(429, Map.of("ratelimit-limit", "30", "ratelimit-remaining", "0",
                "ratelimit-reset", "Sun, 25 Oct 2026 16:08:00 GMT"), RATE_LIMIT_ERROR_BODY);

        contextRunner.run(context -> {
            LowestFareFinder finder = context.getBean(LowestFareFinder.class);

            assertThatThrownBy(() -> finder.findLowestFare(ICN_NRT_ECONOMY))
                    .isInstanceOfSatisfying(FareRateLimitedException.class, e ->
                            assertThat(e.getResetAt()).isEqualTo(Instant.parse("2026-10-25T16:08:00Z")));
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "not-a-date")
    void 응답429_reset헤더없음_60초후재개(String resetHeader) {
        // 헤더가 없거나 해석할 수 없으면 문서의 기간(60초) 뒤를 재개 시각으로 한다(재개 시각은 비지 않는다)
        Map<String, String> headers = resetHeader == null ? Map.of() : Map.of("ratelimit-reset", resetHeader);
        server.respond(429, headers, RATE_LIMIT_ERROR_BODY);

        contextRunner
                .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-10-25T16:07:44Z"), ZoneOffset.UTC))
                .run(context -> {
                    LowestFareFinder finder = context.getBean(LowestFareFinder.class);

                    assertThatThrownBy(() -> finder.findLowestFare(ICN_NRT_ECONOMY))
                            .isInstanceOfSatisfying(FareRateLimitedException.class, e ->
                                    assertThat(e.getResetAt()).isEqualTo(Instant.parse("2026-10-25T16:08:44Z")));
                });
    }

    @Test
    void 응답422_잘못된노선예외() {
        server.respond(422, Map.of(), errorBody(422, "validation_error", "invalid"));

        contextRunner.run(context -> {
            LowestFareFinder finder = context.getBean(LowestFareFinder.class);

            assertThatThrownBy(() -> finder.findLowestFare(ICN_NRT_ECONOMY)).isInstanceOf(InvalidRouteException.class);
        });
    }

    @Test
    void 응답401_설정오류예외() {
        server.respond(401, Map.of(), errorBody(401, "authentication_error", "unauthorized"));

        contextRunner.run(context -> {
            LowestFareFinder finder = context.getBean(LowestFareFinder.class);

            assertThatThrownBy(() -> finder.findLowestFare(ICN_NRT_ECONOMY))
                    .isInstanceOf(FareProviderConfigurationException.class);
        });
    }

    @Test
    void 응답5xx_업체장애예외() {
        server.respond(503, Map.of(), errorBody(503, "api_error", "service_unavailable"));

        contextRunner.run(context -> {
            LowestFareFinder finder = context.getBean(LowestFareFinder.class);

            assertThatThrownBy(() -> finder.findLowestFare(ICN_NRT_ECONOMY))
                    .isInstanceOf(FareProviderUnavailableException.class);
        });
    }

    @Test
    void 타임아웃_업체장애예외() {
        // 운영 응답 타임아웃(15초) 대신 짧게 주입하고, 서버는 그보다 오래 지연한다
        server.delay(Duration.ofSeconds(2));

        contextRunner.withPropertyValues("fare-provider.duffel.response-timeout=300ms").run(context -> {
            LowestFareFinder finder = context.getBean(LowestFareFinder.class);

            assertThatThrownBy(() -> finder.findLowestFare(ICN_NRT_ECONOMY))
                    .isInstanceOf(FareProviderUnavailableException.class);
        });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void ratelimitLimit헤더_설정과다름_경고로그(CapturedOutput output) {
        // 비교 대상은 앱 호출 한도가 아니라 업체 한도 expected-rate-limit(기본 30)
        server.respond(201, Map.of("ratelimit-limit", "50"), "{\"data\":{\"offers\":[]}}");

        contextRunner.run(context -> {
            context.getBean(LowestFareFinder.class).findLowestFare(ICN_NRT_ECONOMY);

            assertThat(output.getOut()).contains("WARN").contains("ratelimit-limit(50)")
                    .contains("expected-rate-limit(30)");
        });
    }

    @Test
    void 커넥션풀이_maxInFlight보다작음_기동실패() {
        // 풀 크기는 새 속성이 아니라 기존 monitor.http.max-connections를 읽는다
        contextRunner
                .withPropertyValues("monitor.http.max-connections=6", "monitor.backpressure.max-in-flight=7")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("max-connections(6)").hasMessageContaining("max-in-flight(7)"));
    }

    @ParameterizedTest
    @CsvSource({"ECONOMY, economy", "BUSINESS, business", "FIRST, first"})
    void 좌석등급매핑_ECONOMY_BUSINESS_FIRST_cabinClass로변환(SeatGrade seatGrade, String cabinClass) {
        contextRunner.run(context -> {
            context.getBean(LowestFareFinder.class)
                    .findLowestFare(new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), seatGrade));

            JSONAssert.assertEquals("{\"data\":{\"cabin_class\":\"" + cabinClass + "\"}}",
                    server.lastRequest().body(), JSONCompareMode.LENIENT);
        });
    }
}
