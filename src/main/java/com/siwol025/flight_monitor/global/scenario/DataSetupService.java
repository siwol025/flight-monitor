package com.siwol025.flight_monitor.global.scenario;

import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.repository.FlightRepository;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile({"dev", "local", "loadtest"})
@RequiredArgsConstructor
@Transactional
public class DataSetupService {

    // flush/clear only — bulk data memory management
    private final EntityManager em;
    private final MockFlightRepository mockFlightRepository;
    private final MockFlightSeatPriceRepository mockFlightSeatPriceRepository;
    private final MockAirlineRepository mockAirlineRepository;
    private final MockAirportRepository mockAirportRepository;
    private final FlightRepository flightRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public String seedData(int count, Long userCount) {
        List<User> users = userRepository.findAll(PageRequest.of(0, userCount.intValue())).getContent();
        if (users.isEmpty()) {
            return "실패: 유저를 찾을 수 없습니다.";
        }

        MockAirline airline = findOrCreateAirline("T_AIR", "테스트항공");
        MockAirport dep = findOrCreateAirport("ICN", "인천");
        MockAirport arr = findOrCreateAirport("JFK", "뉴욕");

        BigDecimal initialPrice = new BigDecimal("1000000.00");
        LocalDateTime departureTime = LocalDateTime.now().plusDays(30);
        LocalDateTime arrivalTime = departureTime.plusHours(14);

        String shortPrefix = java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        for (int i = 1; i <= count; i++) {
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
            mockFlightRepository.save(mockFlight);

            Flight flight = Flight.builder()
                    .flightId(mockFlight.getId())
                    .flightNumber(mockFlight.getFlightNumber())
                    .airlineCode(airline.getAirlineCode())
                    .departureAirport(dep.getAirportCode())
                    .arrivalAirport(arr.getAirportCode())
                    .departureTime(mockFlight.getDepartureTime())
                    .arrivalTime(mockFlight.getArrivalTime())
                    .build();
            flightRepository.save(flight);

            Subscription subscription = Subscription.builder()
                    .user(users.get((i + 1) % users.size()))
                    .flight(flight)
                    .seatGrade(SeatGrade.ECONOMY)
                    .price(initialPrice)
                    .build();
            subscriptionRepository.save(subscription);
        }

        log.info("[DataSetupService] 테스트 데이터 {}건 시딩 완료", count);
        return count + "건의 테스트 데이터가 성공적으로 생성되었습니다.";
    }

    public String dropPrices() {
        List<FlightSeatPrice> prices = mockFlightSeatPriceRepository.findBySeatGrade(SeatGrade.ECONOMY);

        BigDecimal discountRate = new BigDecimal("0.98");

        for (FlightSeatPrice p : prices) {
            BigDecimal newPrice = p.getPrice().multiply(discountRate);
            p.updatePrice(newPrice);
        }

        log.info("[DataSetupService] {}건의 항공권 가격이 20% 하락되었습니다.", prices.size());
        return prices.size() + "건의 항공권 가격이 하락되었습니다.";
    }

