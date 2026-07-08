package com.siwol025.flight_monitor.monitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertConditionCheckerTest {

    private AlertConditionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new AlertConditionChecker();
    }

    @Test
    void shouldNotify_조건없음_가격하락_true() {
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(120_000);

        boolean result = checker.shouldNotify(oldPrice, newPrice, null, null);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_조건없음_가격동일_false() {
        BigDecimal price = BigDecimal.valueOf(150_000);

        boolean result = checker.shouldNotify(price, price, null, null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_조건없음_가격상승_false() {
        BigDecimal oldPrice = BigDecimal.valueOf(120_000);
        BigDecimal newPrice = BigDecimal.valueOf(150_000);

        boolean result = checker.shouldNotify(oldPrice, newPrice, null, null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_targetPrice_달성_true() {
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(90_000);
        BigDecimal targetPrice = BigDecimal.valueOf(100_000);

        boolean result = checker.shouldNotify(oldPrice, newPrice, targetPrice, null);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_targetPrice_미달_false() {
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(110_000);
        BigDecimal targetPrice = BigDecimal.valueOf(100_000);

        boolean result = checker.shouldNotify(oldPrice, newPrice, targetPrice, null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_targetPrice_정확히같음_true() {
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(100_000);
        BigDecimal targetPrice = BigDecimal.valueOf(100_000);

        boolean result = checker.shouldNotify(oldPrice, newPrice, targetPrice, null);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_dropThreshold_달성_true() {
        // 하락률 = (150000-130000)/150000*100 = 13.33% >= 10%
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(130_000);
        BigDecimal dropThresholdPercent = BigDecimal.valueOf(10.00);

        boolean result = checker.shouldNotify(oldPrice, newPrice, null, dropThresholdPercent);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_dropThreshold_미달_false() {
        // 하락률 = (150000-145000)/150000*100 = 3.33% < 10%
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(145_000);
        BigDecimal dropThresholdPercent = BigDecimal.valueOf(10.00);

        boolean result = checker.shouldNotify(oldPrice, newPrice, null, dropThresholdPercent);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotify_둘다지정_targetPrice만_충족_true() {
        // targetPrice 충족: 120000 <= 130000 → true
        // threshold 미충족: (150000-120000)/150000*100 = 20% < 25% → false
        // OR → true
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(120_000);
        BigDecimal targetPrice = BigDecimal.valueOf(130_000);
        BigDecimal dropThresholdPercent = BigDecimal.valueOf(25.00);

        boolean result = checker.shouldNotify(oldPrice, newPrice, targetPrice, dropThresholdPercent);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_둘다지정_threshold만_충족_true() {
        // targetPrice 미충족: 120000 > 100000 → false
        // threshold 충족: (150000-120000)/150000*100 = 20% >= 10% → true
        // OR → true
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(120_000);
        BigDecimal targetPrice = BigDecimal.valueOf(100_000);
        BigDecimal dropThresholdPercent = BigDecimal.valueOf(10.00);

        boolean result = checker.shouldNotify(oldPrice, newPrice, targetPrice, dropThresholdPercent);

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotify_둘다지정_둘다_미충족_false() {
        // targetPrice 미충족: 130000 > 100000 → false
        // threshold 미충족: (150000-130000)/150000*100 = 13.33% < 25% → false
        // OR → false
        BigDecimal oldPrice = BigDecimal.valueOf(150_000);
        BigDecimal newPrice = BigDecimal.valueOf(130_000);
        BigDecimal targetPrice = BigDecimal.valueOf(100_000);
        BigDecimal dropThresholdPercent = BigDecimal.valueOf(25.00);

        boolean result = checker.shouldNotify(oldPrice, newPrice, targetPrice, dropThresholdPercent);

        assertThat(result).isFalse();
    }
}
