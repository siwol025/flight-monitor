package com.siwol025.flight_monitor.user.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.user.domain.Provider;
import com.siwol025.flight_monitor.user.domain.Role;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    public boolean isFirstLogin(Provider provider, String providerId) {
        return !userRepository.existsByProviderAndProviderId(provider, providerId);
    }

    public User findOrCreateUser(Provider provider, String providerId, String email, String name) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    User user = User.builder()
                            .email(email)
                            .name(name)
                            .provider(provider)
                            .providerId(providerId)
                            .role(Role.USER)
                            .build();
                    return userRepository.save(user);
                });
    }

    public User getByUserId(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.USER_NOT_FOUND));
    }
}
