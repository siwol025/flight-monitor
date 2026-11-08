package com.siwol025.flight_monitor.booking.idempotency;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 우리 API 멱등키 설정. 근거는 application.yml의 booking.idempotency 주석에 있다 */
@Getter
@Setter
@ConfigurationProperties(prefix = "booking.idempotency")
public class IdempotencyProperties {

    /** PROCESSING인 채로 이보다 오래 갱신이 없으면 처리 서버가 죽은 것으로 보고 같은 키 재요청이 처리권을 가져간다 */
    private Duration staleAfter = Duration.ofMinutes(2);
}
