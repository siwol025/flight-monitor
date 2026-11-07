package com.siwol025.flight_monitor.fare.adapter.duffel;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 예매용 Duffel 어댑터 설정. 감시({@link DuffelProperties})와 업체 주소·키·커넥션 풀을 나눈다.
 * 근거와 운영 값은 application.yml의 fare-provider.duffel-booking 주석에 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fare-provider.duffel-booking")
public class DuffelBookingProperties {

    private String baseUrl;

    /** Authorization: Bearer 토큰. 테스트 모드 키(duffel_test_)만 받는다 */
    private String apiKey;

    /** Duffel-Version 헤더 */
    private String version = "v2";

    /** 검색 요청 쿼리 supplier_timeout(밀리초로 보냄). 업체가 항공사 응답을 기다리는 최대 시간 */
    private Duration supplierTimeout = Duration.ofSeconds(10);

    /** TCP 연결 수립 타임아웃 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 검색·조회 응답 타임아웃. supplierTimeout보다 길어야 업체가 정상적으로 끊는 경우를 우리가 먼저 끊지 않는다 */
    private Duration queryReadTimeout = Duration.ofSeconds(15);

    /** 예매 전용 커넥션 풀 최대 커넥션 수 */
    private int maxConnections = 10;
}
