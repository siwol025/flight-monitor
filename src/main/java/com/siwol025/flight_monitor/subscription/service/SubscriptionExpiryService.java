package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    @Transactional
    public void expireSubscriptions() {
        // "오늘"은 한국 날짜를 넘긴다(DB 시간대에 기대지 않는다). 출발일 당일 노선은 그날이 끝날 때까지 감시한다
        int updated = subscriptionRepository.bulkExpireSubscriptions(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED,
                LocalDate.now(clock));
        log.info("만료 처리 완료: {}건", updated);
    }
}
