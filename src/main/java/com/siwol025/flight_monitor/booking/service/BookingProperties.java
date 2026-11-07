package com.siwol025.flight_monitor.booking.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 예매 설정. 근거는 application.yml의 booking 주석에 있다 */
@Getter
@Setter
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {

    /** 고정 환율(1 USD당 원). 고객 결제 금액은 업체 금액 × 이 값을 원 단위로 올린 값이다 */
    private BigDecimal usdToKrwRate = new BigDecimal("1400");

    /** offer 만료까지 이보다 적게 남았으면 hold하지 않는다(사용자가 결제를 마칠 시간) */
    private Duration offerExpiryBuffer = Duration.ofMinutes(2);

    /** 예매를 허용하는 판매 항공사(offer owner) IATA 코드 */
    private List<String> allowedOwnerCodes = List.of("ZZ");
}
