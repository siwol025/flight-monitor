package com.siwol025.flight_monitor.fare;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 예매 흐름이 외부 운임 업체에 바라는 주문 기능: 결제를 미룬 주문(hold)을 만들고, 주문을 읽고, hold 주문을 결제한다.
 * 실패는 모두 {@link FlightOrderException}이며 {@link OrderFailureKind}로 결과 불명확과 재시도 가능을 구분한다.
 */
public interface FlightOrderGateway {

    /** 좌석만 잡고 결제는 미룬 주문을 만든다. 업체 주문에 우리 예매 ID를 남긴다 */
    FlightOrder createHoldOrder(HoldOrderCommand command);

    /** 주문 한 건을 읽는다. 없는 주문이면 빈 결과 */
    Optional<FlightOrder> findOrder(String orderId);

    /** offer로 만든 주문을 읽는다. hold 결과가 불명확할 때 주문이 생겼는지 확인하는 용도 */
    List<FlightOrder> findOrdersByOffer(String offerId);

    /** hold 주문을 잔액으로 결제한다. 금액은 주문 금액 표기 그대로 보낸다. correlationId는 업체 문의용 상관 ID */
    void payHeldOrder(String orderId, BigDecimal amount, String currency, String correlationId);
}
