package com.siwol025.flight_monitor.payment.adapter.toss;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 토스페이먼츠 전용 HTTP 클라이언트와 어댑터 빈 조립.
 * 실제 결제를 보내므로 테스트 키(test_)가 아니면 기동하지 않는다.
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossHttpClientConfig {

    @Bean(defaultCandidate = false)
    public CloseableHttpClient tossHttpClient(TossProperties properties) {
        if (!isTestKey(properties.getSecretKey())) {
            // 키 값은 메시지에 싣지 않는다
            throw new IllegalStateException("payment.toss.secret-key는 토스페이먼츠 테스트 키(test_)여야 합니다.");
        }
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(properties.getConnectTimeout()))
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(properties.getConfirmReadTimeout()))
                        .build())
                // 기본 재시도 전략은 429·503에 승인 POST도 다시 보낸다. 재요청 여부는 조회 뒤 호출한 쪽이 정한다
                .disableAutomaticRetries()
                .build();
    }

    // PaymentGateway 포트로 주입된다(반환형이 구현 타입이라 포트 타입으로 찾힘)
    @Bean
    TossPaymentsClient tossPaymentsClient(@Qualifier("tossHttpClient") CloseableHttpClient tossHttpClient,
                                          TossProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                // Basic base64(시크릿 키 + ":"). 콜론이 빠지면 401 UNAUTHORIZED_KEY
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                        .encodeToString((properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8)))
                .requestFactory(new HttpComponentsClientHttpRequestFactory(tossHttpClient));
        // 실패 주입은 테스트 키에서만. 빈 값도 Toss는 "헤더 있음"으로 보고 INVALID_TEST_CODE로 거절하므로 붙이지 않는다
        String testCode = properties.getTestCode();
        if (isTestKey(properties.getSecretKey()) && testCode != null && !testCode.isBlank()) {
            builder.defaultHeader("TossPayments-Test-Code", testCode);
        }
        return new TossPaymentsClient(builder.build());
    }

    private static boolean isTestKey(String secretKey) {
        return secretKey != null && secretKey.startsWith("test_");
    }
}
