package com.siwol025.flight_monitor.mock.duffel;

/**
 * Duffel 단건 offer 조회(GET /air/offers/{id}) 응답의 부분집합. data가 offer 하나이고, offer 모양은 검색 응답과 같다.
 */
public record MockDuffelOfferDetailResponse(MockDuffelOfferResponse.Offer data) {
}
