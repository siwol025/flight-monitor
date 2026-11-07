package com.siwol025.flight_monitor.booking.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import com.siwol025.flight_monitor.booking.repository.BookingRepository;
import com.siwol025.flight_monitor.booking.saga.DuffelPaymentStep;
import com.siwol025.flight_monitor.booking.service.BookingStateWriter;
import com.siwol025.flight_monitor.booking.support.TestBookings;
import com.siwol025.flight_monitor.fare.adapter.duffel.DuffelHttpClientConfig;
import com.siwol025.flight_monitor.fare.adapter.duffel.FakeDuffelServer;
import com.siwol025.flight_monitor.global.config.JacksonConfig;
import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import com.siwol025.flight_monitor.support.ComposeMysql;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Duffel 결제 큐(⑥)를 실제 RabbitMQ(compose와 같은 이미지)·MySQL과 FakeDuffelServer로 검사한다. 발행은 실제 발행기로,
 * 소비는 실제 리스너(동시 2, prefetch 1, 블로킹 재시도 3회)로 한다. 재시도 간격만 10ms로 줄인다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Import({RabbitMqConfig.class, BookingMessagingConfig.class, RabbitDuffelPaymentPublisher.class,
        DuffelPaymentListener.class, DuffelPaymentStep.class, BookingStateWriter.class, DuffelHttpClientConfig.class,
        JacksonConfig.class})
@TestPropertySource(properties = {
        "fare-provider.duffel-booking.api-key=duffel_test_token",
        "fare-provider.duffel-booking.payment-read-timeout=1s",
        "booking.consumer.concurrency=2",
        "booking.consumer.max-retries=3",
        "booking.consumer.initial-interval=10ms"})
class BookingPipelineIntegrationTest {

    private static final int MAX_RETRIES = 3;
    private static final FakeDuffelServer DUFFEL = startDuffel();
    private static final String PAYMENTS = "/air/payments";

    @Container
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ComposeMysql.registerSharedDataSource(registry);
        registry.add("fare-provider.duffel-booking.base-url", DUFFEL::baseUrl);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private DuffelPaymentPublisher publisher;

