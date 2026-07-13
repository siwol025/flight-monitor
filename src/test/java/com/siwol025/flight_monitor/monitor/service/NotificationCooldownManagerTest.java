package com.siwol025.flight_monitor.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.util.concurrent.TimeUnit;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class NotificationCooldownManagerTest {

    private static final String EXPECTED_KEY =
            "notification:cooldown:" + DigestUtils.sha256Hex("test@test.com") + ":1:ECONOMY";

    @Mock
    RedissonClient redissonClient;

    @Mock
    RBucket<Object> rBucket;

    @InjectMocks
    NotificationCooldownManager cooldownManager;

    @Test
    void isInCooldown_키_존재_true_반환됨() {
        given(redissonClient.getBucket(EXPECTED_KEY)).willReturn(rBucket);
        given(rBucket.isExists()).willReturn(true);

        boolean result = cooldownManager.isInCooldown("test@test.com", 1L, SeatGrade.ECONOMY);

        assertThat(result).isTrue();
    }

    @Test
    void isInCooldown_키_없음_false_반환됨() {
        given(redissonClient.getBucket(EXPECTED_KEY)).willReturn(rBucket);
        given(rBucket.isExists()).willReturn(false);

        boolean result = cooldownManager.isInCooldown("test@test.com", 1L, SeatGrade.ECONOMY);

        assertThat(result).isFalse();
    }

    @Test
    void recordCooldown_키_저장_및_TTL_설정됨() {
        given(redissonClient.getBucket(EXPECTED_KEY)).willReturn(rBucket);

        cooldownManager.recordCooldown("test@test.com", 1L, SeatGrade.ECONOMY);

        verify(rBucket).set(any(), eq(24L), eq(TimeUnit.HOURS));
    }
}
