package com.siwol025.flight_monitor.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.request.BookingRequest;
import com.siwol025.flight_monitor.booking.dto.request.PassengerRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingDetailResponse;
import com.siwol025.flight_monitor.booking.dto.response.BookingStatusResponse;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyKeyRepository;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyProperties;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyService;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Fresh;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.KeyReused;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Replay;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.FlightOrderGateway;
import com.siwol025.flight_monitor.fare.adapter.duffel.DuffelHttpClientConfig;
import com.siwol025.flight_monitor.fare.adapter.duffel.FakeDuffelServer;
import com.siwol025.flight_monitor.global.config.JacksonConfig;
import com.siwol025.flight_monitor.global.exception.ErrorResponse;
import com.siwol025.flight_monitor.global.exception.PriceChangedErrorResponse;
import com.siwol025.flight_monitor.support.ComposeMysql;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매(④) = Duffel hold 주문. 업체는 FakeDuffelServer(실제 소켓, 실측 raw 본문)이고, 멱등키·조건부 갱신은 compose와 같은
 * MySQL에서 검사한다. 서버 여러 대의 경합을 재현하려고 테스트 트랜잭션 없이(각 단계가 바로 커밋) 돌린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CheckoutService.class, IdempotencyService.class, BookingStateWriter.class, BookingPolicy.class,
        DuffelHttpClientConfig.class, JacksonConfig.class, CheckoutServiceTest.FixedClockConfig.class})
@TestPropertySource(properties = {
        "fare-provider.duffel-booking.api-key=duffel_test_token",
        "fare-provider.duffel-booking.hold-read-timeout=1s",
        "payment.toss.client-key=test_ck_dummy"})
class CheckoutServiceTest {

    // 실측 offer(s01_get_offer.json): ZZ, 76.04 USD, 만료 2026-11-05T01:38:21Z, 출발 2026-12-03
    private static final String OFFER_ID = "off_034s2hWeuEx8vjWKm7mIaG";
    private static final String PASSENGER_ID = "pas_0000BC7dtyHreavahTPDNK";
    private static final String ORDER_ID = "ord_0000BC7e5XO0QzobMUjokT";
    private static final FakeDuffelServer DUFFEL = startDuffel();
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestConfiguration
    static class FixedClockConfig {
        // offer 만료 38분 전
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-05T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ComposeMysql.registerSharedDataSource(registry);
        registry.add("fare-provider.duffel-booking.base-url", DUFFEL::baseUrl);
    }

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FlightOfferFinder flightOfferFinder;

    @Autowired
    private FlightOrderGateway flightOrderGateway;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private IdempotencyProperties idempotencyProperties;

    @Autowired
    private BookingPolicy bookingPolicy;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private User user;
    private String key;

    @BeforeEach
    void setUp() {
        DUFFEL.reset();
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM bookings");
        String unique = UUID.randomUUID().toString();
        user = userRepository.save(User.builder().email(unique + "@test.com").name("예매자")
                .provider(Provider.GOOGLE).providerId(unique).role(Role.USER).build());
        key = UUID.randomUUID().toString();
    }

    @Test
    void purchase_정상_HELD와원화금액과Toss키저장() throws Exception {
        enqueueOffer(offer -> {
        });
        enqueueHold(201);

        IdempotentResult<Object> result = checkoutService.purchase(user, key, request("76.04"));

        BookingDetailResponse body = freshBody(result, 201, BookingDetailResponse.class);
        assertThat(body.status()).isEqualTo(BookingStatus.HELD);
        assertThat(body.orderId()).isEqualTo(ORDER_ID);
        assertThat(body.bookingReference()).isEqualTo("VNMYQL");
        // 76.04 USD × 1400 = 106,456원(원 단위 올림)
        assertThat(body.chargeAmount()).isEqualTo(106_456L);
        assertThat(body.paymentRequiredBy()).isEqualTo(Instant.parse("2026-11-08T01:10:25Z"));
        assertThat(body.payment().amount()).isEqualTo(106_456L);
        assertThat(body.payment().currency()).isEqualTo("KRW");
        assertThat(body.payment().clientKey()).isEqualTo("test_ck_dummy");
        assertThat(body.payment().customerKey()).matches("fmc_[0-9a-f]{32}");
        assertThat(body.payment().orderId()).startsWith("fm-");

        Booking booking = bookingRepository.findById(body.bookingId()).orElseThrow();
        assertThat(booking.getTossOrderId()).isEqualTo(body.payment().orderId());
        assertThat(booking.getTossIdempotencyKey()).isNotBlank();
        assertThat(booking.getProviderPassengerId()).isEqualTo(PASSENGER_ID);
        assertThat(booking.getStatusChangedAt()).isNotNull();
        // hold 요청: offer가 준 승객 ID, 우리 예매 ID(metadata)
        String holdBody = DUFFEL.requests().stream()
                .filter(r -> r.method().equals("POST") && r.path().equals("/air/orders")).findFirst().orElseThrow()
                .body();
        assertThat(JSON.readTree(holdBody).at("/data/passengers/0/id").asText()).isEqualTo(PASSENGER_ID);
        assertThat(JSON.readTree(holdBody).at("/data/metadata/booking_id").asText())
                .isEqualTo(String.valueOf(body.bookingId()));
    }

