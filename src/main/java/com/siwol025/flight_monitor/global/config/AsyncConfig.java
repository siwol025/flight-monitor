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
        // 작업마다 가상 스레드. 이 executor에는 상한이 없으므로 동시 처리 수 상한은
        // InFlightBackpressure 세마포어(max-in-flight)가 단독으로 결정한다(미처리 백로그는 durable Redis 큐가 짊어진다).
        // ExecutorService 타입으로 노출해 컨텍스트 종료 시 Spring이 shutdown()/close()를 호출하도록 한다.
        ThreadFactory factory = Thread.ofVirtual().name("MonitorWorker-", 0).factory();
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
