package com.siwol025.flight_monitor.payment.adapter.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.payment.PaymentFailureKind;
import com.siwol025.flight_monitor.payment.PaymentGateway;
import com.siwol025.flight_monitor.payment.PaymentGatewayException;
import com.siwol025.flight_monitor.payment.PaymentSnapshot;
import com.siwol025.flight_monitor.payment.PaymentStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 토스페이먼츠 승인·조회 어댑터. 업체는 FakeTossServer(실제 소켓, 실측 멱등 의미론)이고 응답 본문은 실측 raw를 쓴다.
 */
class TossPaymentsClientTest {

    private static final String SECRET_KEY = "test_sk_dummy";
    // 실측 A 시나리오(2단계)의 결제
    private static final String PAYMENT_KEY = "tviva261105bA5BpSbob3xNp";
    private static final String ORDER_ID = "fm-20261105-A-08210ed0d4";

    private FakeTossServer server;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeTossServer();
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(TossHttpClientConfig.class)
                .withPropertyValues(
                        "payment.toss.base-url=" + server.baseUrl(),
                        "payment.toss.secret-key=" + SECRET_KEY);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void confirm_200_DONE() throws IOException {
        server.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));

        contextRunner.run(context -> {
            PaymentSnapshot snapshot = context.getBean(PaymentGateway.class)
                    .confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1");

            assertThat(snapshot).isEqualTo(new PaymentSnapshot(PAYMENT_KEY, ORDER_ID, PaymentStatus.DONE, 1000,
                    Instant.parse("2026-11-05T05:52:16Z")));
        });
    }

    @Test
    void confirm_헤더_Basic콜론_ContentType_멱등키그대로() throws IOException {
        server.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));

        contextRunner.run(context -> {
            context.getBean(PaymentGateway.class).confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1");

            FakeTossServer.RecordedRequest request = server.lastRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/v1/payments/confirm");
            // base64("test_sk_dummy:") — 콜론이 빠지면 401 UNAUTHORIZED_KEY(1단계 1장)
            assertThat(request.headers().getFirst("Authorization")).isEqualTo("Basic dGVzdF9za19kdW1teTo=");
            // Content-Type이 없으면 Toss는 500 COMMON_ERROR(1단계 7장)
            assertThat(request.headers().getFirst("Content-Type")).startsWith("application/json");
            assertThat(request.headers().getFirst("Idempotency-Key")).isEqualTo("idem-k1");
            assertThat(new ObjectMapper().readTree(request.body())).isEqualTo(new ObjectMapper().readTree("""
                    {"paymentKey":"tviva261105bA5BpSbob3xNp","orderId":"fm-20261105-A-08210ed0d4","amount":1000}"""));
        });
    }

    @Test
    void find_IN_PROGRESS_승인아님() throws IOException {
        // 승인 전 조회: balanceAmount·totalAmount는 이미 채워져 있고 approvedAt만 null(2단계 A_01)
        server.enqueue("GET", "/v1/payments/" + PAYMENT_KEY, 200, real("A_145215-a8bd_01_get_before_confirm.json"));

        contextRunner.run(context -> {
            PaymentSnapshot snapshot = context.getBean(PaymentGateway.class).find(PAYMENT_KEY);

            assertThat(snapshot).isEqualTo(new PaymentSnapshot(PAYMENT_KEY, ORDER_ID, PaymentStatus.IN_PROGRESS, 1000,
                    null));
            assertThat(server.lastRequest().method()).isEqualTo("GET");
            assertThat(server.lastRequest().headers().getFirst("Authorization"))
                    .isEqualTo("Basic dGVzdF9za19kdW1teTo=");
        });
    }

    @Test
    void find_DONE() throws IOException {
        server.enqueue("GET", "/v1/payments/" + PAYMENT_KEY, 200, real("A_145215-a8bd_06_get_after_done_0.json"));

        contextRunner.run(context -> {
            PaymentSnapshot snapshot = context.getBean(PaymentGateway.class).find(PAYMENT_KEY);

            assertThat(snapshot).isEqualTo(new PaymentSnapshot(PAYMENT_KEY, ORDER_ID, PaymentStatus.DONE, 1000,
                    Instant.parse("2026-11-05T05:52:16Z")));
        });
    }

    @Test
    void confirm_FORBIDDEN_REQUEST_REJECTED() throws IOException {
        // 결제창 금액과 다른 amount로 승인(2단계 B_02)
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 403,
                real("B_145320-6187_02_confirm_amount_plus_1000.json"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(PaymentGateway.class)
                .confirm(PAYMENT_KEY, ORDER_ID, 3000, "idem-k1"))
                .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(PaymentFailureKind.REJECTED);
                    assertThat(e.getCode()).isEqualTo("FORBIDDEN_REQUEST");
                    assertThat(e.getProviderMessage()).isEqualTo("허용되지 않은 요청입니다.");
                }));
    }

    @Test
    void confirm_EXPIRED_PAYMENT() throws IOException {
        // 인증 후 10분 지나 승인(2단계 D_04)
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 400, real("D_145129-58eb_04_confirm_t11min.json"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(PaymentGateway.class)
                .confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"))
                .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(PaymentFailureKind.EXPIRED);
                    assertThat(e.getCode()).isEqualTo("EXPIRED_PAYMENT");
                }));
    }

    @Test
    void confirm_409_UNKNOWN() throws IOException {
        // 같은 키의 첫 승인이 처리 중일 때 두 번째 요청은 409(2단계 G_02). 처리 중 상태는 FakeTossServer가 지연으로 만든다
        server.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        server.confirmDelay(Duration.ofMillis(800));

        contextRunner.run(context -> {
            PaymentGateway gateway = context.getBean(PaymentGateway.class);
            CompletableFuture<PaymentSnapshot> first = CompletableFuture.supplyAsync(
                    () -> gateway.confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"));
            while (server.requests().isEmpty()) {
                Thread.onSpinWait();
            }

            assertThatThrownBy(() -> gateway.confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"))
                    .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                        assertThat(e.getKind()).isEqualTo(PaymentFailureKind.UNKNOWN);
                        assertThat(e.getCode()).isEqualTo("IDEMPOTENT_REQUEST_PROCESSING");
                    });
            assertThat(first.get().status()).isEqualTo(PaymentStatus.DONE);
        });
    }

    @Test
    void confirm_ALREADY_PROCESSED_PAYMENT_UNKNOWN() throws IOException {
        // 이미 승인된 결제를 다른 키로 승인(2단계 A_04·A_05). 승인됐을 수 있으므로 조회로 확인해야 한다
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 400, real("A_145215-a8bd_04_confirm_no_key.json"));

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(PaymentGateway.class)
                .confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k2"))
                .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(PaymentFailureKind.UNKNOWN);
                    assertThat(e.getCode()).isEqualTo("ALREADY_PROCESSED_PAYMENT");
                }));
    }

    @Test
    void confirm_빈본문403_CONFIGURATION() {
        // 게이트웨이 인증 거절은 code 없는 빈 본문 403(1단계 1장)
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 403, "");

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(PaymentGateway.class)
                .confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"))
                .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(PaymentFailureKind.CONFIGURATION);
                    assertThat(e.getCode()).isNull();
                }));
    }

    @Test
    void confirm_code없는500본문_UNKNOWN() {
        // message가 객체인 Spring 기본 오류 본문(1단계 s06f_key_301)
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 500, """
                {"message":{"timestamp":"2026-11-05T03:00:00.000+00:00","status":500,
                "error":"Internal Server Error","path":"/v1/payments/confirm"}}""");

        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(PaymentGateway.class)
                .confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"))
                .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(PaymentFailureKind.UNKNOWN);
                    assertThat(e.getCode()).isNull();
                    assertThat(e.getProviderMessage()).isNull();
                }));
    }

    @Test
    void confirm_읽기타임아웃_UNKNOWN() throws IOException {
        // 응답을 못 받은 승인도 서버에서는 처리됐을 수 있다(2단계 G_01)
        server.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        server.confirmDelay(Duration.ofSeconds(2));

        contextRunner.withPropertyValues("payment.toss.confirm-read-timeout=200ms")
                .run(context -> assertThatThrownBy(() -> context.getBean(PaymentGateway.class)
                        .confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"))
                        .isInstanceOfSatisfying(PaymentGatewayException.class,
                                e -> assertThat(e.getKind()).isEqualTo(PaymentFailureKind.UNKNOWN)));
    }

    @Test
    void confirm_503_자동재시도없이_한번만보냄() {
        // httpclient5 기본 재시도 전략은 503에 POST를 다시 보낸다. 어댑터 아래에서 재요청하지 않는다
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 503, "");
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 503, "");

        contextRunner.run(context -> {
            assertThatThrownBy(() -> context.getBean(PaymentGateway.class)
                    .confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"))
                    .isInstanceOfSatisfying(PaymentGatewayException.class,
                            e -> assertThat(e.getKind()).isEqualTo(PaymentFailureKind.UNKNOWN));
            assertThat(server.requests()).hasSize(1);
        });
    }

    @Test
    void testCode_test키에서만() throws IOException {
        server.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));

        // 설정한 실패 주입 코드는 승인·조회 요청에 헤더로 붙는다
        contextRunner.withPropertyValues("payment.toss.test-code=REJECT_CARD_PAYMENT").run(context -> {
            context.getBean(PaymentGateway.class).confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1");
            assertThat(server.lastRequest().headers().getFirst("TossPayments-Test-Code"))
                    .isEqualTo("REJECT_CARD_PAYMENT");
        });
        // 비어 있으면 붙이지 않는다(빈 값도 Toss는 INVALID_TEST_CODE로 거절한다)
        contextRunner.run(context -> {
            // 앞 승인과 다른 결제(같은 결제면 FakeTossServer가 ALREADY_PROCESSED_PAYMENT로 답한다)
            context.getBean(PaymentGateway.class).confirm("tviva-other", ORDER_ID, 1000, "idem-k2");
            assertThat(server.lastRequest().headers().containsKey("TossPayments-Test-Code")).isFalse();
        });
        // 테스트 키가 아니면 헤더를 붙일 기회도 없이 기동하지 않고, 키 값을 메시지에 싣지 않는다
        contextRunner.withPropertyValues("payment.toss.secret-key=live_sk_dummy",
                        "payment.toss.test-code=REJECT_CARD_PAYMENT")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("test_")
                            .hasMessageNotContaining("live_sk_dummy");
                });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 실패로그와예외에_시크릿키없음_traceId있음(CapturedOutput output) throws IOException {
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 403,
                real("B_145320-6187_02_confirm_amount_plus_1000.json"));

        contextRunner.run(context -> {
            Throwable thrown = catchThrowable(() -> context.getBean(PaymentGateway.class)
                    .confirm(PAYMENT_KEY, ORDER_ID, 3000, "idem-k1"));

            assertThat(output.getOut()).contains("FORBIDDEN_REQUEST").containsPattern("traceId=[0-9a-f]{32}");
            for (String text : new String[]{output.getAll(), thrown.getMessage()}) {
                assertThat(text).doesNotContain(SECRET_KEY).doesNotContain("dGVzdF9za19kdW1teTo=");
            }
        });
    }

    @Test
    void confirm_빈멱등키_보내지않음() {
        // 빈 키는 Toss가 거절하지 않고 다른 요청과 저장 응답을 공유할 수 있다(1단계 6장)
        contextRunner.run(context -> {
            assertThatThrownBy(() -> context.getBean(PaymentGateway.class).confirm(PAYMENT_KEY, ORDER_ID, 1000, " "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(server.requests()).isEmpty();
        });
    }

    @Test
    void fakeToss_같은키_첫5xx재생_다른키는ALREADY_PROCESSED() throws IOException {
        // D4: 같은 키는 첫 5xx만 재생하고 다시 처리하지 않는다(1단계 s06b2). 새 키는 처리되고, 승인된 뒤 또 다른 키는
        // ALREADY_PROCESSED_PAYMENT로 막혀 이중 청구가 생기지 않는다(2단계 A_05)
        server.confirmSucceeds(real("A_145215-a8bd_02_confirm_k1.json"));
        server.enqueue("POST", FakeTossServer.CONFIRM_PATH, 500,
                "{\"code\":\"FAILED_INTERNAL_SYSTEM_PROCESSING\",\"message\":\"내부 시스템 처리 작업이 실패했습니다. 잠시 후 다시 시도해주세요.\"}");

        contextRunner.run(context -> {
            PaymentGateway gateway = context.getBean(PaymentGateway.class);

            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> gateway.confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k1"))
                        .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                            assertThat(e.getKind()).isEqualTo(PaymentFailureKind.UNKNOWN);
                            assertThat(e.getCode()).isEqualTo("FAILED_INTERNAL_SYSTEM_PROCESSING");
                        });
            }
            assertThat(gateway.confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k2").status())
                    .isEqualTo(PaymentStatus.DONE);
            assertThatThrownBy(() -> gateway.confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k3"))
                    .isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                        assertThat(e.getKind()).isEqualTo(PaymentFailureKind.UNKNOWN);
                        assertThat(e.getCode()).isEqualTo("ALREADY_PROCESSED_PAYMENT");
                    });
            // 승인된 키의 재요청은 성공 응답 재생
            assertThat(gateway.confirm(PAYMENT_KEY, ORDER_ID, 1000, "idem-k2").status())
                    .isEqualTo(PaymentStatus.DONE);
            assertThat(server.requests()).hasSize(5);
        });
    }

    static String real(String name) throws IOException {
        try (InputStream in = TossPaymentsClientTest.class.getResourceAsStream("/toss/real/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
