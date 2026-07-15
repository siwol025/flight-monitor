package com.siwol025.flight_monitor.global.loadtest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loadtest")
@RequiredArgsConstructor
@Tag(name = "Load Test", description = "측정 리그(부하 테스트) 전용 API - consumer 경로 독립 부하 테스트")
@Profile("loadtest")
public class LoadTestController {

    private final LoadTestService loadTestService;

    @PostMapping("/seed-flights")
    @Operation(summary = "목 항공편 파라미터화 시딩", description = "지정한 개수만큼 목 항공편(및 연동 Flight)을 생성합니다.")
    public String seedFlights(
            @Parameter(description = "생성할 항공편 개수", example = "100") @RequestParam int count) {
        int created = loadTestService.seedFlights(count);
        return created + "건의 항공편이 생성되었습니다.";
    }

    @PostMapping("/enqueue")
    @Operation(summary = "모니터링 태스크 큐 직접 주입", description = "시딩된 항공편 기준으로 지정한 개수만큼 모니터링 태스크를 Redis 큐에 직접 주입합니다.")
    public String enqueue(
            @Parameter(description = "주입할 태스크 개수", example = "1000") @RequestParam int count) {
        int enqueued = loadTestService.enqueueTasks(count);
        return enqueued + "건의 모니터링 태스크가 큐에 주입되었습니다.";
    }
}
