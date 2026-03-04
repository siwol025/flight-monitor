package com.siwol025.flight_monitor.monitor.lock;

import com.siwol025.flight_monitor.global.annotation.DistributedLock;
import com.siwol025.flight_monitor.global.lock.CustomSpringELParser;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
    private final Map<String, Lock> localLocks = new ConcurrentHashMap<>();

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String key = REDISSON_LOCK_PREFIX + customSpringELParser.getDynamicValue(
                signature.getParameterNames(),
                joinPoint.getArgs(),
                distributedLock.key());

        try {
            RLock rLock = redissonClient.getLock(key);
            boolean available = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!available) {
                log.info("🔒 [분산 락] 이미 다른 스레드가 처리 중입니다. Key: {}", key);
                return null;
            }

            try {
                return joinPoint.proceed();
            } finally {
                try {
                    if (rLock.isLocked() && rLock.isHeldByCurrentThread()) {
                        rLock.unlock();
                    }
                } catch (IllegalMonitorStateException e) {
                    log.warn("Redisson Lock Already UnLock {} {}", method.getName(), key);
                } catch (Exception e) {
                    // 언락 과정에서 Redis 서버가 다운되어 통신 오류가 발생해도 비즈니스 로직 결과에 영향을 주지 않도록 Catch 처리
                    log.warn("Redisson unlock 중 오류 발생: {}", e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            // 스레드 인터럽트 발생 시 정상적인 스레드 중단 과정이므로 예외를 위로 던짐
            log.error("분산 락 획득 중 인터럽트 발생", e);
            throw e;
        } catch (Exception e) {
            log.error("🚨 [Circuit Breaker] OPEN: Redis 통신 오류 발생! 로컬 락(Fallback)으로 전환합니다. Key: {}, 사유: {}", key, e.getMessage());

            return executeWithLocalLock(key, joinPoint, distributedLock);
        }
    }

    private Object executeWithLocalLock(String key, ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        Lock localLock = localLocks.computeIfAbsent(key, k -> new ReentrantLock());

        boolean available = false;
        try {
            available = localLock.tryLock(distributedLock.waitTime(), distributedLock.timeUnit());

            if (!available) {
                log.warn("⚠️ [LocalLock Fallback] 로컬 락 획득 대기 초과 (동시 실행 방어) - {}", key);
                return null;
            }

            log.info("🛡️ [LocalLock Fallback] 로컬 메모리 락을 통해 안전하게 로직을 수행합니다. - {}", key);

            return joinPoint.proceed();
        } catch (InterruptedException e) {
            log.error("로컬 락 획득 중 인터럽트 발생", e);
            throw e;
        } finally {
            if (available) {
                localLock.unlock();
            }
        }
    }
}
