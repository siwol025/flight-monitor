package com.siwol025.flight_monitor.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "monitoringTaskExecutor")
    public ExecutorService monitoringTaskExecutor(
            @Value("${monitor.executor.pool-size:100}") int poolSize,
            @Value("${monitor.executor.queue-capacity:10}") int queueCapacity) {
        // 플랫폼 스레드풀. 동시 처리 수 상한은 InFlightBackpressure 세마포어(max-in-flight)가 결정하고,
        // 이 풀은 그 상한만큼의 스레드로 처리한다. 큐는 permit 반납/스레드 반환 타이밍 race 흡수용의 소량이며,
        // 버퍼링 목적이 아니다(미처리 백로그는 durable Redis 큐가 짊어진다).
        // ExecutorService 타입으로 노출해 컨텍스트 종료 시 Spring이 shutdown()/close()를 호출하도록 한다.
        ThreadFactory factory = Thread.ofPlatform().name("MonitorWorker-", 0).factory();
        return new ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity), factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
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
