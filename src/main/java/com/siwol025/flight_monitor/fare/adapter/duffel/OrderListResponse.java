package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * GET /air/orders?offer_id= 응답. 주문 한 건의 형식은 {@link OrderResponse.Data}와 같다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OrderListResponse(List<OrderResponse.Data> data) {
}