    @Autowired
    private AmqpAdmin amqpAdmin;

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
        amqpAdmin.purgeQueue(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE, false);
        amqpAdmin.purgeQueue(BookingMessagingConfig.DUFFEL_PAYMENT_DLQ, false);
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM bookings");
        bookingId = TestBookings.held(bookingRepository, bookingStateWriter,
                TestBookings.newUser(userRepository, Role.USER), "off_pipeline");
        TestBookings.moveTo(jdbc, bookingId, BookingStatus.PAYMENT_APPROVED, 0);
    }

    @Test
    void queue_발행하면_결제하고_발권확인_TICKETED() {
        unpaidOrder();
        paymentSucceeds();
        ticketedOrder();

        publisher.publish(bookingId);

        await().atMost(Duration.ofSeconds(10)).until(() -> status() == BookingStatus.TICKETED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(1);
        assertThat(messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_DLQ)).isZero();
    }

    @Test
    void pay_500_UNCONFIRMED_리스너재시도로도_POST1회() throws InterruptedException {
        unpaidOrder();
        DUFFEL.enqueue("POST", PAYMENTS, 500, error(500, "api_error", "internal_server_error"), Duration.ZERO);

        publisher.publish(bookingId);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> status() == BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        // 같은 명령이 다시 와도(재발행·재전달) 결제를 다시 보내지 않는다
        publisher.publish(bookingId);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE) == 0);
        Thread.sleep(300);

        assertThat(status()).isEqualTo(BookingStatus.DUFFEL_PAYMENT_UNCONFIRMED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(1);
        assertThat(messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_DLQ)).isZero();
    }

    @Test
    void pay_503_재시도_POST2회허용() {
        unpaidOrder();
        DUFFEL.enqueue("POST", PAYMENTS, 503, error(503, "api_error", "service_unavailable"), Duration.ZERO);
        unpaidOrder();
        paymentSucceeds();
        ticketedOrder();

        publisher.publish(bookingId);

        // 503은 결제가 만들어지지 않았다는 응답이라 리스너 재시도가 다시 보낸다
        await().atMost(Duration.ofSeconds(10)).until(() -> status() == BookingStatus.TICKETED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(2);
        assertThat(messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_DLQ)).isZero();
    }

    @Test
    void pay_429가재시도보다길면_소진후DLQ_PAYMENT_APPROVED로남아_재발행으로발권() {
        // Retry-After가 리스너 백오프보다 길어 재시도를 모두 429로 소진한다
        for (int i = 0; i <= MAX_RETRIES; i++) {
            unpaidOrder();
            DUFFEL.enqueue("POST", PAYMENTS, 429, error(429, "rate_limit_error", "rate_limit_exceeded"),
                    Duration.ZERO);
        }

        publisher.publish(bookingId);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_DLQ) == 1);
        // 결제가 만들어지지 않았으므로 확인 스케줄러의 재발행 대상(PAYMENT_APPROVED)으로 남는다
        assertThat(status()).isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(MAX_RETRIES + 1);

        unpaidOrder();
        paymentSucceeds();
        ticketedOrder();
        publisher.publish(bookingId);

        await().atMost(Duration.ofSeconds(10)).until(() -> status() == BookingStatus.TICKETED);
    }

    @Test
    void queue_같은명령두서버동시_POST1회() throws InterruptedException {
        // 첫 조회를 늦춰 두 소비자가 같은 예매를 겹쳐 처리하게 한다
        DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 200, real("s03b_h1_hold_order.json"), Duration.ofMillis(300));
        paymentSucceeds();
        ticketedOrder();

        publisher.publish(bookingId);
        publisher.publish(bookingId);

        await().atMost(Duration.ofSeconds(10)).until(() -> status() == BookingStatus.TICKETED);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE) == 0);
        Thread.sleep(300);
        assertThat(status()).isEqualTo(BookingStatus.TICKETED);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isEqualTo(1);
    }

    @Test
    void queue_재시도소진_DLQ() {
        for (int i = 0; i <= MAX_RETRIES; i++) {
            DUFFEL.enqueue("GET", TestBookings.ORDER_PATH, 500, error(500, "api_error", "internal_server_error"),
                    Duration.ZERO);
        }

        publisher.publish(bookingId);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_DLQ) == 1);
        // 최초 1회 + 재시도 3회 모두 결제 전 조회에서 실패했다
        assertThat(DUFFEL.count("GET", TestBookings.ORDER_PATH)).isEqualTo(MAX_RETRIES + 1);
        assertThat(DUFFEL.count("POST", PAYMENTS)).isZero();
        assertThat(status()).isEqualTo(BookingStatus.PAYMENT_APPROVED);
        assertThat(messageCount(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE)).isZero();
    }

    @Test
    void mock프로필_발행기는있고_리스너는없음() {
        // 승인 API는 프로필 조건 없이 모든 인스턴스에서 돈다. 발행기가 빠지면 승인한 예매가 발행되지 않는다
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(RabbitMqConfig.class, BookingMessagingConfig.class,
                        RabbitDuffelPaymentPublisher.class, DuffelPaymentListener.class)
                .withBean(DuffelPaymentStep.class, () -> mock(DuffelPaymentStep.class))
                .withPropertyValues("spring.profiles.active=mock",
                        "spring.rabbitmq.host=" + RABBIT.getHost(),
                        "spring.rabbitmq.port=" + RABBIT.getAmqpPort())
                .run(context -> {
                    assertThat(context).hasSingleBean(DuffelPaymentPublisher.class);
                    assertThat(context).doesNotHaveBean(DuffelPaymentListener.class);
                });
    }

    private BookingStatus status() {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    private long messageCount(String queue) {
        return amqpAdmin.getQueueInfo(queue).getMessageCount();
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

    private static byte[] error(int status, String type, String code) {
        return """
                {"errors":[{"type":"%s","code":"%s","title":"Error","message":"Error message."}],
                "meta":{"request_id":"FakeRequestId","status":%d}}""".formatted(type, code, status)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] real(String name) {
        try (InputStream in = BookingPipelineIntegrationTest.class.getResourceAsStream("/duffel/real/" + name)) {
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
