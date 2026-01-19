package com.siwol025.flight_monitor.user.repository;

import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>{
    Optional<User> findByEmail(String email);

    boolean existsByProviderAndProviderId(Provider provider, String providerId);

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
