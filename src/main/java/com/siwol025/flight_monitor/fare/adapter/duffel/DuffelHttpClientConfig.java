package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.siwol025.flight_monitor.fare.LowestFareFinder;
import java.time.Clock;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Duffel 전용 HTTP 클라이언트와 어댑터 빈 조립.
 *
 * <p>HTTP 클라이언트는 {@code defaultCandidate = false}로 등록한다. 타입만으로 주입받는 기존 빈
 * ({@code RestTemplateConfig}의 {@code CloseableHttpClient} 주입)에 끼어들지 않고, 이름으로 지정한 곳에만 주입된다.
 * 커넥션 풀은 이 클라이언트가 소유하며 컨텍스트 종료 때 클라이언트와 함께 닫힌다.</p>
 */
@Configuration
@EnableConfigurationProperties(DuffelProperties.class)
public class DuffelHttpClientConfig {

    /**
     * 정합 불변식: 풀 최대 커넥션 수({@code monitor.http.max-connections})는 in-flight 세마포어 permit 총량
     * ({@code monitor.backpressure.max-in-flight}) 이상이어야 한다. 진행 중인 요청마다 커넥션을 하나씩 붙잡으므로
     * 풀이 작으면 세마포어가 허용한 동시성을 풀이 다시 조여 새 병목이 된다. 풀 크기는 기존 속성을 그대로 읽는다.
     */
    @Bean(defaultCandidate = false)
    public CloseableHttpClient duffelHttpClient(DuffelProperties properties,
                                                @Value("${monitor.http.max-connections:100}") int maxConnections,
                                                @Value("${monitor.backpressure.max-in-flight:100}") int maxInFlight) {
        if (maxConnections < maxInFlight) {
            throw new IllegalStateException(
                    "monitor.http.max-connections(" + maxConnections
                            + ") < monitor.backpressure.max-in-flight(" + maxInFlight
                            + "): Duffel HTTP 커넥션 풀이 in-flight 세마포어 permit 보다 작으면 풀이 새 병목이 된다."
                            + " max-connections >= max-in-flight 불변식이 깨졌습니다.");
        }
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnections)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(properties.getConnectTimeout()))
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(properties.getResponseTimeout()))
                        .build())
                .build();
    }

    @Bean
    public LowestFareFinder lowestFareFinder(@Qualifier("duffelHttpClient") CloseableHttpClient duffelHttpClient,
                                             DuffelProperties properties, ObjectProvider<Clock> clock) {
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader("Duffel-Version", properties.getVersion())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(duffelHttpClient))
                .build();
        return new DuffelLowestFareFinder(restClient, properties, clock.getIfAvailable(Clock::systemUTC));
    }
}
