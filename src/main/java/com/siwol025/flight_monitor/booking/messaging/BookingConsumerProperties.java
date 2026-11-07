package com.siwol025.flight_monitor.booking.messaging;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Duffel 결제 큐 소비자 설정. 근거는 application.yml의 booking.consumer 주석에 있다 */
@Getter
@Setter
@ConfigurationProperties(prefix = "booking.consumer")
public class BookingConsumerProperties {

    private int concurrency = 2;

    /** 블로킹 재시도 중에는 미리 받아 둔 메시지도 기다리므로 1로 둔다 */
    private int prefetch = 1;

    /** 최초 시도 뒤 재시도 횟수. 소진하면 DLQ */
    private int maxRetries = 3;

    /** 첫 재시도 간격. 이후 2배씩 늘린다(1s → 2s → 4s) */
    private Duration initialInterval = Duration.ofSeconds(1);
}
