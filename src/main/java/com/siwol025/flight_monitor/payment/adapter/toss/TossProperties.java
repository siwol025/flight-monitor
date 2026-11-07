package com.siwol025.flight_monitor.payment.adapter.toss;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 어댑터 설정. 근거와 운영 값은 application.yml의 payment.toss 주석에 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payment.toss")
public class TossProperties {

    private String baseUrl;

    /** 서버 전용 시크릿 키. 테스트 키(test_)만 받는다. 로그·예외·응답에 남기지 않는다 */
    private String secretKey;

    /** 브라우저 SDK에 내려 주는 공개 클라이언트 키 */
    private String clientKey;

    /** TCP 연결 수립 타임아웃 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 승인·조회 응답 타임아웃 */
    private Duration confirmReadTimeout = Duration.ofSeconds(30);

    /** 실패 주입용 TossPayments-Test-Code 헤더 값. 비어 있으면 붙이지 않는다 */
    private String testCode;
}
