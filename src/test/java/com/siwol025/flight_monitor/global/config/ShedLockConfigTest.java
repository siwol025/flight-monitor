package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class ShedLockConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withUserConfiguration(ShedLockConfig.class);

    @Test
    void ShedLock_LockProvider_빈_생성됨() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(LockProvider.class));
    }

    @Test
    void ShedLock_스케줄락_활성화_설정_로드됨() {
        contextRunner.run(context -> assertThat(context).hasBean("proxyScheduledLockAopBeanPostProcessor"));
    }
}
