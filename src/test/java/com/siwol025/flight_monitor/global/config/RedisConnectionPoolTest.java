package com.siwol025.flight_monitor.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 커넥션 풀 가드.
 *
 * <p>컨슈머의 블로킹 {@code BRPOP} 은 전용(dedicated) 커넥션을 요구한다. Lettuce 커넥션 풀
 * (commons-pool2)이 없으면 pop 1건마다 커넥션을 새로 열고 닫아, 고처리량에서 TIME_WAIT 누적으로
 * 에페메랄 포트를 고갈시킨다(실측: pop ~560/s에서 ~50s 만에 포트 고갈 → 파이프라인 스톨).
 * 이 테스트는 {@code spring.data.redis.lettuce.pool.*} 로 풀링이 실제 활성화됨을 고정한다.
 */
class RedisConnectionPoolTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withPropertyValues(
                    "spring.data.redis.lettuce.pool.enabled=true",
                    "spring.data.redis.lettuce.pool.max-active=16");

    @Test
    void redis커넥션팩토리_lettuce풀링_구성사용() {
        runner.run(context -> {
            LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);

            assertThat(factory.getClientConfiguration())
                    .as("블로킹 pop 커넥션 churn 방지 — Lettuce 풀링 클라이언트 구성이어야 한다")
                    .isInstanceOf(LettucePoolingClientConfiguration.class);
        });
    }
}
