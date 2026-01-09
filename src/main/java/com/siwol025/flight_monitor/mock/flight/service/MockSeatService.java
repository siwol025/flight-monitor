package com.siwol025.flight_monitor.mock.flight.service;

import com.siwol025.flight_monitor.domain.flight.Flight;
import com.siwol025.flight_monitor.domain.flight.Seat;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockSeatBulkRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockSeatResponse;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockSeatRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockSeatService {
    private final MockSeatRepository mockSeatRepository;
    private final MockFlightRepository mockFlightRepository;

    @Transactional
    public void createBulkSeats(Long flightId, MockSeatBulkRequest request) {
        Flight flight = mockFlightRepository.findById(flightId)
                .orElseThrow(() -> new IllegalArgumentException("항공편을 찾을 수 없습니다."));

        List<Seat> seats = new ArrayList<>();
        char[] symbols = request.seatSymbols().toCharArray();

        for (int i = request.startRow(); i <= request.endRow(); i++) {
            addSeats(seats, symbols, i, request.seatGrade(), flight);
        }

        mockSeatRepository.saveAll(seats);
    }

    @Transactional
    public void reserveSeat(Long seatId) {
        Seat seat = mockSeatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("해당 좌석을 찾을 수 없습니다."));

        validateReserveSeat(seat);

        seat.reserve();
    }

    public List<MockSeatResponse> readSeatsByFlight(Long flightId) {
        List<Seat> seats = mockSeatRepository.findSeatsByFlightIdOrderBySeatNumber(flightId);

        return seats.stream()
                .map(MockSeatResponse::of)
                .toList();
    }

    public List<MockSeatResponse> readSeatsByFlightAndSeatGrade(Long flightId, SeatGrade seatGrade) {
        List<Seat> seats = mockSeatRepository.findSeatsByFlightIdAndSeatGrade(flightId, seatGrade);

        return seats.stream()
                .map(MockSeatResponse::of)
                .toList();
    }

    public List<MockSeatResponse> readSeats() {
        List<Seat> seats = mockSeatRepository.findAll();

        return seats.stream()
                .map(MockSeatResponse::of)
                .toList();
    }

    private void addSeats(List<Seat> seats, char[] symbols, int row, SeatGrade seatGrade, Flight flight) {
        for (char symbol : symbols) {
            String seatNumber = row + String.valueOf(symbol);

            seats.add(Seat.builder()
                    .seatGrade(seatGrade)
                    .seatNumber(seatNumber)
                    .flight(flight)
                    .isBooked(false)
                    .build()
            );
        }
    }

    private void validateReserveSeat(Seat seat) {
        if (seat.isBooked()) {
            throw new IllegalArgumentException("이미 예약된 좌석입니다.");
        }
    }
}
