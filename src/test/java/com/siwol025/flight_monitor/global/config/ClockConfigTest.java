package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class);

    @Test
    void 시계빈_서울시간대() {
        // 존이 빠지거나 UTC가 되면 한국 시각 0~9시에 지난 출발일이 통과한다(컨테이너 기본 시간대는 UTC)
        contextRunner.run(context ->
                assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Seoul")));
    }
}
