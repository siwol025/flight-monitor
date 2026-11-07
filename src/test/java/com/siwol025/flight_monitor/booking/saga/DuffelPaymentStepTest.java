package com.siwol025.flight_monitor.booking.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.booking.service.BookingStateWriter;
import com.siwol025.flight_monitor.booking.support.TestBookings;
import com.siwol025.flight_monitor.fare.FlightOrderException;
import com.siwol025.flight_monitor.fare.OrderFailureKind;
import com.siwol025.flight_monitor.fare.adapter.duffel.DuffelHttpClientConfig;
import com.siwol025.flight_monitor.fare.adapter.duffel.FakeDuffelServer;
import com.siwol025.flight_monitor.global.config.JacksonConfig;
import com.siwol025.flight_monitor.support.ComposeMysql;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duffel hold 결제와 발권 확인(⑥). 업체는 FakeDuffelServer(실제 소켓, 실측 raw 본문)이고 조건부 갱신은 compose와 같은
 * MySQL에서 검사한다. 리스너 재시도는 handle을 다시 부르는 것과 같으므로 여기서는 직접 다시 부른다(큐는 통합 테스트).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DuffelPaymentStep.class, BookingStateWriter.class, DuffelHttpClientConfig.class, JacksonConfig.class})
@TestPropertySource(properties = {
        "fare-provider.duffel-booking.api-key=duffel_test_token",
        "fare-provider.duffel-booking.payment-read-timeout=1s"})
class DuffelPaymentStepTest {

