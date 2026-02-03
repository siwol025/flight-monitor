package com.siwol025.flight_monitor.user.domain;

import com.siwol025.flight_monitor.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    private Role role;

    @Builder
    public User(String email, String name, Provider provider, String providerId, Role role) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // 주소값이 같으면 당연히 true
        if (!(o instanceof User user)) return false; // 타입 체크
        return id != null && id.equals(user.id); // ID(PK)가 같으면 같은 유저로 판단
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
