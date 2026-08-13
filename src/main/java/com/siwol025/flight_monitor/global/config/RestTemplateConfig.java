package com.siwol025.flight_monitor.global.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    static final int CONNECT_TIMEOUT_MS = 3000;
    static final int READ_TIMEOUT_MS = 5000;

    /**
     * 커넥션 수준 타임아웃 — connect(연결 수립) 3s, socket(응답 바이트 간 read) 5s.
     * Spring 7 의 팩토리가 타임아웃 세터를 노출하지 않으므로 httpclient5 설정에 직접 담는다.
     */
    static ConnectionConfig connectionConfig() {
        return ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setSocketTimeout(Timeout.ofMilliseconds(READ_TIMEOUT_MS))
                .build();
    }

    /**
     * 요청 수준 타임아웃 — 풀에서 커넥션을 빌리기까지의 대기(connection-request) 3s, 응답(response) 5s.
     */
    static RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setResponseTimeout(Timeout.ofMilliseconds(READ_TIMEOUT_MS))
                .build();
    }

    /**
     * 외부 API 호출용 커넥션 풀 관리자.
     *
     * <p>정합 불변식: 풀 최대 커넥션 수({@code monitor.http.max-connections}) 는 in-flight 세마포어
     * permit 총량({@code monitor.backpressure.max-in-flight}) <b>이상</b>이어야 한다. 풀이 세마포어보다
     * 작으면 세마포어가 허용한 동시성을 풀이 다시 조여 새 병목이 되므로 기동 시 fail-fast 한다.
     */
    @Bean
    public PoolingHttpClientConnectionManager monitoringHttpConnectionManager(
            @Value("${monitor.http.max-connections:780}") int maxConnections,
            @Value("${monitor.backpressure.max-in-flight:100}") int maxInFlight) {
        if (maxConnections < maxInFlight) {
            throw new IllegalStateException(
                    "monitor.http.max-connections(" + maxConnections
                            + ") < monitor.backpressure.max-in-flight(" + maxInFlight
                            + "): HTTP 커넥션 풀이 in-flight 세마포어 permit 보다 작으면 풀이 새 병목이 된다."
                            + " max-connections >= max-in-flight 불변식이 깨졌습니다.");
        }
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(maxConnections)
                .setDefaultConnectionConfig(connectionConfig())
                .build();
    }

    @Bean
    public CloseableHttpClient monitoringHttpClient(PoolingHttpClientConnectionManager connectionManager) {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig())
                .build();
    }

    @Bean
    public RestTemplate restTemplate(CloseableHttpClient monitoringHttpClient) {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(monitoringHttpClient));
    }
}
