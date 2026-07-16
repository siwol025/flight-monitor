package com.siwol025.flight_monitor.global.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.global.scenario.DataSetupService;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.repository.FlightRepository;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class LoadTestServiceTest {

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void enqueue_count건_큐에_leftPushAll로_배치주입됨() throws Exception {
        // given
        DataSetupService dataSetupService = mock(DataSetupService.class);
        LoadTestService loadTestService = new LoadTestService(dataSetupService, flightRepository, redisTemplate, objectMapper);

        List<Flight> flights = List.of(
                Flight.builder().flightId(1L).flightNumber("KE001").airlineCode("KE")
                        .departureAirport("ICN").arrivalAirport("JFK").build(),
                Flight.builder().flightId(2L).flightNumber("KE002").airlineCode("KE")
                        .departureAirport("ICN").arrivalAirport("JFK").build(),
                Flight.builder().flightId(3L).flightNumber("KE003").airlineCode("KE")
                        .departureAirport("ICN").arrivalAirport("JFK").build()
        );
        given(flightRepository.findAll()).willReturn(flights);
        given(redisTemplate.opsForList()).willReturn(listOperations);

        // when
        int enqueued = loadTestService.enqueueTasks(5);

        // then
        assertThat(enqueued).isEqualTo(5);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(listOperations, times(1)).leftPushAll(eq(TASK_QUEUE_KEY), chunkCaptor.capture());

        List<List<String>> chunks = chunkCaptor.getAllValues();
        int totalPushed = chunks.stream().mapToInt(List::size).sum();
        assertThat(totalPushed).isEqualTo(5);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.size()).isLessThanOrEqualTo(1000));

        FlightMonitorTaskDto decoded = objectMapper.readValue(chunks.get(0).get(0), FlightMonitorTaskDto.class);
        assertThat(decoded.flightId()).isIn(1L, 2L, 3L);
        assertThat(decoded.seatGrade()).isNotNull();
    }

    @Test
    void enqueue_시드보다_많은count_요청시_가용범위내_주입() {
        // given: 항공편 3개 * 좌석등급 3개 = 최대 9개 조합만 가용
        DataSetupService dataSetupService = mock(DataSetupService.class);
        LoadTestService loadTestService = new LoadTestService(dataSetupService, flightRepository, redisTemplate, objectMapper);

        List<Flight> flights = List.of(
                Flight.builder().flightId(1L).flightNumber("KE001").airlineCode("KE")
                        .departureAirport("ICN").arrivalAirport("JFK").build(),
                Flight.builder().flightId(2L).flightNumber("KE002").airlineCode("KE")
                        .departureAirport("ICN").arrivalAirport("JFK").build(),
                Flight.builder().flightId(3L).flightNumber("KE003").airlineCode("KE")
                        .departureAirport("ICN").arrivalAirport("JFK").build()
        );
        given(flightRepository.findAll()).willReturn(flights);
        given(redisTemplate.opsForList()).willReturn(listOperations);

        // when: 가용 조합(9개)보다 훨씬 많은 100건을 요청
        int enqueued = loadTestService.enqueueTasks(100);

        // then: 가용 범위(9개) 내로 캡핑됨
        assertThat(enqueued).isEqualTo(9);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(listOperations, times(1)).leftPushAll(eq(TASK_QUEUE_KEY), chunkCaptor.capture());

        List<List<String>> chunks = chunkCaptor.getAllValues();
        int totalPushed = chunks.stream().mapToInt(List::size).sum();
        assertThat(totalPushed).isEqualTo(9);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.size()).isLessThanOrEqualTo(1000));
    }

    @Test
    void enqueue_1000건_초과시_leftPushAll이_1000단위로_청크분할됨() throws Exception {
        // given: 항공편 400개 * 좌석등급 3개 = 1200개 조합 가용 (1000건 초과 청크 분할 검증용)
        DataSetupService dataSetupService = mock(DataSetupService.class);
        LoadTestService loadTestService = new LoadTestService(dataSetupService, flightRepository, redisTemplate, objectMapper);

        List<Flight> flights = new ArrayList<>();
        for (int i = 1; i <= 400; i++) {
            flights.add(Flight.builder().flightId((long) i).flightNumber("KE" + i).airlineCode("KE")
                    .departureAirport("ICN").arrivalAirport("JFK").build());
        }
        given(flightRepository.findAll()).willReturn(flights);
        given(redisTemplate.opsForList()).willReturn(listOperations);

        // when: 가용 조합(1200개)보다 적은 1200건 요청 (전부 가용범위 내)
        int enqueued = loadTestService.enqueueTasks(1200);

        // then
        assertThat(enqueued).isEqualTo(1200);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(listOperations, times(2)).leftPushAll(eq(TASK_QUEUE_KEY), chunkCaptor.capture());

        List<List<String>> chunks = chunkCaptor.getAllValues();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.size()).isLessThanOrEqualTo(1000));
        int totalPushed = chunks.stream().mapToInt(List::size).sum();
        assertThat(totalPushed).isEqualTo(1200);

        for (List<String> chunk : chunks) {
            for (String payload : chunk) {
                FlightMonitorTaskDto decoded = objectMapper.readValue(payload, FlightMonitorTaskDto.class);
                assertThat(decoded.flightId()).isNotNull();
                assertThat(decoded.seatGrade()).isNotNull();
            }
        }
    }

    @Test
    void seedFlights_count건_생성() throws Exception {
        // given
        MockAirlineRepository mockAirlineRepository = mock(MockAirlineRepository.class);
        MockAirportRepository mockAirportRepository = mock(MockAirportRepository.class);
        MockFlightRepository mockFlightRepository = mock(MockFlightRepository.class);
        MockFlightSeatPriceRepository mockFlightSeatPriceRepository = mock(MockFlightSeatPriceRepository.class);
        FlightRepository realFlightRepository = mock(FlightRepository.class);
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EntityManager em = mock(EntityManager.class);

        given(mockAirlineRepository.findByAirlineCode(anyString())).willReturn(Optional.empty());
        given(mockAirlineRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(mockAirportRepository.findByAirportCode(anyString())).willReturn(Optional.empty());
        given(mockAirportRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AtomicLong mockFlightIdSeq = new AtomicLong(1L);
        given(mockFlightRepository.save(any())).willAnswer(inv -> {
            MockFlight mockFlight = inv.getArgument(0);
            java.lang.reflect.Field idField = MockFlight.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mockFlight, mockFlightIdSeq.getAndIncrement());
            return mockFlight;
        });
        given(realFlightRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        DataSetupService dataSetupService = new DataSetupService(
                em, mockFlightRepository, mockFlightSeatPriceRepository,
                mockAirlineRepository, mockAirportRepository, realFlightRepository,
                subscriptionRepository, userRepository);

        LoadTestService loadTestService = new LoadTestService(dataSetupService, realFlightRepository, redisTemplate, objectMapper);

        // when
        int created = loadTestService.seedFlights(5);

        // then
        assertThat(created).isEqualTo(5);
        verify(mockFlightRepository, times(5)).save(any());
        verify(realFlightRepository, times(5)).save(any());
    }
}
