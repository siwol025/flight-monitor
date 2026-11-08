package com.siwol025.flight_monitor.notification.producer;

import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import java.util.concurrent.CompletionStage;

public interface NotificationPublisher {

    /**
     * 가격 하락 이벤트를 발행한다. 동기적으로 예외를 던지지 않는다.
     *
     * @return 브로커가 받아 큐까지 라우팅했다고 확인하면 정상 완료된다. 확인 거부·라우팅 실패·확인 대기 시간 초과·전송 실패면
     *         {@link PriceDropPublishException}으로 실패 완료된다. 정상 완료는 항상 브로커 I/O 스레드가 아닌 알림 executor에서
     *         일어나므로 이어지는 작업에서 DB를 써도 된다(실패 완료는 다른 스레드에서 일어날 수 있다)
     */
    CompletionStage<Void> publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto);
}
