package com.siwol025.flight_monitor.global.loadtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.global.scenario.DataSetupService;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.repository.FlightRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 측정 리그(부하 테스트) 전용 서비스.
 * (a) 파라미터화된 개수만큼 목 항공편을 시딩하고,
 * (b) 파라미터화된 개수만큼 모니터링 태스크를 Redis 큐에 직접 주입해
 * consumer 경로를 producer/구독 경로와 독립적으로 부하 테스트할 수 있게 한다.
 */
@Slf4j
@Service
@Profile("loadtest")
@RequiredArgsConstructor
public class LoadTestService {

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";
    private static final int CHUNK_SIZE = 1000;

    private final DataSetupService dataSetupService;
    private final FlightRepository flightRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public int seedFlights(int count) {
        List<Flight> flights = dataSetupService.createFlights(count);
        log.info("[LoadTestService] 항공편 {}건 시딩 완료", flights.size());
        return flights.size();
    }

    public int enqueueTasks(int count) {
        List<Long> flightIds = flightRepository.findAll().stream()
                .map(Flight::getFlightId)
                .toList();

        if (flightIds.isEmpty()) {
            log.warn("[LoadTestService] 시딩된 항공편이 없어 큐 주입을 건너뜁니다.");
            return 0;
        }

        List<FlightMonitorTaskDto> combinations = new ArrayList<>();
        for (Long flightId : flightIds) {
            for (SeatGrade seatGrade : SeatGrade.values()) {
                combinations.add(new FlightMonitorTaskDto(flightId, seatGrade));
            }
        }

        int actualCount = Math.min(count, combinations.size());
        List<FlightMonitorTaskDto> targets = combinations.subList(0, actualCount);

        for (int i = 0; i < targets.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, targets.size());
            List<String> jsonPayloads = targets.subList(i, end).stream()
                    .map(this::toJson)
                    .toList();
            redisTemplate.opsForList().leftPushAll(TASK_QUEUE_KEY, jsonPayloads);
        }

        log.info("[LoadTestService] 모니터링 태스크 {}건 큐 주입 완료 (요청: {}건, 가용 조합: {}건)",
                actualCount, count, combinations.size());
        return actualCount;
    }

    private String toJson(FlightMonitorTaskDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("[LoadTestService] JSON 직렬화 실패: {}", dto, e);
            throw new RuntimeException("작업 페이로드 직렬화 오류", e);
        }
    }
}
