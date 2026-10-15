package com.siwol025.flight_monitor.monitor.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.global.fallback.service.FallbackTaskService;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class TaskQueueManagerTest {

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";
    private static final Throwable REDIS_DOWN = new RedisConnectionFailureException("redis down");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private FallbackTaskService fallbackTaskService;

    @Mock
    private TaskQueueConsumerManager taskQueueConsumerManager;

    private SimpleMeterRegistry meterRegistry;
    private TaskQueueManager taskQueueManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        taskQueueManager = new TaskQueueManager(redisTemplate, fallbackTaskService, taskQueueConsumerManager,
                new PipelineMetrics(meterRegistry));
    }

    @Test
    void publishTasks_락없이_Redis푸시만_수행() {
        // given
        List<String> payloads = List.of("payload-1", "payload-2");
        given(redisTemplate.opsForList()).willReturn(listOperations);

        // when
        taskQueueManager.publishTasks(payloads);

        // then: 분산락 없이 leftPushAll 이 정확히 한 번 호출된다
        verify(listOperations, times(1)).leftPushAll(TASK_QUEUE_KEY, payloads);
    }

    @Test
    void publishTasks_빈리스트면_아무것도안함() {
        // when
        taskQueueManager.publishTasks(Collections.emptyList());

        // then
        verify(listOperations, never()).leftPushAll(anyString(), anyList());
    }

    @Test
    void publishTasks_null이면_아무것도안함() {
        // when
        taskQueueManager.publishTasks(null);

        // then
        verify(listOperations, never()).leftPushAll(anyString(), anyList());
    }

    @Test
    void fallbackPublishTasks_Redis실패시_전체페이로드가_DB폴백에_저장됨() {
        // given
        List<String> payloads = List.of("payload-1", "payload-2", "payload-3");

        // when
        taskQueueManager.fallbackPublishTasks(payloads, REDIS_DOWN);

        // then
        verify(fallbackTaskService).savePendingTasks(payloads);
    }

    @Test
    void fallbackPublishTasks_연속호출시_모든청크가_저장됨() {
        // given: 프로듀서가 한 주기에 여러 청크를 연달아 폴백으로 보낸다
        List<String> chunk1 = List.of("payload-1", "payload-2");
        List<String> chunk2 = List.of("payload-3", "payload-4");

        // when
        taskQueueManager.fallbackPublishTasks(chunk1, REDIS_DOWN);
        taskQueueManager.fallbackPublishTasks(chunk2, REDIS_DOWN);

        // then: 두 번째 청크부터 연쇄 유실되지 않는다
        verify(fallbackTaskService).savePendingTasks(chunk1);
        verify(fallbackTaskService).savePendingTasks(chunk2);
    }

    @Test
    void fallbackPublishTasks_저장후_폴백존재플래그_설정됨() {
        // when
        taskQueueManager.fallbackPublishTasks(List.of("payload-1"), REDIS_DOWN);

        // then
        verify(taskQueueConsumerManager).markFallbackTaskExists();
    }

    @Test
    void fallbackPublishTasks_DB저장실패시_예외전파없이_유실건수_메트릭_증가() {
        // given: Redis·DB 이중 장애
        List<String> payloads = List.of("payload-1", "payload-2", "payload-3");
        willThrow(new DataAccessResourceFailureException("db down"))
                .given(fallbackTaskService).savePendingTasks(payloads);

        // when & then: 남은 청크 발행이 계속되도록 예외를 프로듀서로 전파하지 않는다
        assertThatCode(() -> taskQueueManager.fallbackPublishTasks(payloads, REDIS_DOWN))
                .doesNotThrowAnyException();
        assertThat(meterRegistry.counter(PipelineMetrics.METRIC_TASK_DROPPED).count()).isEqualTo(3.0);
    }

    @Test
    void fallbackPublishTasks_DB저장실패시_폴백존재플래그_설정안됨() {
        // given
        List<String> payloads = List.of("payload-1");
        willThrow(new DataAccessResourceFailureException("db down"))
                .given(fallbackTaskService).savePendingTasks(payloads);

        // when
        taskQueueManager.fallbackPublishTasks(payloads, REDIS_DOWN);

        // then
        verify(taskQueueConsumerManager, never()).markFallbackTaskExists();
    }
}
