package com.siwol025.flight_monitor.notification.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MockMailService 장애 주입 설정. 메일 유실 재현·측정용이며 기본값은 모두 0(주입 없음)이다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mock.mail")
public class MockMailProperties {

    /** 발송 실패 확률 (0.0 ~ 1.0) */
    private double failureRate = 0.0;

    /** 실패 중 영구 오류(재시도해도 실패) 비율 (0.0 ~ 1.0). 나머지는 일시 오류 */
    private double permanentFailureRatio = 0.0;

    /** 발송 1건당 지연 (SMTP 왕복 시간 모사) */
    private long latencyMs = 0;

    /** 몰아치는 장애 시작 시점: 프로세스의 첫 발송 시각 기준 */
    private long outageStartAfterMs = 0;

    /** 몰아치는 장애 지속 시간. 이 구간의 발송은 전부 일시 오류. 0이면 장애 없음 */
    private long outageDurationMs = 0;
}