    private static final FakeDuffelServer DUFFEL = startDuffel();
    private static final String PAYMENTS = "/air/payments";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ComposeMysql.registerSharedDataSource(registry);
        registry.add("fare-provider.duffel-booking.base-url", DUFFEL::baseUrl);
    }

    @Autowired
    private DuffelPaymentStep step;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingStateWriter bookingStateWriter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long bookingId;

    @BeforeEach
    void setUp() {
        DUFFEL.reset();
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM bookings");
        bookingId = TestBookings.held(bookingRepository, bookingStateWriter,
                TestBookings.newUser(userRepository, Role.USER), "off_step");
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.PAYMENT_APPROVED, 0);
    }

    @Test
    void pay_조회후결제_documents_TICKETED() throws Exception {
        unpaidOrder();
        paymentSucceeds();
        ticketedOrder();

        step.handle(bookingId);

        Booking booking = booking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.TICKETED);
        assertThat(booking.getTicketNumbers()).isEqualTo("0123456789");
        assertThat(booking.getDuffelPaidAt()).isEqualTo(LocalDateTime.of(2026, 11, 5, 1, 10, 28));
        // 결제 금액·통화는 주문 조회의 total_amount 표기 그대로, 상관 ID는 예매 ID
        FakeDuffelServer.RecordedRequest payment = DUFFEL.requests().stream()
                .filter(r -> r.path().equals(PAYMENTS)).findFirst().orElseThrow();
        assertThat(new ObjectMapper().readTree(payment.body())).isEqualTo(new ObjectMapper().readTree("""
                {"data":{"order_id":"ord_0000BC7e5XO0QzobMUjokT",
                "payment":{"type":"balance","amount":"31.18","currency":"USD"}}}"""));
        assertThat(payment.headers().getFirst("x-client-correlation-id")).isEqualTo(String.valueOf(bookingId));
        assertThat(DUFFEL.requests()).extracting(FakeDuffelServer.RecordedRequest::method)
                .containsExactly("GET", "POST", "GET");
    }

    @Test
    void pay_이미paid면_결제안함_TICKETED() {
        ticketedOrder();
        ticketedOrder();

        step.handle(bookingId);

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.TICKETED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isZero();
    }

    @Test
    void pay_타임아웃주입_UNCONFIRMED_POST재요청0회() {
        unpaidOrder();
        DUFFEL.enqueue("POST", PAYMENTS, 201, real("s03b_p2b_payment_amount_format_3dp.json"), Duration.ofSeconds(2));

        step.handle(bookingId);
        // 메시지 재전달·리스너 재시도와 같다
        step.handle(bookingId);

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(1);
    }

    @Test
    void pay_500_UNCONFIRMED_예외없음_POST1회() {
        unpaidOrder();
        DUFFEL.enqueue("POST", PAYMENTS, 500, error(500, "api_error", "internal_server_error"), Duration.ZERO);

        // 예외를 던지지 않는다: 리스너가 재시도하지 않는다
        step.handle(bookingId);
        step.handle(bookingId);

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(1);
    }

    @Test
    void pay_503_PAYMENT_APPROVED로되돌리고_예외_재시도때POST2회() {
        unpaidOrder();
        DUFFEL.enqueue("POST", PAYMENTS, 503, error(503, "api_error", "service_unavailable"), Duration.ZERO);

        assertThatThrownBy(() -> step.handle(bookingId)).isInstanceOfSatisfying(FlightOrderException.class,
                e -> assertThat(e.getKind()).isEqualTo(OrderFailureKind.NOT_CREATED));
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.PAYMENT_APPROVED);

        unpaidOrder();
        paymentSucceeds();
        ticketedOrder();
        step.handle(bookingId);

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.TICKETED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(2);
    }

    @Test
    void pay_DUFFEL_PAYING에서재시작_UNCONFIRMED_POST0회() {
        // 이전 소비자가 결제 도중 죽었다
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.DUFFEL_PAYING, 0);

        step.handle(bookingId);

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        assertThat(DUFFEL.requests()).isEmpty();
    }

    @Test
    void pay_결제전GET실패_재시도_POST0회() {
        DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 500, error(500, "api_error", "internal_server_error"),
                Duration.ZERO);

        assertThatThrownBy(() -> step.handle(bookingId)).isInstanceOf(RuntimeException.class);

        // 아직 결제를 보내지 않았다: 재시도가 다시 가져갈 수 있게 되돌린다
        assertThat(booking().getStatus()).isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isZero();
    }

    @Test
    void pay_422거절_failureCode_DUFFEL_PAYING유지_재전달에도POST1회() {
        unpaidOrder();
        DUFFEL.enqueue("POST", PAYMENTS, 422, error(422, "invalid_state_error", "insufficient_balance"),
                Duration.ZERO);

        step.handle(bookingId);
        step.handle(bookingId);

        Booking booking = booking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYING);
        assertThat(booking.getFailureCode()).isEqualTo("insufficient_balance");
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(1);
    }

    @Test
    void pay_결제후조회에documents없음_UNCONFIRMED_예외없음() {
        unpaidOrder();
        paymentSucceeds();
        unpaidOrder();

        step.handle(bookingId);

        assertThat(booking().getStatus()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(1);
    }

    @Test
    void pay_결제할상태아니면_외부호출없음() {
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.TICKETED, 0);

        step.handle(bookingId);

        assertThat(DUFFEL.requests()).isEmpty();
    }

    private void unpaidOrder() {
        DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 200, real("s03b_h1_hold_order.json"), Duration.ZERO);
    }

    private void ticketedOrder() {
        DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 200,
                real("s03b_p4_get_order_immediately_after_payment.json"), Duration.ZERO);
    }

    private void paymentSucceeds() {
        DUFFEL.enqueue("POST", PAYMENTS, 201, real("s03b_p2b_payment_amount_format_3dp.json"), Duration.ZERO);
    }

    private Booking booking() {
        return bookingRepository.findById(bookingId).orElseThrow();
    }

    // Duffel 오류 본문 형식: errors[](type·code·title·message) + meta(request_id·status)
    static byte[] error(int status, String type, String code) {
        return """
                {"errors":[{"type":"%s","code":"%s","title":"Error","message":"Error message."}],
                "meta":{"request_id":"FakeRequestId","status":%d}}""".formatted(type, code, status)
                .getBytes(StandardCharsets.UTF_8);
    }

    // 실측 raw 응답 본문(docs/experiments/duffel-test-mode/raw/의 response.body)
    static byte[] real(String name) {
        try (InputStream in = DuffelPaymentStepTest.class.getResourceAsStream("/duffel/real/" + name)) {
            return in.readAllBytes();
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
