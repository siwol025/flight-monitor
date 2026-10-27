package com.siwol025.flight_monitor.monitor.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 감시 큐(노선 ID ZSET)의 발행·소비 의미를 실제 Redis(compose와 같은 이미지)로 검사한다.
 */
@Testcontainers
class MonitoringRouteQueueTest {

    private static final Duration CYCLE = Duration.ofSeconds(60);
    private static final long POP_TIMEOUT_SECONDS = 1;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private CycleClock clock;
    private TaskQueueManager publisher;
    private TaskQueueConsumerManager consumer;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        clock = new CycleClock(Instant.parse("2026-10-27T00:00:00Z"));
        restartServer();
    }

    /** 서버 메모리 상태 없이 Redis 큐만 이어받는 새 발행·소비 객체 */
    private void restartServer() {
        FallbackTaskService fallbackTaskService = mock(FallbackTaskService.class);
        consumer = new TaskQueueConsumerManager(redis, fallbackTaskService);
        publisher = new TaskQueueManager(redis, fallbackTaskService, consumer,
                new PipelineMetrics(new SimpleMeterRegistry()), clock);
    }

    @Test
    void 발행_이미큐에있는노선_다시넣지않음() {
        publisher.publishTasks(List.of("2"));
        clock.advance(CYCLE);
        publisher.publishTasks(List.of("1"));
        clock.advance(CYCLE);
        // 이미 기다리는 노선을 다시 발행해도 한 번만 있고, 처음 넣은 시각(앞자리)을 지킨다
        publisher.publishTasks(List.of("2"));

        assertThat(publisher.getQueueSizeSafely()).isEqualTo(2);
        assertThat(List.of(pop(), pop())).containsExactly("2", "1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 발행_빈목록또는null_큐변화없음(List<String> routes) {
        publisher.publishTasks(routes);

        assertThat(publisher.getQueueSizeSafely()).isZero();
    }

    @Test
    void 소비_가장오래기다린노선부터() {
        // 문자열 순으로는 "10"이 "9"보다 앞이라, 넣은 시각으로 꺼내야만 9가 먼저 나온다
        publisher.publishTasks(List.of("9"));
        clock.advance(CYCLE);
        publisher.publishTasks(List.of("10"));

        assertThat(List.of(pop(), pop())).containsExactly("9", "10");
    }

    @Test
    void pop후_같은노선_다음주기에다시발행가능() {
        publisher.publishTasks(List.of("1"));
        assertThat(pop()).isEqualTo("1");
        clock.advance(CYCLE);

        // 꺼낸 노선은 큐에서 사라졌으므로 다음 주기 발행으로 다시 들어간다(처리 중 표시 같은 별도 추적이 없다)
        publisher.publishTasks(List.of("1"));

        assertThat(pop()).isEqualTo("1");
    }

    @Test
    void 처리중서버중단_노선이다음주기에다시발행됨() {
        publisher.publishTasks(List.of("1", "2"));
        clock.advance(CYCLE);
        // 노선 1을 꺼낸 서버가 처리하지 못하고 죽는다
        assertThat(pop()).isEqualTo("1");
        restartServer();

        publisher.publishTasks(List.of("1", "2"));

        // 1은 다시 들어가고, 계속 기다리던 2는 처음 넣은 시각을 지켜 먼저 나온다
        assertThat(publisher.getQueueSizeSafely()).isEqualTo(2);
        assertThat(List.of(pop(), pop())).containsExactly("2", "1");
    }

    @Test
    void 소비지연지속_큐길이는_활성노선수를넘지않음() {
        // 소비가 멈춘 채 주기가 계속 돌아도 같은 노선이 쌓이지 않는다(그래서 큐 길이 임계치로 발행을 건너뛸 필요가 없다)
        for (int cycle = 0; cycle < 10; cycle++) {
            publisher.publishTasks(List.of("1", "2", "3"));
            clock.advance(CYCLE);
        }

        assertThat(publisher.getQueueSizeSafely()).isEqualTo(3);
    }

    @Test
    void 소비_빈큐_타임아웃후null() {
        // 폴러는 null을 받으면 DB 폴백 큐를 확인하고 다시 돈다. 바로 돌아오면 빈 큐에서 Redis를 쉼 없이 두드린다
        long start = System.nanoTime();

        String popped = pop();

        assertThat(popped).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
    }

    @Test
    void 소비대기중_발행되면_타임아웃전에꺼냄() throws Exception {
        // 블로킹 pop이 다른 명령과 같은 커넥션을 붙잡으면, 발행이 pop 타임아웃(3초)이 끝날 때까지 막힌다
        long start = System.nanoTime();
        CompletableFuture<Void> publishing = CompletableFuture.runAsync(
                () -> publisher.publishTasks(List.of("7")),
                CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS));

        String popped = consumer.blockAndPopTask(3);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        publishing.get(5, TimeUnit.SECONDS);

        assertThat(popped).isEqualTo("7");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }

    private String pop() {
        return consumer.blockAndPopTask(POP_TIMEOUT_SECONDS);
    }

    /**
     * 발행 주기마다 앞으로 감는 시계.
     */
    private static final class CycleClock extends Clock {

        private Instant instant;

        CycleClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
