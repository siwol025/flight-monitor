package com.siwol025.flight_monitor.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "오늘"의 기준 시계. 출발일 판정(구독 등록의 과거 출발일, 구독 만료)은 한국 날짜로 한다.
 * 컨테이너 기본 시간대는 UTC라 JVM 기본 시간대에 기대면 한국 시각 0~9시에 날짜가 하루 늦다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
