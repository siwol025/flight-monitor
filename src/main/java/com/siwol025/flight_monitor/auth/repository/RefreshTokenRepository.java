package com.siwol025.flight_monitor.auth.repository;

import com.siwol025.flight_monitor.auth.domain.RefreshToken;
import com.siwol025.flight_monitor.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    void deleteByUser(User user);

}
