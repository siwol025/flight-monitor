package com.siwol025.flight_monitor.monitor.scheduler;

import com.siwol025.flight_monitor.monitor.service.MonitorService;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.subscription.dto.FlightSeatGradeDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceScheduler {

    private final SubscriptionService subscriptionService;
    private final MonitorService monitorService;

    @Scheduled(fixedRate = 600000)
    public void runPriceMonitoring() {
        List<FlightSeatGradeDto> flights = subscriptionService.getActiveFlights();

        log.info("=== [가격 모니터링 스케줄러 시작] 총 감시 대상 수: {}건 ===", flights.size());

        for (FlightSeatGradeDto flight : flights) {
            try {
                monitorService.checkAndUpdatePrice(flight.flightId(), flight.seatGrade());

                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.error("스케줄러 스레드 중단 발생: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[모니터링 실패] 항공편 ID: {}, 좌석: {}, 사유: {}",
                        flight.flightId(), flight.seatGrade(), e.getMessage());
            }
        }

        log.info("=== [가격 모니터링 스케줄러 종료] 모든 대상 처리가 완료되었습니다. ===");
    }
}
