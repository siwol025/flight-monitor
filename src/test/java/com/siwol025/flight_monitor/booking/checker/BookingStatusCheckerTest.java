package com.siwol025.flight_monitor.booking.checker;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.idempotency.IdempotencyService;
import com.siwol025.flight_monitor.booking.messaging.DuffelPaymentPublisher;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.booking.saga.DuffelPaymentStep;
import com.siwol025.flight_monitor.booking.service.BookingPolicy;
import com.siwol025.flight_monitor.booking.service.BookingStateWriter;
import com.siwol025.flight_monitor.booking.service.CheckoutService;
import com.siwol025.flight_monitor.booking.service.PaymentConfirmService;
import com.siwol025.flight_monitor.booking.support.TestBookings;
import com.siwol025.flight_monitor.fare.FlightOrderGateway;
import com.siwol025.flight_monitor.fare.adapter.duffel.DuffelHttpClientConfig;
import com.siwol025.flight_monitor.fare.adapter.duffel.FakeDuffelServer;
import com.siwol025.flight_monitor.global.config.ClockConfig;
import com.siwol025.flight_monitor.global.config.JacksonConfig;
import com.siwol025.flight_monitor.payment.adapter.toss.FakeTossServer;
import com.siwol025.flight_monitor.payment.adapter.toss.TossHttpClientConfig;
import com.siwol025.flight_monitor.support.ComposeMysql;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확인 스케줄러. 업체는 FakeDuffelServer·FakeTossServer(실제 소켓, 실측 raw 본문), 상태·멈춤 판정은 compose와 같은 MySQL
 * (DB NOW(6))에서 검사한다. 주기 실행 대신 check()를 직접 부른다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
@Import({BookingStatusChecker.class, CheckoutService.class, PaymentConfirmService.class, DuffelPaymentStep.class,
        BookingStateWriter.class, IdempotencyService.class, BookingPolicy.class, DuffelHttpClientConfig.class,
        TossHttpClientConfig.class, JacksonConfig.class, ClockConfig.class,
        BookingStatusCheckerTest.RecordingPublisherConfig.class})
@TestPropertySource(properties = {
        "fare-provider.duffel-booking.api-key=duffel_test_token",
        "payment.toss.secret-key=test_sk_dummy",
        "payment.toss.client-key=test_ck_dummy",
        "booking.checker.stale-after=2m",
        "booking.checker.quiet-period=10m"})
class BookingStatusCheckerTest {

    private static final FakeDuffelServer DUFFEL = startDuffel();
    private static final FakeTossServer TOSS = startToss();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ExecutorService THREADS = Executors.newFixedThreadPool(2);
    private static final String PAYMENTS = "/air/payments";
    // 실측 A 시나리오(Toss 2단계)의 결제: 주문번호·금액(1,000원)을 예매에 맞춘다
    private static final String PAYMENT_KEY = "tviva261105bA5BpSbob3xNp";
    private static final String TOSS_ORDER_ID = "fm-20261105-A-08210ed0d4";
    private static final String TOSS_PAYMENT_PATH = "/v1/payments/" + PAYMENT_KEY;

    static class RecordingPublisher implements DuffelPaymentPublisher {
        final List<Long> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(long bookingId) {
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
        registry.add("fare-provider.duffel-booking.base-url", DUFFEL::baseUrl);
        registry.add("payment.toss.base-url", TOSS::baseUrl);
    }

    // 앱의 @EnableScheduling이 이 컨텍스트에도 걸려, 실제 스케줄러면 기동 직후 check()가 백그라운드에서 한 번 돌아 테스트의
    // check()와 업체 가짜 응답을 다툰다. 아무것도 실행하지 않는 스케줄러로 바꿔 check()는 테스트가 부를 때만 돈다
    @MockitoBean
    private TaskScheduler taskScheduler;

    @Autowired
    private BookingStatusChecker checker;

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
    private CheckoutService checkoutService;

    @Autowired
    private PaymentConfirmService paymentConfirmService;