    @Test
    void purchase_같은키재요청_hold재호출없이_저장응답() {
        enqueueOffer(offer -> {
        });
        enqueueHold(201);
        BookingDetailResponse first = freshBody(checkoutService.purchase(user, key, request("76.04")), 201,
                BookingDetailResponse.class);

        IdempotentResult<Object> again = checkoutService.purchase(user, key, request("76.04"));

        assertThat(again).isInstanceOfSatisfying(Replay.class, replay -> {
            assertThat(replay.status()).isEqualTo(201);
            assertThat(replay.body()).contains("\"bookingId\":" + first.bookingId()).contains("\"status\":\"HELD\"");
        });
        assertThat(DUFFEL.count("GET", "/air/offers/" + OFFER_ID)).isEqualTo(1);
        assertThat(DUFFEL.count("POST", "/air/orders")).isEqualTo(1);
    }

    @Test
    void purchase_두서버같은키동시_hold1회() throws Exception {
        // 같은 DB를 보는 두 서버. hold가 끝나기 전에 다른 서버가 같은 키를 받는다
        CheckoutService serverA = checkoutService;
        CheckoutService serverB = new CheckoutService(flightOfferFinder, flightOrderGateway,
                new IdempotencyService(idempotencyKeyRepository, idempotencyProperties, objectMapper),
                new BookingStateWriter(bookingRepository), bookingRepository, bookingPolicy, transactionManager,
                objectMapper, clock, "test_ck_dummy");
        enqueueOffer(offer -> {
        });
        enqueueOffer(offer -> {
        });
        DUFFEL.enqueue("POST", "/air/orders", 201, bytes(heldOrder()), Duration.ofMillis(500));
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<IdempotentResult<Object>> a = CompletableFuture.supplyAsync(() -> {
            await(start);
            return serverA.purchase(user, key, request("76.04"));
        });
        CompletableFuture<IdempotentResult<Object>> b = CompletableFuture.supplyAsync(() -> {
            await(start);
            return serverB.purchase(user, key, request("76.04"));
        });

        assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                .extracting(result -> result.getClass().getSimpleName())
                .containsExactlyInAnyOrder("Fresh", "Processing");
        assertThat(DUFFEL.count("POST", "/air/orders")).isEqualTo(1);
    }

    @Test
    void purchase_같은키다른본문_422() {
        enqueueOffer(offer -> {
        });
        enqueueHold(201);
        checkoutService.purchase(user, key, request("76.04"));

        assertThat(checkoutService.purchase(user, key, request("76.040", LocalDate.of(1991, 1, 1))))
                .isInstanceOf(KeyReused.class);
        assertThat(DUFFEL.count("POST", "/air/orders")).isEqualTo(1);
    }

    @Test
    void purchase_hold불가_409() {
        enqueueOffer(offer -> ((ObjectNode) offer.get("payment_requirements")).put("requires_instant_payment", true));

        assertError(checkoutService.purchase(user, key, request("76.04")), 409, "OFFER_NOT_HOLDABLE");
        assertThat(DUFFEL.count("POST", "/air/orders")).isZero();
        // 확정 4xx는 FAILED로 저장해 같은 키에 그대로 돌려준다
        assertThat(checkoutService.purchase(user, key, request("76.04"))).isInstanceOfSatisfying(Replay.class,
                replay -> assertThat(replay.status()).isEqualTo(409));
    }

