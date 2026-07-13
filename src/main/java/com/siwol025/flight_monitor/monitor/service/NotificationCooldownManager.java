package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationCooldownManager {

    private static final String KEY_PREFIX = "notification:cooldown:";
    private static final String COOLDOWN_MARKER = "1";

    private final RedissonClient redissonClient;

    @Value("${notification.cooldown.hours:24}")
    private long cooldownHours = 24;

    public boolean tryAcquire(String email, Long flightId, SeatGrade seatGrade) {
        String key = buildKey(email, flightId, seatGrade);
        return redissonClient.getBucket(key).setIfAbsent(COOLDOWN_MARKER, Duration.ofHours(cooldownHours));
    }

    public void release(String email, Long flightId, SeatGrade seatGrade) {
        String key = buildKey(email, flightId, seatGrade);
        redissonClient.getBucket(key).delete();
    }

    private String buildKey(String email, Long flightId, SeatGrade seatGrade) {
        return KEY_PREFIX + DigestUtils.sha256Hex(email) + ":" + flightId + ":" + seatGrade.name();
    }
}
