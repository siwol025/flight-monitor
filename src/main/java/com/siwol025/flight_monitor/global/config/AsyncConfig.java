package com.siwol025.flight_monitor.global.config;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "monitoringTaskExecutor")
    public Executor monitoringTaskExecutor(
            PipelineMetrics pipelineMetrics,
            @Value("${monitor.executor.core-pool-size:30}") int corePoolSize,
            @Value("${monitor.backpressure.max-in-flight:100}") int maxInFlight,
            @Value("${monitor.executor.queue-capacity:8}") int queueCapacity,
            @Value("${monitor.worker.count:3}") int workerCount) {
        // 거부 도달-불가 불변식: 큐 용량이 워커 수보다 작으면 CallerRuns 로 조용히 처리량이
        // 저하되므로 기동 단계에서 fail-fast.
        // 이 불변식은 MonitoringExecutorRejectionUnreachableGuardTest가 가드한다.
        if (queueCapacity < workerCount) {
            throw new IllegalStateException(
                    "monitor.executor.queue-capacity(" + queueCapacity
                            + ") < monitor.worker.count(" + workerCount
                            + "): queue-capacity >= worker.count 불변식이 깨졌습니다.");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        // max pool size는 in-flight 세마포어 permit 총량과 동일 설정 키에서 파생(단일 진실 공급원).
        // 정렬 불변식은 MonitoringExecutorAlignmentTest가 가드하므로 한쪽 기본값만 바꾸지 말 것.
        executor.setMaxPoolSize(maxInFlight);
        executor.setQueueCapacity(queueCapacity);

        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);

        executor.setThreadNamePrefix("MonitorWorker-Async-");
        executor.setRejectedExecutionHandler(new MetricsAwareCallerRunsPolicy(pipelineMetrics));

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    @Bean(name = "notificationEventExecutor")
    public Executor notificationEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-Async-");

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
