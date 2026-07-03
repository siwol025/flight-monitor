package com.siwol025.flight_monitor.global.scenario;

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
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Tag(name = "Test Scenario", description = "장애 및 스케일 아웃 부하 테스트용 API")
@Profile({"dev", "local"})
public class TestScenarioController {

    private final DataSetupService dataSetupService;

    @PostMapping("/seed")
    @Operation(summary = "테스트 데이터 대량 생성", description = "지정한 개수만큼 가상의 항공편과 사용자의 구독 데이터를 DB에 일괄 생성합니다.")
    public String seedData(
            @Parameter(description = "생성할 데이터 건수", example = "100") @RequestParam(defaultValue = "100") int count,
            @Parameter(description = "알림을 수신할 테스트 유저수", example = "1") @RequestParam Long userCount) {
        return dataSetupService.seedData(count, userCount);
    }

    @PostMapping("/drop-prices")
    @Operation(summary = "일괄 가격 하락 트리거", description = "DB에 저장된 모든 이코노미 좌석의 가격을 20% 하락시켜 다음 스케줄러 주기 때 알림을 유발합니다.")
    public String dropPrices() {
        return dataSetupService.dropPrices();
    }

    @PostMapping("/seed-bulk")
    @Operation(summary = "대규모 부하 테스트 데이터 생성", description = "회원 1만명, 항공편 500개, 구독 1만건(모든 항공편 최소 1명 이상)을 생성합니다.")
    public String seedBulkData() {
        return dataSetupService.seedBulkData();
    }
}
