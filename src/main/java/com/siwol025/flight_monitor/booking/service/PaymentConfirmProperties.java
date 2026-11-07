package com.siwol025.flight_monitor.booking.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 승인(⑤)의 인라인 재요청 설정. 근거는 application.yml의 payment.toss 주석에 있다.
 * 키는 Toss 설정과 같은 곳(payment.toss)에 두지만 쓰는 쪽은 승인 서비스다(어댑터는 재요청하지 않는다).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payment.toss")
public class PaymentConfirmProperties {

    /** 결과가 불명확한 승인을 조회로 미승인(IN_PROGRESS) 확인한 뒤 요청 안에서 다시 보내는 최대 횟수(0.5s·1s 간격) */
    private int confirmInlineRetries = 2;
}