    @Test
    void purchase_허용외항공사_409() {
        enqueueOffer(offer -> ((ObjectNode) offer.get("owner")).put("iata_code", "KE"));

        assertError(checkoutService.purchase(user, key, request("76.04")), 409, "OFFER_NOT_HOLDABLE");
        assertThat(DUFFEL.count("POST", "/air/orders")).isZero();
    }

    @Test
    void purchase_만료임박_hold안함_409() {
        // 지금(01:00:00)부터 2분 안에 만료된다
        enqueueOffer(offer -> offer.put("expires_at", "2026-11-05T01:01:59Z"));

        assertError(checkoutService.purchase(user, key, request("76.04")), 409, "OFFER_EXPIRED");
        assertThat(DUFFEL.count("POST", "/air/orders")).isZero();
        assertThat(bookingCount()).isZero();
    }

    @Test
    void purchase_가격변동_409현재가() {
        enqueueOffer(offer -> {
        });

        IdempotentResult<Object> result = checkoutService.purchase(user, key, request("70.00"));

        PriceChangedErrorResponse body = freshBody(result, 409, PriceChangedErrorResponse.class);
        assertThat(body.tag()).isEqualTo("OFFER_PRICE_CHANGED");
        assertThat(body.currentAmount()).isEqualByComparingTo("76.04");
        assertThat(body.currentCurrency()).isEqualTo("USD");
        assertThat(DUFFEL.count("POST", "/air/orders")).isZero();
    }

    @Test
    void purchase_미성년_400_hold안함() {
        enqueueOffer(offer -> {
        });

        // 출발일(2026-12-03)에 하루 모자라 만 17세
        assertError(checkoutService.purchase(user, key, request("76.04", LocalDate.of(2008, 12, 4))), 400,
                "PASSENGER_NOT_ADULT");
        assertThat(DUFFEL.count("POST", "/air/orders")).isZero();
        assertThat(bookingCount()).isZero();
    }

