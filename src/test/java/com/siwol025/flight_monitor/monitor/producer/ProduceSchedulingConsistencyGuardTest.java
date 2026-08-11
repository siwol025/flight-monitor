package com.siwol025.flight_monitor.monitor.producer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 프로듀서 스케줄링 일관성 불변식 가드:
 * {@code monitor.produce.lock-at-least-for < monitor.produce.fixed-rate-ms} 가 깨지면
 * (락 보유 시간이 주기 이상이면) 스프링 컨텍스트가 기동 단계에서 fail-fast 로 실패해야 한다.
 *
 * <p>lockAtLeastFor 가 주기 이상이면 ShedLock 이 주기를 조용히 건너뛰어
 * "주기당 정확히 1회 발행" 불변식을 깨뜨린다. 현재는 검증이 없어 out-of-range 설정으로도
 * 컨텍스트가 정상 기동한다. 이 테스트는 그 공백을 드러낸다.
 */
class ProduceSchedulingConsistencyGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // Duration 프로퍼티(PT290S 등)를 @Value 로 바인딩하려면 Spring Boot 의 변환 서비스가 필요하다.
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(ProduceSchedulingConsistencyValidator.class);

    @Test
    void 컨텍스트기동_lockAtLeastFor가주기이상이면_실패한다() {
        contextRunner
                .withPropertyValues(
                        "monitor.produce.fixed-rate-ms=60000",
                        "monitor.produce.lock-at-least-for=PT290S",
                        "monitor.produce.lock-at-most-for=PT300S")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("lock-at-least-for")
                        .hasMessageContaining("fixed-rate-ms"));
    }
}
