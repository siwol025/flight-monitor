package com.siwol025.flight_monitor.global.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.repository.FlightRepository;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSetupServiceTest {

    @Mock private EntityManager em;
    @Mock private MockFlightRepository mockFlightRepository;
    @Mock private MockFlightSeatPriceRepository mockFlightSeatPriceRepository;
    @Mock private MockAirlineRepository mockAirlineRepository;
    @Mock private MockAirportRepository mockAirportRepository;
    @Mock private FlightRepository flightRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private DataSetupService dataSetupService;

    @Test
    void createFlights_각항공편_3등급_좌석가격_시드됨() {
        given(mockAirlineRepository.findByAirlineCode(any())).willReturn(Optional.empty());
        given(mockAirlineRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(mockAirportRepository.findByAirportCode(any())).willReturn(Optional.empty());
        given(mockAirportRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        dataSetupService.createFlights(1);

        ArgumentCaptor<MockFlight> captor = ArgumentCaptor.forClass(MockFlight.class);
        verify(mockFlightRepository).save(captor.capture());
        MockFlight saved = captor.getValue();

        assertThat(saved.getFlightSeatPrices())
                .extracting(FlightSeatPrice::getSeatGrade)
                .containsExactlyInAnyOrder(SeatGrade.ECONOMY, SeatGrade.BUSINESS, SeatGrade.FIRST);
    }
}
