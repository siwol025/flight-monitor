package com.siwol025.flight_monitor.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.time.Duration;
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
    void tryAcquire_키_없음_true_반환_및_setIfAbsent_TTL설정됨() {
        given(redissonClient.getBucket(EXPECTED_KEY)).willReturn(rBucket);
        given(rBucket.setIfAbsent(any(), eq(Duration.ofHours(24)))).willReturn(true);

        boolean result = cooldownManager.tryAcquire("test@test.com", 1L, SeatGrade.ECONOMY);

        assertThat(result).isTrue();
        verify(rBucket).setIfAbsent(any(), eq(Duration.ofHours(24)));
    }

    @Test
    void tryAcquire_키_존재_false_반환됨() {
        given(redissonClient.getBucket(EXPECTED_KEY)).willReturn(rBucket);
        given(rBucket.setIfAbsent(any(), eq(Duration.ofHours(24)))).willReturn(false);

        boolean result = cooldownManager.tryAcquire("test@test.com", 1L, SeatGrade.ECONOMY);

        assertThat(result).isFalse();
    }

    @Test
    void release_키_삭제됨() {
        given(redissonClient.getBucket(EXPECTED_KEY)).willReturn(rBucket);

        cooldownManager.release("test@test.com", 1L, SeatGrade.ECONOMY);

        then(rBucket).should().delete();
    }
}
