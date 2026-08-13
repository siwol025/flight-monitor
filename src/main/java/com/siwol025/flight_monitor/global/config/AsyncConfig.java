package com.siwol025.flight_monitor.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "monitoringTaskExecutor")
    public ExecutorService monitoringTaskExecutor() {
        // 가상 스레드 per-task executor: 블로킹 외부 호출이 캐리어/플랫폼 스레드를 점유하지 않으므로
        // in-flight 상한은 오직 InFlightBackpressure 세마포어가 결정한다(풀 크기/큐/거부 없음).
        // ExecutorService 타입으로 노출해 컨텍스트 종료 시 Spring이 shutdown()/close()를 호출하도록 한다.
        ThreadFactory factory = Thread.ofVirtual().name("MonitorWorker-VT-", 0).factory();
        return Executors.newThreadPerTaskExecutor(factory);
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
