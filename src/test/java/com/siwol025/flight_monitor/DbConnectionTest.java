package com.siwol025.flight_monitor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DbConnectionTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void DB_연결_테스트() {
        // 간단한 쿼리 실행 (MySQL 버전 확인)
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);

        System.out.println("MySQL Version: " + version);
        Assertions.assertThat(version).isNotNull();
    }
}
