package com.siwol025.flight_monitor.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

    private final SubscriptionExpiryService subscriptionExpiryService;

    @Scheduled(cron = "${subscription.expiry.cron:0 0 * * * *}")
    public void expireSubscriptions() {
        subscriptionExpiryService.expireSubscriptions();
    }
}
