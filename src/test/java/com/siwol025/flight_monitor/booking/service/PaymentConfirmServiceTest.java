package com.siwol025.flight_monitor.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.dto.request.PaymentConfirmRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingStatusResponse;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyKeyRepository;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyProperties;
import com.siwol025.flight_monitor.payment.PaymentGateway;
import com.siwol025.flight_monitor.payment.PaymentSnapshot;
import com.siwol025.flight_monitor.payment.PaymentStatus;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyService;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Fresh;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Replay;
import com.siwol025.flight_monitor.booking.messaging.DuffelPaymentPublisher;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.OfferTerms;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.global.config.JacksonConfig;
import com.siwol025.flight_monitor.global.exception.ErrorResponse;
import com.siwol025.flight_monitor.payment.adapter.toss.FakeTossServer;
import com.siwol025.flight_monitor.payment.adapter.toss.TossHttpClientConfig;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 승인(⑤). 결제사는 FakeTossServer(실제 소켓, 실측 멱등 의미론, 실측 raw 본문)이고 멱등키·조건부 갱신은 compose와 같은
 * MySQL에서 검사한다. 서버 여러 대의 경합을 재현하려고 테스트 트랜잭션 없이(각 단계가 바로 커밋) 돌린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PaymentConfirmService.class, IdempotencyService.class, BookingStateWriter.class, TossHttpClientConfig.class,
        JacksonConfig.class, PaymentConfirmServiceTest.RecordingPublisherConfig.class})
@TestPropertySource(properties = {
        "payment.toss.secret-key=test_sk_dummy",
        "payment.toss.client-key=test_ck_dummy",
        "payment.toss.confirm-read-timeout=1s"})
class PaymentConfirmServiceTest {

    // 실측 A 시나리오(2단계)의 결제: 주문번호·금액(1,000원)을 예매에 맞춰 둔다
    private static final String PAYMENT_KEY = "tviva261105bA5BpSbob3xNp";
    private static final String ORDER_ID = "fm-20261105-A-08210ed0d4";
    private static final long CHARGE_AMOUNT = 1000;
    private static final String PAYMENT_PATH = "/v1/payments/" + PAYMENT_KEY;
    private static final FakeTossServer TOSS = startToss();
    private static final ObjectMapper JSON = new ObjectMapper();
    // 두 서버를 흉내 내는 전용 스레드(공용 풀이 다른 작업으로 밀려 경합이 사라지지 않게)
    private static final ExecutorService THREADS = Executors.newFixedThreadPool(2);
    private static final String FAILED_INTERNAL =
            "{\"code\":\"FAILED_INTERNAL_SYSTEM_PROCESSING\",\"message\":\"내부 시스템 처리 작업이 실패했습니다.\"}";

    static class RecordingPublisher implements DuffelPaymentPublisher {
        final List<Long> published = new CopyOnWriteArrayList<>();
        volatile boolean fail;

        @Override
        public void publish(long bookingId) {
            if (fail) {
                throw new IllegalStateException("브로커 연결 실패");
            }
            published.add(bookingId);
        }
    }

    @TestConfiguration
    static class RecordingPublisherConfig {
        @Bean
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ComposeMysql.registerSharedDataSource(registry);
        registry.add("payment.toss.base-url", TOSS::baseUrl);
    }

    @Autowired
    private PaymentConfirmService paymentConfirmService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingStateWriter bookingStateWriter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingPublisher publisher;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private IdempotencyProperties idempotencyProperties;

    @Autowired
    private PaymentConfirmProperties confirmProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private String key;
    private long bookingId;
    private String tossKey;

    @BeforeEach
    void setUp() {
        TOSS.reset();
        publisher.published.clear();
        publisher.fail = false;
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM bookings");
        user = newUser();
        key = UUID.randomUUID().toString();
        tossKey = UUID.randomUUID().toString();
        bookingId = heldBooking(user, ORDER_ID, tossKey);
    }

