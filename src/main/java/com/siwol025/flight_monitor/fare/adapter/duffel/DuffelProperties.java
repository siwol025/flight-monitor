package com.siwol025.flight_monitor.fare.adapter.duffel;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Duffel 어댑터 설정. 근거와 운영 값은 application.yml의 fare-provider.duffel 주석에 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fare-provider.duffel")
public class DuffelProperties {

    private String baseUrl;

    /** Authorization: Bearer 토큰 */
    private String apiKey;

    /** Duffel-Version 헤더 */
    private String version = "v2";

    /** 요청 쿼리 supplier_timeout(밀리초로 보냄). 업체가 항공사 응답을 기다리는 최대 시간 */
    private Duration supplierTimeout = Duration.ofSeconds(10);

    /** TCP 연결 수립 타임아웃 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** HTTP 응답 타임아웃. supplierTimeout보다 길어야 업체가 정상적으로 끊는 경우를 우리가 먼저 끊지 않는다 */
    private Duration responseTimeout = Duration.ofSeconds(15);

    /** 업체가 알려 준 기간당 한도. 응답 ratelimit-limit과 다르면 경고한다(앱 호출 한도가 아니라 업체 한도와 비교) */
    private int expectedRateLimit = 30;
}
