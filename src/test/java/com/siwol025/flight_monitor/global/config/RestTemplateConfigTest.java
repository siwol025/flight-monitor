package com.siwol025.flight_monitor.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static com.siwol025.flight_monitor.global.config.RestTemplateConfig.CONNECT_TIMEOUT_MS;
import static com.siwol025.flight_monitor.global.config.RestTemplateConfig.READ_TIMEOUT_MS;
import static org.assertj.core.api.Assertions.assertThat;

class RestTemplateConfigTest {

    @Test
    void HTTP클라이언트_커넥션풀링_활성화됨_재사용() {
        // 커넥션 풀링 팩토리(HttpComponentsClientHttpRequestFactory) 를 써야 커넥션이 재사용된다.
        // SimpleClientHttpRequestFactory(풀 없음) 는 매 호출 핸드셰이크를 반복하므로 배제.
        RestTemplateConfig config = new RestTemplateConfig();
        RestTemplate restTemplate = config.restTemplate(
                config.monitoringHttpClient(config.monitoringHttpConnectionManager(780, 100)));

        assertThat(restTemplate.getRequestFactory())
                .as("RequestFactory 는 커넥션 풀링 가능한 HttpComponentsClientHttpRequestFactory 여야 한다")
                .isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void restTemplate_connectTimeout과_readTimeout이_지정된_값으로_설정되어야한다() {
        // Spring 7 의 HttpComponentsClientHttpRequestFactory 는 타임아웃 세터를 노출하지 않는다.
        // 타임아웃은 httpclient5 의 ConnectionConfig(connect/socket) 와 RequestConfig(response) 에 산다.
        assertThat(RestTemplateConfig.connectionConfig().getConnectTimeout().toMilliseconds())
                .as("connectTimeout 은 %dms 여야 한다", CONNECT_TIMEOUT_MS)
                .isEqualTo(CONNECT_TIMEOUT_MS);

        assertThat(RestTemplateConfig.connectionConfig().getSocketTimeout().toMilliseconds())
                .as("socketTimeout(readTimeout) 은 %dms 여야 한다", READ_TIMEOUT_MS)
                .isEqualTo(READ_TIMEOUT_MS);

        assertThat(RestTemplateConfig.requestConfig().getResponseTimeout().toMilliseconds())
                .as("responseTimeout(readTimeout) 은 %dms 여야 한다", READ_TIMEOUT_MS)
                .isEqualTo(READ_TIMEOUT_MS);
    }
}