    @Test
    void purchase_hold422_FAILED() {
        enqueueOffer(offer -> {
        });
        DUFFEL.enqueue("POST", "/air/orders", 422,
                errorBody(422, "validation_error", "invalid_phone_number"), Duration.ZERO);

        assertError(checkoutService.purchase(user, key, request("76.04")), 400, "ORDER_REJECTED");

        assertThat(onlyBooking().getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(onlyBooking().getFailureCode()).isEqualTo("invalid_phone_number");
        assertThat(checkoutService.purchase(user, key, request("76.04"))).isInstanceOfSatisfying(Replay.class,
                replay -> assertThat(replay.status()).isEqualTo(400));
        assertThat(DUFFEL.count("POST", "/air/orders")).isEqualTo(1);
    }

    @Test
    void purchase_hold503_생성안됨_FAILED_503_같은키재처리가능() {
        // 업체가 만들지 않았다고 확인한 일시 장애(503): 예매를 끝내고 키를 지워 같은 키로 다시 처리하게 한다
        enqueueOffer(offer -> {
        });
        DUFFEL.enqueue("POST", "/air/orders", 503, errorBody(503, "api_error", "service_unavailable"), Duration.ZERO);

        assertError(checkoutService.purchase(user, key, request("76.04")), 503, "FARE_PROVIDER_UNAVAILABLE");
        assertThat(onlyBooking().getStatus()).isEqualTo(BookingStatus.FAILED);

        enqueueOffer(offer -> {
        });
        enqueueHold(201);
        freshBody(checkoutService.purchase(user, key, request("76.04")), 201, BookingDetailResponse.class);
    }

    @Test
    void purchase_hold타임아웃_POST재요청0회_목록조회로HELD() {
        enqueueOffer(offer -> {
        });
        // 응답 타임아웃(1s)보다 늦게 응답한다. 업체에는 hold가 생겼다
        DUFFEL.enqueue("POST", "/air/orders", 201, bytes(heldOrder()), Duration.ofSeconds(2));
        DUFFEL.enqueue("GET", "/air/orders", 200, () -> bytes(orderList(holdBookingId())), Duration.ZERO);

        BookingDetailResponse body = freshBody(checkoutService.purchase(user, key, request("76.04")), 201,
                BookingDetailResponse.class);

        assertThat(body.status()).isEqualTo(BookingStatus.HELD);
        assertThat(body.orderId()).isEqualTo(ORDER_ID);
        assertThat(DUFFEL.count("POST", "/air/orders")).isEqualTo(1);
        assertThat(DUFFEL.requests()).filteredOn(r -> r.method().equals("GET") && r.path().equals("/air/orders"))
                .singleElement().extracting(FakeDuffelServer.RecordedRequest::query)
                .isEqualTo("offer_id=" + OFFER_ID);
    }

    @Test
    void purchase_hold타임아웃_목록없음_202저장_HOLD_REQUESTED() {
        enqueueOffer(offer -> {
        });
        DUFFEL.enqueue("POST", "/air/orders", 201, bytes(heldOrder()), Duration.ofSeconds(2));
        DUFFEL.enqueue("GET", "/air/orders", 200, bytes(readJson("s05_list_orders_by_offer_id_unknown.json")),
                Duration.ZERO);

        IdempotentResult<Object> result = checkoutService.purchase(user, key, request("76.04"));

        BookingStatusResponse body = freshBody(result, 202, BookingStatusResponse.class);
        assertThat(body).isEqualTo(new BookingStatusResponse(onlyBooking().getId(), BookingStatus.HOLD_REQUESTED));
        assertThat(onlyBooking().getStatus()).isEqualTo(BookingStatus.HOLD_REQUESTED);
        assertThat(DUFFEL.count("POST", "/air/orders")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM idempotency_keys WHERE idem_key = ?", String.class, key))
                .isEqualTo("COMPLETED");
    }

    @Test
    void purchase_hold201본문해석실패_POST재요청0회_목록조회로확인() {
        // 201을 받았지만 본문을 읽지 못했다: hold는 생겼을 수 있으므로 결과 불명확과 같게 조회로만 확인한다
        enqueueOffer(offer -> {
        });
        DUFFEL.enqueue("POST", "/air/orders", 201, "not json".getBytes(StandardCharsets.UTF_8), Duration.ZERO);
        DUFFEL.enqueue("GET", "/air/orders", 200, bytes(readJson("s05_list_orders_by_offer_id_unknown.json")),
                Duration.ZERO);

        IdempotentResult<Object> result = checkoutService.purchase(user, key, request("76.04"));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status()).isEqualTo(BookingStatus.HOLD_REQUESTED);
        assertThat(DUFFEL.count("POST", "/air/orders")).isEqualTo(1);
        assertThat(DUFFEL.count("GET", "/air/orders")).isEqualTo(1);
    }

    @Test
    void purchase_202뒤같은키재요청_저장된202_외부호출0회() {
        enqueueOffer(offer -> {
        });
        DUFFEL.enqueue("POST", "/air/orders", 201, bytes(heldOrder()), Duration.ofSeconds(2));
        DUFFEL.enqueue("GET", "/air/orders", 200, bytes(readJson("s05_list_orders_by_offer_id_unknown.json")),
                Duration.ZERO);
        long bookingId = freshBody(checkoutService.purchase(user, key, request("76.04")), 202,
                BookingStatusResponse.class).bookingId();
        DUFFEL.reset();

        IdempotentResult<Object> again = checkoutService.purchase(user, key, request("76.04"));

        assertThat(again).isEqualTo(new Replay<>(202,
                "{\"bookingId\":" + bookingId + ",\"status\":\"HOLD_REQUESTED\"}"));
        assertThat(DUFFEL.requests()).isEmpty();
    }

    @Test
    void purchase_stale재선점_booking있음_hold재호출0회() {
        enqueueOffer(offer -> {
        });
        DUFFEL.enqueue("POST", "/air/orders", 201, bytes(heldOrder()), Duration.ofSeconds(2));
        DUFFEL.enqueue("GET", "/air/orders", 200, bytes(readJson("s05_list_orders_by_offer_id_unknown.json")),
                Duration.ZERO);
        long bookingId = freshBody(checkoutService.purchase(user, key, request("76.04")), 202,
                BookingStatusResponse.class).bookingId();
        // 처리 서버가 hold를 보낸 뒤 결과를 저장하기 전에 죽었다(2분 넘게 갱신 없음, 예매 ID는 키에 남아 있다)
        jdbc.update("""
                UPDATE idempotency_keys SET status = 'PROCESSING', response_status = NULL, response_body = NULL,
                       updated_at = NOW(6) - INTERVAL 121 SECOND
                 WHERE idem_key = ?""", key);
        DUFFEL.reset();

        IdempotentResult<Object> result = checkoutService.purchase(user, key, request("76.04"));

        // 외부 호출 없이 그 예매의 현재 상태를 응답한다(확인은 스케줄러)
        assertThat(freshBody(result, 202, BookingStatusResponse.class))
                .isEqualTo(new BookingStatusResponse(bookingId, BookingStatus.HOLD_REQUESTED));
        assertThat(DUFFEL.requests()).isEmpty();
        assertThat(bookingCount()).isEqualTo(1);
    }

    private static void assertError(IdempotentResult<Object> result, int status, String tag) {
        assertThat(freshBody(result, status, ErrorResponse.class).tag()).isEqualTo(tag);
    }

    private Booking onlyBooking() {
        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(1);
        return bookings.getFirst();
    }

    private int bookingCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bookings", Integer.class);
    }

