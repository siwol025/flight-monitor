package com.siwol025.flight_monitor.mock.duffel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siwol025.flight_monitor.mock.duffel.MockDuffelRateLimitEnforcer.RateLimitStatus;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MockDuffelRateLimitEnforcerTest {

    private static final String OFFER_REQUEST = """
            {"data":{"slices":[{"origin":"ICN","destination":"NRT","departure_date":"2026-11-26"}],
            "passengers":[{"type":"adult"}],"cabin_class":"economy"}}""";

    @Test
    void 한도초과_429와_ratelimit헤더반환() throws Exception {
        // 진짜 컨트롤러·한도 강제기·서비스, DB 경계(리포지토리)만 목. 시각은 고정: 16:07:44가 속한 창은 16:08:00에 끝난다
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T16:07:44Z"), ZoneOffset.UTC);
        MockDuffelOfferController controller = new MockDuffelOfferController(
                new MockDuffelOfferService(mock(MockFlightRepository.class)),
                new MockDuffelRateLimitEnforcer(30, Duration.ofSeconds(60)),
                new StaticListableBeanFactory(Map.of("clock", clock)).getBeanProvider(Clock.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        // 정상 응답(201)에도 세 헤더가 붙는다
        mockMvc.perform(offerRequest())
                .andExpect(status().isCreated())
                .andExpect(header().string("ratelimit-limit", "30"))
                .andExpect(header().string("ratelimit-remaining", "29"))
                .andExpect(header().string("ratelimit-reset", "Sun, 25 Oct 2026 16:08:00 GMT"));
        for (int i = 2; i <= 30; i++) {
            mockMvc.perform(offerRequest()).andExpect(status().isCreated());
        }

        mockMvc.perform(offerRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("ratelimit-limit", "30"))
                .andExpect(header().string("ratelimit-remaining", "0"))
                .andExpect(header().string("ratelimit-reset", "Sun, 25 Oct 2026 16:08:00 GMT"))
                .andExpect(jsonPath("$.errors[0].type").value("rate_limit_error"))
                .andExpect(jsonPath("$.errors[0].code").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.meta.status").value(429));
    }

    private static MockHttpServletRequestBuilder offerRequest() {
        return post("/air/offer_requests?return_offers=true&supplier_timeout=10000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OFFER_REQUEST);
    }

    @Test
    void 창경과후_한도회복() {
        // 창은 기간 경계에 맞춘다: 16:07:10이 속한 60초 창은 16:07:00~16:08:00
        MockDuffelRateLimitEnforcer enforcer = new MockDuffelRateLimitEnforcer(2, Duration.ofSeconds(60));
        enforcer.acquire(Instant.parse("2026-10-25T16:07:10Z"));
        enforcer.acquire(Instant.parse("2026-10-25T16:07:11Z"));

        assertThat(enforcer.acquire(Instant.parse("2026-10-25T16:07:50Z")))
                .isEqualTo(new RateLimitStatus(false, 2, 0, Instant.parse("2026-10-25T16:08:00Z")));
        assertThat(enforcer.acquire(Instant.parse("2026-10-25T16:08:00Z")))
                .isEqualTo(new RateLimitStatus(true, 2, 1, Instant.parse("2026-10-25T16:09:00Z")));
    }
}
