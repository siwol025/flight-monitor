package com.siwol025.flight_monitor.monitor.utils;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.global.fallback.repository.FallbackMonitoringTaskRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class TaskQueueManagerTest {

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private TaskQueueManager newTaskQueueManager() {
        // 목표 설계: RedissonClient 협력자 없이 생성한다
        FallbackMonitoringTaskRepository fallbackRepository = mock(FallbackMonitoringTaskRepository.class);
        TaskQueueConsumerManager taskQueueConsumerManager = mock(TaskQueueConsumerManager.class);
        return new TaskQueueManager(redisTemplate, fallbackRepository, taskQueueConsumerManager);
    }

    @Test
    void publishTasks_락없이_Redis푸시만_수행() {
        // given
        TaskQueueManager taskQueueManager = newTaskQueueManager();
        List<String> payloads = List.of("payload-1", "payload-2");
        given(redisTemplate.opsForList()).willReturn(listOperations);

        // when
        taskQueueManager.publishTasks(payloads);

        // then: Redisson 락 없이 leftPushAll 이 정확히 한 번 호출된다
        verify(listOperations, times(1)).leftPushAll(TASK_QUEUE_KEY, payloads);
    }

    @Test
    void publishTasks_빈리스트면_아무것도안함() {
        // given
        TaskQueueManager taskQueueManager = newTaskQueueManager();

        // when
        taskQueueManager.publishTasks(Collections.emptyList());

        // then
        verify(listOperations, never()).leftPushAll(anyString(), anyList());
    }

    @Test
    void publishTasks_null이면_아무것도안함() {
        // given
        TaskQueueManager taskQueueManager = newTaskQueueManager();

        // when
        taskQueueManager.publishTasks(null);

        // then
        verify(listOperations, never()).leftPushAll(anyString(), anyList());
    }
}
