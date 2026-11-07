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
import com.siwol025.flight_monitor.fare.OfferTerms;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Duffel offer 검색·단건 조회·주문으로 예매 흐름의 포트를 구현하는 어댑터. Duffel 형식 변환이 이 패키지 밖으로 새지 않는다.
 */
@Slf4j
class DuffelFlightBookingClient implements FlightOfferFinder, FlightOrderPlacer {

    private static final String OFFER_NO_LONGER_AVAILABLE = "offer_no_longer_available";
    private static final String PRICE_CHANGED = "price_changed";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final DuffelBookingProperties properties;
    private final Clock clock;

    DuffelFlightBookingClient(RestClient restClient, DuffelBookingProperties properties, Clock clock) {
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
                    throw rateLimited(res.getHeaders(), readError(req, res));
                })
                .onStatus(status -> status.value() == 422, (req, res) -> {
                    DuffelError error = readError(req, res);
                    throw new InvalidRouteException("Duffel이 노선을 거부함(" + error + "): " + error.body());
                })
                // onStatus는 등록 순서대로 검사하므로 429·422가 먼저 걸러진다
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw otherError(readError(req, res));
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
                    if (!res.getStatusCode().isError()) {
                        return Optional.of(toFlightOffer(res.bodyTo(OfferResponse.class).data()));
                    }
                    DuffelError error = readError(req, res);
                    if (error.status() == 429) {
                        throw rateLimited(res.getHeaders(), error);
                    }
                    if (error.status() == 404) {
                        return Optional.empty();
                    }
                    if (error.status() == 422) {
                        if (OFFER_NO_LONGER_AVAILABLE.equals(error.code())) {
                            return Optional.empty();
                        }
                        throw unknownUnprocessable(error);
                    }
                    throw otherError(error);
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
                    throw rateLimited(res.getHeaders(), readError(req, res));
                })
                .onStatus(status -> status.value() == 422, (req, res) -> {
                    DuffelError error = readError(req, res);
                    if (OFFER_NO_LONGER_AVAILABLE.equals(error.code())) {
                        throw new FlightOfferUnavailableException("Duffel offer 매진(" + error + "): " + error.body());
                    }
                    if (PRICE_CHANGED.equals(error.code())) {
                        throw new FlightOfferPriceChangedException("Duffel offer 가격 변동(" + error + "): " + error.body());
                    }
                    throw unknownUnprocessable(error);
                })
                // 없는 offer도 사용자에게는 예매할 수 없는 항공권이다
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    DuffelError error = readError(req, res);
                    throw new FlightOfferUnavailableException("Duffel offer 없음(" + error + "): " + error.body());
                })
                // onStatus는 등록 순서대로 검사하므로 429·422·404가 먼저 걸러진다
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw otherError(readError(req, res));
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
    private static FareProviderException otherError(DuffelError error) {
        if (error.status() >= 500) {
            return new FareProviderUnavailableException("Duffel 업체 장애(" + error + "): " + error.body());
        }
        return new FareProviderConfigurationException("Duffel 요청이 거부됨(" + error + "): " + error.body());
    }

    // 업체 한도는 API 키 단위라 감시와 같은 예외로 알린다(재개 시각 해석은 감시 어댑터와 같다)
    private FareRateLimitedException rateLimited(HttpHeaders headers, DuffelError error) {
        return new FareRateLimitedException(DuffelLowestFareFinder.resetAt(headers, clock),
                "Duffel 호출 한도 초과(" + error + "): ratelimit-reset=" + headers.getFirst("ratelimit-reset"));
    }

    // 모르는 422는 우리 요청이 업체 규칙과 맞지 않는 것이다(그 밖의 4xx와 같은 취급)
    private static FareProviderConfigurationException unknownUnprocessable(DuffelError error) {
        return new FareProviderConfigurationException("Duffel이 요청을 처리하지 못함(" + error + "): " + error.body());
    }

    /**
     * 비 2xx 응답의 원인. 업체 문의에 쓰는 요청 ID는 본문 meta.request_id, 본문이 JSON이 아니면 x-request-id 헤더다.
     * 오류 본문은 응답마다 한 번만 읽을 수 있으므로 읽은 원문도 함께 두고, 예외 메시지에 원인과 원문을 함께 싣는다.
     */
    private record DuffelError(int status, String type, String code, String requestId, String body) {

        @Override
        public String toString() {
            return "status=" + status + ", type=" + type + ", code=" + code + ", requestId=" + requestId;
        }
    }

    // 예매 경로의 업체 오류는 사용자에게 503·409로만 보이므로 원인(code·요청 ID)을 여기서 WARN으로 남긴다
    private static DuffelError readError(HttpRequest request, ClientHttpResponse response) throws IOException {
        String body = DuffelLowestFareFinder.bodyOf(response);
        DuffelErrorResponse parsed = parse(body);
        DuffelErrorResponse.Error first = parsed == null || parsed.errors() == null || parsed.errors().isEmpty()
                ? null : parsed.errors().getFirst();
        String requestId = parsed != null && parsed.meta() != null && parsed.meta().requestId() != null
                ? parsed.meta().requestId() : response.getHeaders().getFirst("x-request-id");
        DuffelError error = new DuffelError(response.getStatusCode().value(), first == null ? null : first.type(),
                first == null ? null : first.code(), requestId, body);
        log.warn("[DuffelFlightBookingClient] Duffel 비 2xx 응답: {} {} status={} type={} code={} requestId={}",
                request.getMethod(), request.getURI().getPath(), error.status(), error.type(), error.code(),
                error.requestId());
        return error;
    }

    // 본문을 해석할 수 없으면(프록시의 HTML 등) null
    private static DuffelErrorResponse parse(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, DuffelErrorResponse.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static FlightOffer toFlightOffer(OfferRequestResponse.Offer offer) {
        List<OfferRequestResponse.Segment> segments = offer.slices().getFirst().segments();
        OfferRequestResponse.Segment first = segments.getFirst();
        OfferRequestResponse.Segment last = segments.getLast();
        return new FlightOffer(offer.id(), offer.totalAmount(), offer.totalCurrency(), first.origin().iataCode(),
                last.destination().iataCode(), seatGrade(first), first.marketingCarrier().iataCode(),
                first.marketingCarrier().name(), first.marketingCarrierFlightNumber(), first.departingAt(),
                last.arrivingAt(), toTerms(offer));
    }

    // 업체가 주지 않은 조건(mock 응답 등)은 빈 목록·false·null로 둔다
    private static OfferTerms toTerms(OfferRequestResponse.Offer offer) {
        List<String> passengerIds = offer.passengers() == null ? List.of()
                : offer.passengers().stream().map(OfferRequestResponse.OfferPassenger::id).toList();
        boolean requiresInstantPayment = offer.paymentRequirements() != null
                && offer.paymentRequirements().requiresInstantPayment();
        String ownerCode = offer.owner() == null ? null : offer.owner().iataCode();
        return new OfferTerms(passengerIds, requiresInstantPayment, offer.expiresAt(), ownerCode);
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
