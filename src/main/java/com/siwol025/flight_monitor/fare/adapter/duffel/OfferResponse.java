package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /air/offers/{id} 응답. data가 offer 하나이고, offer 모양은 검색 응답과 같다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OfferResponse(OfferRequestResponse.Offer data) {
}
