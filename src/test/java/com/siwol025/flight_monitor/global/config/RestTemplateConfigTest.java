package com.siwol025.flight_monitor.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static com.siwol025.flight_monitor.global.config.RestTemplateConfig.CONNECT_TIMEOUT_MS;
import static com.siwol025.flight_monitor.global.config.RestTemplateConfig.READ_TIMEOUT_MS;
import static org.assertj.core.api.Assertions.assertThat;

class RestTemplateConfigTest {

    @Test
    void restTemplate_connectTimeout과_readTimeout이_지정된_값으로_설정되어야한다() throws Exception {
        RestTemplateConfig config = new RestTemplateConfig();
        RestTemplate restTemplate = config.restTemplate();

        assertThat(restTemplate.getRequestFactory())
                .as("RequestFactory는 SimpleClientHttpRequestFactory이어야 한다")
                .isInstanceOf(SimpleClientHttpRequestFactory.class);

        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();

        Field connectTimeoutField = SimpleClientHttpRequestFactory.class.getDeclaredField("connectTimeout");
        connectTimeoutField.setAccessible(true);
        int connectTimeout = (int) connectTimeoutField.get(factory);

        Field readTimeoutField = SimpleClientHttpRequestFactory.class.getDeclaredField("readTimeout");
        readTimeoutField.setAccessible(true);
        int readTimeout = (int) readTimeoutField.get(factory);

        assertThat(connectTimeout)
                .as("connectTimeout은 %dms이어야 한다", CONNECT_TIMEOUT_MS)
                .isEqualTo(CONNECT_TIMEOUT_MS);

        assertThat(readTimeout)
                .as("readTimeout은 %dms이어야 한다", READ_TIMEOUT_MS)
                .isEqualTo(READ_TIMEOUT_MS);
    }
}
