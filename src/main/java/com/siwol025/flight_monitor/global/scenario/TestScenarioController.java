package com.siwol025.flight_monitor.global.scenario;

import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Tag(name = "Test Scenario", description = "장애 및 스케일 아웃 부하 테스트용 API")
public class TestScenarioController {

    private final EntityManager em;

    @PostMapping("/seed")
    @Transactional
    @Operation(summary = "테스트 데이터 대량 생성", description = "지정한 개수만큼 가상의 항공편과 사용자의 구독 데이터를 DB에 일괄 생성합니다.")
    public String seedData(
            @Parameter(description = "생성할 데이터 건수", example = "100") @RequestParam(defaultValue = "100") int count,
            @Parameter(description = "알림을 수신할 테스트 유저의 ID", example = "1") @RequestParam Long userId) {

        User user = em.find(User.class, userId);
        if (user == null) {
            return "실패: ID가 " + userId + "인 유저를 찾을 수 없습니다.";
        }

        // 수정: 항공사가 이미 존재하는지 확인 후 생성
        MockAirline airline = em.createQuery("SELECT a FROM MockAirline a WHERE a.airlineCode = :code", MockAirline.class)
                .setParameter("code", "T_AIR")
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    MockAirline newAirline = MockAirline.builder().airlineCode("T_AIR").airlineName("테스트항공").build();
                    em.persist(newAirline);
                    return newAirline;
                });

        // 수정: 출발 공항이 이미 존재하는지 확인 후 생성
        MockAirport dep = em.createQuery("SELECT a FROM MockAirport a WHERE a.airportCode = :code", MockAirport.class)
                .setParameter("code", "ICN")
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    MockAirport newDep = MockAirport.builder().airportCode("ICN").airportName("인천").build();
                    em.persist(newDep);
                    return newDep;
                });

        // 수정: 도착 공항이 이미 존재하는지 확인 후 생성
        MockAirport arr = em.createQuery("SELECT a FROM MockAirport a WHERE a.airportCode = :code", MockAirport.class)
                .setParameter("code", "JFK")
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    MockAirport newArr = MockAirport.builder().airportCode("JFK").airportName("뉴욕").build();
                    em.persist(newArr);
                    return newArr;
                });

        BigDecimal initialPrice = new BigDecimal("1000000.00");
        LocalDateTime departureTime = LocalDateTime.now().plusDays(30);
        LocalDateTime arrivalTime = departureTime.plusHours(14);

        // 수정: 매우 짧은 랜덤 접두사 생성 (예: "A1B2")
        String shortPrefix = java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        for (int i = 1; i <= count; i++) {
            // 수정: 최대 8~9자리의 짧은 고유 비행기 번호 생성 (예: "TA1B2-100")
            String uniqueFlightNumber = "T" + shortPrefix + "-" + i;

            MockFlight mockFlight = MockFlight.builder()
                    .flightNumber(uniqueFlightNumber)
                    .airline(airline)
                    .departureAirport(dep)
                    .arrivalAirport(arr)
                    .departureTime(departureTime)
                    .arrivalTime(arrivalTime)
                    .build();

            FlightSeatPrice seatPrice = FlightSeatPrice.builder()
                    .flight(mockFlight)
                    .seatGrade(SeatGrade.ECONOMY)
                    .price(initialPrice)
                    .build();
            mockFlight.addFlightSeatPrice(seatPrice);
            em.persist(mockFlight);

            Flight flight = Flight.builder()
                    .flightId(mockFlight.getId())
                    .flightNumber(mockFlight.getFlightNumber())
                    .airlineCode(airline.getAirlineCode())
                    .departureAirport(dep.getAirportCode())
                    .arrivalAirport(arr.getAirportCode())
                    .departureTime(mockFlight.getDepartureTime())
                    .arrivalTime(mockFlight.getArrivalTime())
                    .build();
            em.persist(flight);

            Subscription subscription = Subscription.builder()
                    .user(user)
                    .flight(flight)
                    .seatGrade(SeatGrade.ECONOMY)
                    .price(initialPrice)
                    .build();
            em.persist(subscription);
        }

        log.info("✅ 테스트 데이터 {}건 시딩 완료", count);
        return count + "건의 테스트 데이터가 성공적으로 생성되었습니다.";
    }

    @PostMapping("/drop-prices")
    @Transactional
    @Operation(summary = "일괄 가격 하락 트리거", description = "DB에 저장된 모든 이코노미 좌석의 가격을 20% 하락시켜 다음 스케줄러 주기 때 알림을 유발합니다.")
    public String dropPrices() {
        List<FlightSeatPrice> prices = em.createQuery("SELECT p FROM FlightSeatPrice p WHERE p.seatGrade = :grade", FlightSeatPrice.class)
                .setParameter("grade", SeatGrade.ECONOMY)
                .getResultList();

        BigDecimal discountRate = new BigDecimal("0.8");

        for (FlightSeatPrice p : prices) {
            BigDecimal newPrice = p.getPrice().multiply(discountRate);
            p.updatePrice(newPrice);
        }

        log.info("✅ {}건의 항공권 가격이 20% 하락되었습니다.", prices.size());
        return prices.size() + "건의 항공권 가격이 하락되었습니다.";
    }
}
