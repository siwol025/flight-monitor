package com.siwol025.flight_monitor.mock.flight.service;

import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.domain.Seat;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockSeatBulkRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockSeatResponse;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockSeatRepository;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
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
        MockFlight mockFlight = mockFlightRepository.findById(flightId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.FLIGHT_NOT_FOUND));

        List<Seat> seats = new ArrayList<>();
        char[] symbols = request.seatSymbols().toCharArray();

        for (int i = request.startRow(); i <= request.endRow(); i++) {
            addSeats(seats, symbols, i, request.seatGrade(), mockFlight);
        }

        mockSeatRepository.saveAll(seats);
    }

    @Transactional
    public void reserveSeat(Long seatId) {
        Seat seat = mockSeatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.SEAT_NOT_FOUND));

        validateReserveSeat(seat);

        seat.reserve();
    }

    @Transactional
    public void cancelSeat(Long seatId) {
        Seat seat = mockSeatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.SEAT_NOT_FOUND));

        seat.cancelReservation();
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

    private void addSeats(List<Seat> seats, char[] symbols, int row, SeatGrade seatGrade, MockFlight flight) {
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
