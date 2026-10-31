package com.siwol025.flight_monitor.booking.repository;

import com.siwol025.flight_monitor.booking.domain.Booking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // 같은 시각에 생성된 내역은 나중에 저장된 것(큰 ID)을 앞에 둔다
    List<Booking> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