    @Test
    void confirm_DONE_PAYMENT_APPROVED_발행() {
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class))
                .isEqualTo(new BookingStatusResponse(bookingId, BookingStatus.PAYMENT_APPROVED));
        Booking booking = booking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(booking.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(booking.getTossApprovedAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 5, 52, 16));
        // ④에서 저장한 Toss 키와 저장한 금액으로 승인했다
        assertThat(TOSS.lastRequest().headers().getFirst("Idempotency-Key")).isEqualTo(tossKey);
        assertThat(TOSS.lastRequest().body()).contains("\"amount\":1000");
        assertThat(TOSS.approvedCount()).isEqualTo(1);
        assertThat(publisher.published).containsExactly(bookingId);
    }

    @Test
    void confirm_금액불일치_Toss호출없이400() {
        // Toss는 결제창과 다른 금액을 FORBIDDEN_REQUEST로 거절한다(실측 B_02). 우리 쪽에서 먼저 막는다
        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT + 1000));

        assertThat(freshBody(result, 400, ErrorResponse.class).tag()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
        assertThat(TOSS.requests()).isEmpty();
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void confirm_카드거절_HELD_새주문번호새키() {
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 403,
                "{\"code\":\"REJECT_CARD_COMPANY\",\"message\":\"결제 승인이 거절되었습니다.\"}");

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 400, ErrorResponse.class))
                .isEqualTo(new ErrorResponse("PAYMENT_REJECTED", "결제 승인이 거절되었습니다."));
        Booking booking = booking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        // Toss 주문번호는 재사용하지 않는다(DUPLICATED_ORDER_ID). 새 결제는 새 Toss 키로 승인한다
        assertThat(booking.getTossOrderId()).startsWith("fm-").isNotEqualTo(ORDER_ID);
        assertThat(booking.getTossIdempotencyKey()).isNotBlank().isNotEqualTo(tossKey);
        assertThat(booking.getPaymentKey()).isNull();
        assertThat(publisher.published).isEmpty();
        // 확정 실패는 저장해 같은 키에 그대로 돌려준다(Toss 재호출 없음)
        assertThat(paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT)))
                .isInstanceOfSatisfying(Replay.class, replay -> assertThat(replay.status()).isEqualTo(400));
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
    }

    @Test
    void confirm_승인기한지남_HELD_409() {
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 400, real("D_145129-58eb_04_confirm_t11min.json"));

        assertError(paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT)), 409, "PAYMENT_EXPIRED");
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(booking().getTossOrderId()).isNotEqualTo(ORDER_ID);
    }

    @Test
    void confirm_타임아웃주입_조회DONE_재요청0회() {
        // 읽기 타임아웃(1s) 뒤에도 Toss는 승인을 끝낸다(실측 G_01)
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.confirmDelay(Duration.ofMillis(1500));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_06_get_after_done_0.json"));

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
        assertThat(TOSS.count("GET", PAYMENT_PATH)).isEqualTo(1);
        assertThat(booking().getTossApprovedAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 5, 52, 16));
        assertThat(publisher.published).containsExactly(bookingId);
    }

    @Test
    void confirm_타임아웃주입_조회IN_PROGRESS_같은키재요청_DONE재생() {
        // 조회 시점에는 아직 승인 전이다. 같은 키로 다시 보내면 Toss는 끝난 첫 승인을 재생한다(다시 처리하지 않음)
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.confirmDelay(Duration.ofMillis(1200));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(confirmKeys()).containsExactly(tossKey, tossKey);
        assertThat(TOSS.approvedCount()).isEqualTo(1);
    }

    @Test
    void confirm_조회ABORTED_HELD() {
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 500,
                "{\"code\":\"UNKNOWN_PAYMENT_ERROR\",\"message\":\"결제에 실패했어요.\"}");
        TOSS.enqueue("GET", PAYMENT_PATH, 200, withStatus(real("A_145215-a8bd_01_get_before_confirm.json"), "ABORTED"));

        assertError(paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT)), 400, "PAYMENT_REJECTED");
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(booking().getTossOrderId()).isNotEqualTo(ORDER_ID);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
    }

    @Test
    void confirm_조회도실패_202저장_PAYMENT_CONFIRMING보류() {
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 500,
                "{\"code\":\"FAILED_INTERNAL_SYSTEM_PROCESSING\",\"message\":\"m\"}");
        TOSS.enqueue("GET", PAYMENT_PATH, 500, "");

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        // 결과를 모르는 채로 재요청하지 않는다. 확인은 스케줄러가 이어 간다
        assertThat(freshBody(result, 202, BookingStatusResponse.class))
                .isEqualTo(new BookingStatusResponse(bookingId, BookingStatus.PAYMENT_CONFIRMING));
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.PAYMENT_CONFIRMING);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM idempotency_keys WHERE idem_key = ?", String.class, key))
                .isEqualTo("COMPLETED");
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void confirm_200본문해석실패_UNKNOWN과같게_조회후판단() {
        // 200을 받았지만 모르는 status라 해석하지 못했다: 승인됐을 수 있으므로 조회로만 확인한다
        TOSS.confirmSucceeds(withStatus(real("A_145215-a8bd_02_confirm_k1.json"), "SOMETHING_NEW"));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_06_get_after_done_0.json"));

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
    }

    @Test
    void confirm_409처리중_조회후같은키() throws Exception {
        // 같은 Toss 키의 이전 요청(응답을 못 받은 시도)이 아직 처리 중이다: 409 → 조회로 미승인 확인 → 같은 키로 다시
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.confirmDelay(Duration.ofMillis(300));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));
        CompletableFuture<PaymentSnapshot> previous = CompletableFuture.supplyAsync(
                () -> paymentGateway.confirm(PAYMENT_KEY, ORDER_ID, CHARGE_AMOUNT, tossKey));
        while (TOSS.requests().isEmpty()) {
            Thread.onSpinWait();
        }

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(previous.get(5, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.DONE);
        assertThat(confirmKeys()).containsExactly(tossKey, tossKey, tossKey);
        assertThat(TOSS.count("GET", PAYMENT_PATH)).isEqualTo(1);
        assertThat(TOSS.approvedCount()).isEqualTo(1);
    }

    @Test
    void confirm_5xx재생중_조회IN_PROGRESS_새키로재승인() {
        // D4: 같은 키는 첫 5xx만 재생한다(1단계 s06b2). 미승인을 두 번 확인했으니 새 키로 보낸다
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 500, FAILED_INTERNAL);
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        String newKey = booking().getTossIdempotencyKey();
        assertThat(newKey).isNotEqualTo(tossKey);
        assertThat(confirmKeys()).containsExactly(tossKey, tossKey, newKey);
        assertThat(TOSS.approvedCount()).isEqualTo(1);
    }

    @Test
    void confirm_같은키409재생_조회IN_PROGRESS_새키로재승인() {
        // D4 확장: Toss가 409 IDEMPOTENT_REQUEST_PROCESSING을 이 키에 저장해 계속 재생한다(E2E Test-Code 실측).
        // 같은 키로 한 번 더 보내도 409이고 조회가 IN_PROGRESS면 새 키로 보낸다
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 409, real("G_145517-22fd_02_confirm_k1_concurrent_x.json"));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        String newKey = booking().getTossIdempotencyKey();
        assertThat(newKey).isNotEqualTo(tossKey);
        assertThat(confirmKeys()).containsExactly(tossKey, tossKey, newKey);
        assertThat(TOSS.approvedCount()).isEqualTo(1);
    }

    @Test
    void confirm_새키재승인_ALREADY_PROCESSED_조회로DONE() {
        // 새 키 승인이 ALREADY_PROCESSED_PAYMENT면 그사이 승인된 것이다. 다시 조회로 확인한다(이중 청구 없음)
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 500, FAILED_INTERNAL);
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 400, real("A_145215-a8bd_04_confirm_no_key.json"));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_06_get_after_done_0.json"));

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(3);
        assertThat(TOSS.count("GET", PAYMENT_PATH)).isEqualTo(3);
        assertThat(TOSS.approvedCount()).isZero();
        assertThat(publisher.published).containsExactly(bookingId);
    }

    @Test
    void confirm_인라인재요청소진_202보류() {
        // 같은 키 재요청이 계속 처리 중(409)이면 인라인 2회 뒤 보류한다(이후 확인은 스케줄러)
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.confirmDelay(Duration.ofSeconds(5));
        for (int i = 0; i < 3; i++) {
            TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));
        }

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_CONFIRMING);
        assertThat(confirmKeys()).containsExactly(tossKey, tossKey, tossKey);
        assertThat(booking().getTossIdempotencyKey()).isEqualTo(tossKey);
    }

    @Test
    void confirm_설정오류_조회IN_PROGRESS로미승인확인_HELD복귀_503() {
        // 인증 거절(빈 본문 403)은 우리 설정 문제다. 미승인을 확인한 뒤에만 HELD로 돌리고 키를 지운다(같은 키로 다시 처리)
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 403, "");
        TOSS.enqueue("GET", PAYMENT_PATH, 200, real("A_145215-a8bd_01_get_before_confirm.json"));

        assertError(paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT)), 503,
                "PAYMENT_PROVIDER_UNAVAILABLE");
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(booking().getTossOrderId()).isEqualTo(ORDER_ID);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_keys", Integer.class)).isZero();
    }

    @Test
    void confirm_202뒤같은키재요청_저장된202_Toss호출0회() {
        TOSS.enqueue("POST", FakeTossServer.CONFIRM_PATH, 500, FAILED_INTERNAL);
        TOSS.enqueue("GET", PAYMENT_PATH, 500, "");
        paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));
        int calls = TOSS.requests().size();

        IdempotentResult<Object> again = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(again).isEqualTo(new Replay<>(202,
                "{\"bookingId\":" + bookingId + ",\"status\":\"PAYMENT_CONFIRMING\"}"));
        assertThat(TOSS.requests()).hasSize(calls);
    }

    @Test
    void confirm_successUrl새로고침_같은키_저장응답_Toss호출1회() {
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        IdempotentResult<Object> again = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        assertThat(again).isEqualTo(new Replay<>(202,
                "{\"bookingId\":" + bookingId + ",\"status\":\"PAYMENT_APPROVED\"}"));
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
        assertThat(publisher.published).containsExactly(bookingId);
    }

    @Test
    void confirm_두서버동시같은키_Toss호출1회() throws Exception {
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.confirmDelay(Duration.ofMillis(500));
        PaymentConfirmService serverB = secondServer();
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<IdempotentResult<Object>> a = CompletableFuture.supplyAsync(() -> {
            await(start);
            return paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));
        }, THREADS);
        CompletableFuture<IdempotentResult<Object>> b = CompletableFuture.supplyAsync(() -> {
            await(start);
            return serverB.confirm(user, key, request(CHARGE_AMOUNT));
        }, THREADS);

        // 한 서버만 처리하고, 다른 서버는 처리 중(409) 또는 저장된 결과 재생이다(둘 다 Toss를 부르지 않는다)
        List<String> kinds = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).stream()
                .map(result -> result.getClass().getSimpleName()).toList();
        assertThat(kinds).containsOnlyOnce("Fresh");
        assertThat(kinds).allMatch(kind -> List.of("Fresh", "Processing", "Replay").contains(kind));
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
        assertThat(TOSS.approvedCount()).isEqualTo(1);
    }

    @Test
    void confirm_다른키동시_예매상태전이로_Toss호출1회() throws Exception {
        // 버튼 연타 등으로 우리 API 키가 달라도 HELD → PAYMENT_CONFIRMING 조건부 UPDATE가 한 요청만 Toss를 부르게 한다
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        TOSS.confirmDelay(Duration.ofMillis(500));
        PaymentConfirmService serverB = secondServer();
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<IdempotentResult<Object>> a = CompletableFuture.supplyAsync(() -> {
            await(start);
            return paymentConfirmService.confirm(user, UUID.randomUUID().toString(), request(CHARGE_AMOUNT));
        }, THREADS);
        CompletableFuture<IdempotentResult<Object>> b = CompletableFuture.supplyAsync(() -> {
            await(start);
            return serverB.confirm(user, UUID.randomUUID().toString(), request(CHARGE_AMOUNT));
        }, THREADS);

        List<BookingStatus> statuses = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).stream()
                .map(result -> freshBody(result, 202, BookingStatusResponse.class).status()).toList();
        // 진 요청은 Toss를 부르지 않고 현재 상태(승인 중)만 돌려준다
        assertThat(statuses).containsExactlyInAnyOrder(BookingStatus.PAYMENT_APPROVED,
                BookingStatus.PAYMENT_CONFIRMING);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
        assertThat(publisher.published).containsExactly(bookingId);
    }

    @Test
    void confirm_남의예매주문번호_404_Toss호출없음() {
        IdempotentResult<Object> result = paymentConfirmService.confirm(newUser(), key, request(CHARGE_AMOUNT));

        assertError(result, 404, "BOOKING_NOT_FOUND");
        assertThat(TOSS.requests()).isEmpty();
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void confirm_발행실패해도_승인202() {
        TOSS.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        publisher.fail = true;

        IdempotentResult<Object> result = paymentConfirmService.confirm(user, key, request(CHARGE_AMOUNT));

        // 승인은 커밋됐다. 멈춘 PAYMENT_APPROVED는 확인 스케줄러가 다시 발행한다
        assertThat(freshBody(result, 202, BookingStatusResponse.class).status())
                .isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.PAYMENT_APPROVED);
    }

    private PaymentConfirmService secondServer() {
        return new PaymentConfirmService(paymentGateway,
                new IdempotencyService(idempotencyKeyRepository, idempotencyProperties, objectMapper),
                new BookingStateWriter(bookingRepository), bookingRepository, publisher, confirmProperties,
                objectMapper);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> confirmKeys() {
        return TOSS.requests().stream().filter(r -> r.path().equals(FakeTossServer.CONFIRM_PATH))
                .map(r -> r.headers().getFirst("Idempotency-Key")).toList();
    }

    private static void assertError(IdempotentResult<Object> result, int status, String tag) {
        assertThat(freshBody(result, status, ErrorResponse.class).tag()).isEqualTo(tag);
    }

    private static String withStatus(String paymentJson, String status) {
        try {
            ObjectNode node = (ObjectNode) JSON.readTree(paymentJson);
            node.put("status", status);
            return JSON.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Booking booking() {
        return bookingRepository.findById(bookingId).orElseThrow();
    }

    private User newUser() {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.builder().email(unique + "@test.com").name("예매자")
                .provider(Provider.GOOGLE).providerId(unique).role(Role.USER).build());
    }

    // ④를 마친 예매(HELD): 고객 결제 금액·Toss 주문번호·Toss 멱등키가 고정돼 있다
    private long heldBooking(User owner, String tossOrderId, String tossIdempotencyKey) {
        FlightOffer offer = new FlightOffer("off_" + UUID.randomUUID(), new BigDecimal("0.72"), "USD", "JFK", "EWR",
                SeatGrade.ECONOMY, "ZZ", "Duffel Airways", "1234", LocalDateTime.of(2026, 12, 3, 9, 0),
                LocalDateTime.of(2026, 12, 3, 10, 0),
                new OfferTerms(List.of("pas_1"), false, Instant.parse("2026-12-01T00:00:00Z"), "ZZ"));
        Passenger passenger = new Passenger(PassengerTitle.MR, "Tony", "Stark", Gender.M, LocalDate.of(1990, 1, 1),
                "tony@example.com", "+442080160508");
        long id = bookingRepository.save(Booking.holdRequested(owner, offer, passenger)).getId();
        bookingStateWriter.hold(id, "ord_" + UUID.randomUUID(), "VNMYQL", Instant.parse("2026-11-08T01:10:25Z"),
                CHARGE_AMOUNT, tossOrderId, tossIdempotencyKey);
        return id;
    }

    private static PaymentConfirmRequest request(long amount) {
        return new PaymentConfirmRequest(ORDER_ID, PAYMENT_KEY, amount);
    }

    private static <T> T freshBody(IdempotentResult<Object> result, int status, Class<T> type) {
        assertThat(result).isInstanceOf(Fresh.class);
        Fresh<Object> fresh = (Fresh<Object>) result;
        assertThat(fresh.response().getStatusCode().value()).isEqualTo(status);
        return type.cast(fresh.response().getBody());
    }

    static String real(String name) {
        try (InputStream in = PaymentConfirmServiceTest.class.getResourceAsStream("/toss/real/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FakeTossServer startToss() {
        try {
            return new FakeTossServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
