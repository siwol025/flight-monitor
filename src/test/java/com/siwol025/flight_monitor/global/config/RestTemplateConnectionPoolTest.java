package com.siwol025.flight_monitor.global.config;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HTTP 커넥션 풀 사이징·정합 가드 검증.
 *
 * <p>상향된 in-flight 세마포어 permit 을 실제로 받쳐주려면 HTTP 커넥션 풀이
 * {@code max-in-flight} 이상으로 사이징되어야 한다. 풀이 세마포어보다 작으면 그게 새 병목이 된다.
 */
class RestTemplateConnectionPoolTest {

    private final RestTemplateConfig config = new RestTemplateConfig();

    @Test
    void HTTP풀_maxConn_maxInFlight이상으로_사이징됨() {
        int maxInFlight = 780;

        PoolingHttpClientConnectionManager cm =
                config.monitoringHttpConnectionManager(maxInFlight, maxInFlight);

        assertThat(cm.getMaxTotal())
                .as("풀 max-total 은 max-in-flight 이상이어야 한다")
                .isGreaterThanOrEqualTo(maxInFlight);
        assertThat(cm.getDefaultMaxPerRoute())
                .as("풀 max-per-route 는 max-in-flight 이상이어야 한다")
                .isGreaterThanOrEqualTo(maxInFlight);
    }

    @Test
    void 정합가드_HTTP풀_maxConn이_maxInFlight미만이면_기동실패() {
        assertThatThrownBy(() -> config.monitoringHttpConnectionManager(100, 780))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-connections")
                .hasMessageContaining("max-in-flight");
    }
}
