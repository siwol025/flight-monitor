package com.siwol025.flight_monitor.monitor.lock;

import com.siwol025.flight_monitor.global.annotation.DistributedLock;
import com.siwol025.flight_monitor.global.lock.CustomSpringELParser;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAspect {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:FLIGHT:";

    private final RedissonClient redissonClient;
    private final CustomSpringELParser customSpringELParser;

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String key = REDISSON_LOCK_PREFIX + customSpringELParser.getDynamicValue(
                signature.getParameterNames(),
                joinPoint.getArgs(),
                distributedLock.key());
        RLock rLock = redissonClient.getLock(key);

        try {
            boolean available = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!available) {
                log.info("🔒 [분산 락] 이미 다른 스레드가 처리 중입니다. Key: {}", key);
                return null;
            }

            return joinPoint.proceed();
        } catch (InterruptedException e) {
            // 스레드가 인터럽트(중단) 요청을 받으면 발생하는 예외를 처리합니다.
            log.error("분산 락 획득 중 인터럽트 발생", e);
            throw e;
        } finally {
            try {
                if (rLock != null && rLock.isLocked() && rLock.isHeldByCurrentThread()) {
                    rLock.unlock();
                }
            } catch (IllegalMonitorStateException e) {
                log.warn("Redisson Lock Already UnLock {} {}", method.getName(), key);
            }
        }
    }
}
