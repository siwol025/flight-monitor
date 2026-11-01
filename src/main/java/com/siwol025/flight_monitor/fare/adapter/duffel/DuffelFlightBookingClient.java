package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.fare.FareProviderConfigurationException;
import com.siwol025.flight_monitor.fare.FareProviderException;
import com.siwol025.flight_monitor.fare.FareProviderUnavailableException;
import com.siwol025.flight_monitor.fare.FareRateLimitedException;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.FlightOfferPriceChangedException;
import com.siwol025.flight_monitor.fare.FlightOfferUnavailableException;
import com.siwol025.flight_monitor.fare.FlightOrderPlacer;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PlacedOrder;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Duffel offer 검색·단건 조회·주문으로 예매 흐름의 포트를 구현하는 어댑터. Duffel 형식 변환이 이 패키지 밖으로 새지 않는다.
 */
class DuffelFlightBookingClient implements FlightOfferFinder, FlightOrderPlacer {

    private static final String OFFER_NO_LONGER_AVAILABLE = "offer_no_longer_available";
    private static final String PRICE_CHANGED = "price_changed";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final DuffelProperties properties;
    private final Clock clock;

    DuffelFlightBookingClient(RestClient restClient, DuffelProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<FlightOffer> searchOffers(RouteKey routeKey) {
        OfferRequestResponse response = withoutResponse(() -> restClient.post()
                .uri(uri -> uri.path("/air/offer_requests")
                        .queryParam("return_offers", true)
                        .queryParam("supplier_timeout", properties.getSupplierTimeout().toMillis())
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(DuffelLowestFareFinder.toRequest(routeKey))
                .retrieve()
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw rateLimited(res.getHeaders());
                })
                .onStatus(status -> status.value() == 422, (req, res) -> {
                    throw new InvalidRouteException("Duffel이 노선을 거부함(422): " + DuffelLowestFareFinder.bodyOf(res));
                })
                // onStatus는 등록 순서대로 검사하므로 429·422가 먼저 걸러진다
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw otherError(res);
                })
                .body(OfferRequestResponse.class));
        // 금액 표기 자릿수가 달라도 같은 값으로 보도록 compareTo로 정렬한다. 동률이면 응답 순서
        return response.data().offers().stream()
                .sorted(Comparator.comparing(OfferRequestResponse.Offer::totalAmount))
                .map(DuffelFlightBookingClient::toFlightOffer)
                .toList();
    }

    @Override
    public Optional<FlightOffer> findOffer(String offerId) {
        // 매진·없음은 오류가 아니라 빈 결과라서 retrieve()의 예외 대신 상태를 직접 본다
        return withoutResponse(() -> restClient.get()
                .uri("/air/offers/{id}", offerId)
                .exchange((req, res) -> {
                    if (res.getStatusCode().value() == 429) {
                        throw rateLimited(res.getHeaders());
                    }
                    if (res.getStatusCode().value() == 404) {
                        return Optional.empty();
                    }
                    if (res.getStatusCode().value() == 422) {
                        String body = DuffelLowestFareFinder.bodyOf(res);
                        if (OFFER_NO_LONGER_AVAILABLE.equals(errorCode(body))) {
                            return Optional.empty();
                        }
                        throw unknownUnprocessable(body);
                    }
                    if (res.getStatusCode().isError()) {
                        throw otherError(res);
                    }
                    return Optional.of(toFlightOffer(res.bodyTo(OfferResponse.class).data()));
                }));
    }

    @Override
    public PlacedOrder placeOrder(String offerId, BigDecimal amount, String currency, Passenger passenger) {
        OrderResponse.Data order = withoutResponse(() -> restClient.post()
                .uri("/air/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(toOrderRequest(offerId, amount, currency, passenger))
                .retrieve()
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw rateLimited(res.getHeaders());
                })
                .onStatus(status -> status.value() == 422, (req, res) -> {
                    String body = DuffelLowestFareFinder.bodyOf(res);
                    String code = errorCode(body);
                    if (OFFER_NO_LONGER_AVAILABLE.equals(code)) {
                        throw new FlightOfferUnavailableException("Duffel offer 매진(422): " + body);
                    }
                    if (PRICE_CHANGED.equals(code)) {
                        throw new FlightOfferPriceChangedException("Duffel offer 가격 변동(422): " + body);
                    }
                    throw unknownUnprocessable(body);
                })
                // 없는 offer도 사용자에게는 예매할 수 없는 항공권이다
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new FlightOfferUnavailableException("Duffel offer 없음(404): " + DuffelLowestFareFinder.bodyOf(res));
                })
                // onStatus는 등록 순서대로 검사하므로 429·422·404가 먼저 걸러진다
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw otherError(res);
                })
                .body(OrderResponse.class)).data();
        return new PlacedOrder(order.id(), order.bookingReference(), order.totalAmount(), order.totalCurrency());
    }

    // 결제 없이 잔액(balance)으로 즉시(instant) 주문한다. 금액은 받은 표기 그대로 보낸다(Duffel은 offer 총액과 같아야 함)
    private static OrderRequest toOrderRequest(String offerId, BigDecimal amount, String currency, Passenger passenger) {
        OrderRequest.Passenger duffelPassenger = new OrderRequest.Passenger(
                passenger.title().name().toLowerCase(Locale.ROOT), passenger.givenName(), passenger.familyName(),
                passenger.gender().name().toLowerCase(Locale.ROOT), passenger.bornOn().toString(), passenger.email(),
                passenger.phoneNumber());
        return new OrderRequest(new OrderRequest.Data("instant", List.of(offerId),
                List.of(new OrderRequest.Payment("balance", amount.toPlainString(), currency)),
                List.of(duffelPassenger)));
    }

    // 타임아웃·연결 실패: 응답 자체를 받지 못함. 재시도하지 않는다
    private static <T> T withoutResponse(Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            throw new FareProviderUnavailableException("Duffel 응답 없음(타임아웃·연결 실패)", e);
        }
    }

    // 감시 어댑터와 같은 규칙: 5xx는 업체 장애, 그 밖의 4xx(401 인증, 400 요청 형식, 403 권한)는 우리 설정·요청 문제
    private static FareProviderException otherError(ClientHttpResponse res) throws IOException {
        int status = res.getStatusCode().value();
        String body = DuffelLowestFareFinder.bodyOf(res);
        if (res.getStatusCode().is5xxServerError()) {
            return new FareProviderUnavailableException("Duffel 업체 장애(" + status + "): " + body);
        }
        return new FareProviderConfigurationException("Duffel 요청이 거부됨(" + status + "): " + body);
    }

    // 업체 한도는 API 키 단위라 감시와 같은 예외로 알린다(재개 시각 해석은 감시 어댑터와 같다)
    private FareRateLimitedException rateLimited(HttpHeaders headers) {
        return new FareRateLimitedException(DuffelLowestFareFinder.resetAt(headers, clock),
                "Duffel 호출 한도 초과: ratelimit-reset=" + headers.getFirst("ratelimit-reset"));
    }

    // 422 본문의 errors[0].code. 없거나 본문을 해석할 수 없으면 null
    private static String errorCode(String body) {
        try {
            DuffelErrorResponse error = OBJECT_MAPPER.readValue(body, DuffelErrorResponse.class);
            return error.errors() == null || error.errors().isEmpty() ? null : error.errors().getFirst().code();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // 모르는 422는 우리 요청이 업체 규칙과 맞지 않는 것이다(그 밖의 4xx와 같은 취급)
    private static FareProviderConfigurationException unknownUnprocessable(String body) {
        return new FareProviderConfigurationException("Duffel이 요청을 처리하지 못함(422): " + body);
    }

    private static FlightOffer toFlightOffer(OfferRequestResponse.Offer offer) {
        List<OfferRequestResponse.Segment> segments = offer.slices().getFirst().segments();
        OfferRequestResponse.Segment first = segments.getFirst();
        OfferRequestResponse.Segment last = segments.getLast();
        return new FlightOffer(offer.id(), offer.totalAmount(), offer.totalCurrency(), first.origin().iataCode(),
                last.destination().iataCode(), seatGrade(first), first.marketingCarrier().iataCode(),
                first.marketingCarrier().name(), first.marketingCarrierFlightNumber(), first.departingAt(),
                last.arrivingAt());
    }

    // 검색 때 등급을 지정하므로 정상 경로에서는 세 값만 온다(premium_economy 등은 우리 요청과 맞지 않는 응답)
    private static SeatGrade seatGrade(OfferRequestResponse.Segment segment) {
        String cabinClass = segment.passengers() == null || segment.passengers().isEmpty()
                ? null : segment.passengers().getFirst().cabinClass();
        return switch (cabinClass == null ? "" : cabinClass) {
            case "economy" -> SeatGrade.ECONOMY;
            case "business" -> SeatGrade.BUSINESS;
            case "first" -> SeatGrade.FIRST;
            default -> throw new FareProviderConfigurationException("Duffel 응답의 cabin_class를 해석할 수 없음: " + cabinClass);
        };
    }
}