    @Autowired
    private FlightOrderGateway flightOrderGateway;

    private long bookingId;

    @BeforeEach
    void setUp() {
        DUFFEL.reset();
        TOSS.reset();
        publisher.published.clear();
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM bookings");
        bookingId = TestBookings.held(bookingRepository, bookingStateWriter,
                TestBookings.newUser(userRepository, Role.USER), "off_checker");
    }

    @Test
    void checker_UNCONFIRMED_paid_documents_TICKETED() {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED, 601);
        ticketedOrder();

        checker.check();

        Booking booking = booking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.TICKETED);
        assertThat(booking.getTicketNumbers()).isEqualTo("0123456789");
        assertThat(DUFFEL.count("POST", PAYMENTS)).isZero();
    }

    @Test
    void checker_UNCONFIRMED_미결제_보류유지_WARN_POST0회(CapturedOutput output) {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED, 601);
        unpaidOrder();
        unpaidOrder();

        // 여러 주기가 지나도 재결제하지 않는다
        checker.check();
        checker.check();

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isZero();
        assertThat(DUFFEL.count("GET", TestBookings.ORDER_PATH)).isEqualTo(2);
        assertThat(output.getOut()).contains("보류 예매가 미결제로 확인됨(재결제하지 않고 보류 유지");
    }

    @Test
    void checker_UNCONFIRMED_조회실패_보류유지() {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED, 601);
        DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 500, error(500, "api_error", "internal_server_error"),
                Duration.ZERO);

        checker.check();

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isZero();
    }

    @Test
    void checker_UNCONFIRMED_quietPeriod전_조회안함() {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED, 590);

        checker.check();

        assertThat(DUFFEL.requests()).isEmpty();
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
    }

    @Test
    void checker_APPROVED멈춤_재발행() {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.PAYMENT_APPROVED, 121);
        long fresh = TestBookings.held(bookingRepository, bookingStateWriter,
                TestBookings.newUser(userRepository, Role.USER), "off_fresh");
        TestBookings.moveTo(jdbc, fresh, BookingStatus.PAYMENT_APPROVED, 10);

        checker.check();

        // 방금 승인된 예매는 아직 소비자가 처리할 시간이다
        assertThat(publisher.published).containsExactly(bookingId);
        assertThat(DUFFEL.requests()).isEmpty();
    }

    @Test
    void checker_CONFIRMING멈춤_조회DONE_APPROVED() {
        confirming(121);
        TOSS.enqueue("GET", TOSS_PAYMENT_PATH, 200, toss("A_145215-a8bd_06_get_after_done_0.json"));

        checker.check();

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isZero();
        assertThat(publisher.published).containsExactly(bookingId);
    }

    @Test
    void checker_CONFIRMING멈춤_조회IN_PROGRESS_같은키재승인() {
        String tossKey = confirming(121);
        TOSS.enqueue("GET", TOSS_PAYMENT_PATH, 200, toss("A_145215-a8bd_01_get_before_confirm.json"));
        TOSS.confirmSucceeds(toss("A_145215-a8bd_02_confirm_k1.json"));

        checker.check();

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(TOSS.count("POST", FakeTossServer.CONFIRM_PATH)).isEqualTo(1);
        assertThat(TOSS.lastRequest().headers().getFirst("Idempotency-Key")).isEqualTo(tossKey);
        assertThat(TOSS.approvedCount()).isEqualTo(1);
    }

    @Test
    void checker_CONFIRMING_stale전_조회안함() {
        confirming(30);

        checker.check();

        assertThat(TOSS.requests()).isEmpty();
    }

    @Test
    void checker_HOLD_REQUESTED_목록발견_HELD() {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.HOLD_REQUESTED, 121);
        DUFFEL.enqueue("GET", "/air/orders", 200, bytes(orderList(String.valueOf(bookingId))), Duration.ZERO);

        checker.check();

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(DUFFEL.count("POST", "/air/orders")).isZero();
        assertThat(DUFFEL.requests()).singleElement().extracting(FakeDuffelServer.RecordedRequest::query)
                .isEqualTo("offer_id=off_checker");
    }

    @Test
    void checker_HOLD_REQUESTED_목록없음_보류유지() {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.HOLD_REQUESTED, 121);
        DUFFEL.enqueue("GET", "/air/orders", 200, real("s05_list_orders_by_offer_id_unknown.json"), Duration.ZERO);

        checker.check();

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.HOLD_REQUESTED);
        assertThat(DUFFEL.count("POST", "/air/orders")).isZero();
    }

    @Test
    void checker_두서버동시실행_전이1회() throws Exception {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED, 601);
        ticketedOrder();
        ticketedOrder();
        AtomicInteger ticketed = new AtomicInteger();
        BookingStatusChecker serverA = server(ticketed);
        BookingStatusChecker serverB = server(ticketed);
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> {
            await(start);
            serverA.check();
        }, THREADS);
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> {
            await(start);
            serverB.check();
        }, THREADS);
        CompletableFuture.allOf(a, b).get(10, TimeUnit.SECONDS);

        assertThat(ticketed.get()).isEqualTo(1);
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.TICKETED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isZero();
    }

    // 같은 DB·업체를 보는 다른 서버. 발권 전이가 실제로 바꾼(영향 행 1) 횟수를 센다
    private BookingStatusChecker server(AtomicInteger ticketed) {
        BookingStateWriter countingWriter = new BookingStateWriter(bookingRepository) {
            @Override
            public boolean ticket(long id, Instant paidAt, List<String> ticketNumbers) {
                boolean changed = super.ticket(id, paidAt, ticketNumbers);
                if (changed) {
                    ticketed.incrementAndGet();
                }
                return changed;
            }
        };
        return new BookingStatusChecker(bookingRepository, checkoutService, paymentConfirmService, publisher,
                new DuffelPaymentStep(flightOrderGateway, countingWriter, bookingRepository),
                Duration.ofMinutes(2), Duration.ofMinutes(10));
    }

    // 승인 결과를 모르는 채 멈춘 예매(PAYMENT_CONFIRMING). Toss 멱등키를 돌려준다
    private String confirming(long secondsAgo) {
        jdbc.update("UPDATE bookings SET toss_order_id = ?, charge_amount = 1000, payment_key = ? WHERE id = ?",
                TOSS_ORDER_ID, PAYMENT_KEY, bookingId);
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.PAYMENT_CONFIRMING, secondsAgo);
        return booking().getTossIdempotencyKey();
    }

    private void unpaidOrder() {
        DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 200, real("s03b_h1_hold_order.json"), Duration.ZERO);
    }

    private void ticketedOrder() {
        DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 200,
                real("s03b_p4_get_order_immediately_after_payment.json"), Duration.ZERO);
    }

    private Booking booking() {
        return bookingRepository.findById(bookingId).orElseThrow();
    }

    // 실측 hold 주문에 우리 예매 ID를 남긴 offer_id 목록 응답
    private static ObjectNode orderList(String ourBookingId) {
        try {
            ObjectNode order = (ObjectNode) JSON.readTree(real("s03b_h1_hold_order.json")).get("data");
            order.putObject("metadata").put("booking_id", ourBookingId);
            ObjectNode root = JSON.createObjectNode();
            root.putArray("data").add(order);
            root.putObject("meta").put("limit", 50);
            return root;
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

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] error(int status, String type, String code) {
        return """
                {"errors":[{"type":"%s","code":"%s","title":"Error","message":"Error message."}],
                "meta":{"request_id":"FakeRequestId","status":%d}}""".formatted(type, code, status)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] real(String name) {
        try (InputStream in = BookingStatusCheckerTest.class.getResourceAsStream("/duffel/real/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toss(String name) {
        try (InputStream in = BookingStatusCheckerTest.class.getResourceAsStream("/toss/real/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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

    private static FakeTossServer startToss() {
        try {
            return new FakeTossServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