    public String seedBulkData() {
        int userCount = 10000;
        int flightCount = 500;

        long startTime = System.currentTimeMillis();

        // 1. 유저 1만명 생성
        List<User> users = new ArrayList<>(userCount);
        for (int i = 1; i <= userCount; i++) {
            User user = User.builder()
                    .email("testuser" + i + "@example.com")
                    .name("User" + i)
                    .provider(Provider.GOOGLE)
                    .providerId("local_" + i)
                    .role(Role.USER)
                    .build();
            userRepository.save(user);
            users.add(user);
        }
        log.info("[DataSetupService] 유저 {}명 생성 완료", userCount);

        // 2, 3. 항공사/공항 기초 데이터 확인 후 항공편 500개 생성
        List<Flight> flights = createFlights(flightCount);
        log.info("[DataSetupService] 항공편 {}개 생성 완료", flightCount);

        BigDecimal initialPrice = new BigDecimal("1000000.00");

        // 4. 구독 1만건 생성 (모든 항공편 최소 1명 할당)
        int userIndex = 0;

        // 그룹 1: 1~10번째 항공편(10개) - 각 456명 구독 (총 4,560건)
        for (int i = 0; i < 10; i++) {
            Flight targetFlight = flights.get(i);
            for (int j = 0; j < 456; j++) {
                createSubscription(users.get(userIndex++), targetFlight, initialPrice);
            }
        }

        // 그룹 2: 11~60번째 항공편(50개) - 각 100명 구독 (총 5,000건)
        for (int i = 10; i < 60; i++) {
            Flight targetFlight = flights.get(i);
            for (int j = 0; j < 100; j++) {
                createSubscription(users.get(userIndex++), targetFlight, initialPrice);
            }
        }

        // 그룹 3: 나머지 항공편(440개) - 각 1명 구독 (총 440건)
        for (int i = 60; i < 500; i++) {
            Flight targetFlight = flights.get(i);
            createSubscription(users.get(userIndex++), targetFlight, initialPrice);
        }

        log.info("[DataSetupService] 구독 10,000건 생성 완료");

        // 영속성 컨텍스트 초기화 (메모리 확보)
        em.flush();
        em.clear();

        long endTime = System.currentTimeMillis();
        return String.format("완료: 유저 %d명, 항공편 %d개, 구독 10000건 생성 (소요 시간: %d ms)",
                userCount, flightCount, (endTime - startTime));
    }

    /**
     * 항공사/공항 기초 데이터를 확인(없으면 생성)한 뒤, 지정한 개수만큼 MockFlight + 연동된 Flight 를 생성한다.
     * seedBulkData() 및 LoadTestService(loadtest 프로필)에서 공용으로 사용하는 헬퍼.
     */
    public List<Flight> createFlights(int count) {
        MockAirline airline = findOrCreateAirline("T_AIR", "테스트항공");
        MockAirport dep = findOrCreateAirport("ICN", "인천");
        MockAirport arr = findOrCreateAirport("JFK", "뉴욕");

        List<Flight> flights = new ArrayList<>(count);
        BigDecimal initialPrice = new BigDecimal("1000000.00");
        LocalDateTime departureTime = LocalDateTime.now().plusDays(30);
        LocalDateTime arrivalTime = departureTime.plusHours(14);
        String shortPrefix = java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        for (int i = 1; i <= count; i++) {
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
            mockFlightRepository.save(mockFlight);

            Flight flight = Flight.builder()
                    .flightId(mockFlight.getId())
                    .flightNumber(mockFlight.getFlightNumber())
                    .airlineCode(airline.getAirlineCode())
                    .departureAirport(dep.getAirportCode())
                    .arrivalAirport(arr.getAirportCode())
                    .departureTime(mockFlight.getDepartureTime())
                    .arrivalTime(mockFlight.getArrivalTime())
                    .build();
            flightRepository.save(flight);
            flights.add(flight);
        }
        return flights;
    }

    MockAirline findOrCreateAirline(String code, String name) {
        return mockAirlineRepository.findByAirlineCode(code)
                .orElseGet(() -> {
                    MockAirline newAirline = MockAirline.builder().airlineCode(code).airlineName(name).build();
                    return mockAirlineRepository.save(newAirline);
                });
    }

    MockAirport findOrCreateAirport(String code, String name) {
        return mockAirportRepository.findByAirportCode(code)
                .orElseGet(() -> {
                    MockAirport newAirport = MockAirport.builder().airportCode(code).airportName(name).build();
                    return mockAirportRepository.save(newAirport);
                });
    }

    private void createSubscription(User user, Flight flight, BigDecimal price) {
        Subscription subscription = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(SeatGrade.ECONOMY)
                .price(price)
                .build();
        subscriptionRepository.save(subscription);
    }
}