    // 처리 중인 hold 요청이 업체 주문에 남긴 우리 예매 ID
    private static String holdBookingId() {
        String holdBody = DUFFEL.requests().stream()
                .filter(r -> r.method().equals("POST") && r.path().equals("/air/orders")).findFirst().orElseThrow()
                .body();
        try {
            return JSON.readTree(holdBody).at("/data/metadata/booking_id").asText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // offer_id 목록 조회 응답: hold 주문 1건(우리 예매 ID를 metadata에 남김)
    private static ObjectNode orderList(String bookingId) {
        ObjectNode order = (ObjectNode) heldOrder().get("data");
        order.putObject("metadata").put("booking_id", bookingId);
        ObjectNode root = JSON.createObjectNode();
        root.putArray("data").add(order);
        root.putObject("meta").put("limit", 50);
        return root;
    }

    private static byte[] errorBody(int status, String type, String code) {
        return ("""
                {"errors":[{"type":"%s","code":"%s","title":"t","message":"m"}],
                "meta":{"status":%d,"request_id":"req_test"}}""").formatted(type, code, status)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T freshBody(IdempotentResult<Object> result, int status, Class<T> type) {
        assertThat(result).isInstanceOf(Fresh.class);
        Fresh<Object> fresh = (Fresh<Object>) result;
        assertThat(fresh.response().getStatusCode().value()).isEqualTo(status);
        return type.cast(fresh.response().getBody());
    }

    private static BookingRequest request(String amount) {
        return request(amount, LocalDate.of(1990, 1, 1));
    }

    private static BookingRequest request(String amount, LocalDate bornOn) {
        return new BookingRequest(OFFER_ID, new BigDecimal(amount), "USD",
                new PassengerRequest("mr", "Tony", "Stark", "m", bornOn, "tony@example.com", "+442080160508"));
    }

    // 실측 offer 본문을 고쳐 재조회 응답으로 둔다
    private static void enqueueOffer(Consumer<ObjectNode> edit) {
        ObjectNode root = (ObjectNode) readJson("s01_get_offer.json");
        edit.accept((ObjectNode) root.get("data"));
        DUFFEL.enqueue("GET", "/air/offers/" + OFFER_ID, 200, bytes(root), Duration.ZERO);
    }

    private static void enqueueHold(int status) {
        DUFFEL.enqueue("POST", "/air/orders", status, bytes(heldOrder()), Duration.ZERO);
    }

    // 실측 hold 201 본문(s03b_h1_hold_order.json)을 이 offer의 주문으로 맞춘다(실측은 다른 offer였다)
    private static ObjectNode heldOrder() {
        ObjectNode root = (ObjectNode) readJson("s03b_h1_hold_order.json");
        ObjectNode data = (ObjectNode) root.get("data");
        data.put("offer_id", OFFER_ID);
        data.put("total_amount", "76.04");
        return root;
    }

    private static com.fasterxml.jackson.databind.JsonNode readJson(String fileName) {
        try (InputStream in = CheckoutServiceTest.class.getResourceAsStream("/duffel/real/" + fileName)) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(Object json) {
        try {
            return JSON.writeValueAsBytes(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FakeDuffelServer startDuffel() {
        try {
            return new FakeDuffelServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
