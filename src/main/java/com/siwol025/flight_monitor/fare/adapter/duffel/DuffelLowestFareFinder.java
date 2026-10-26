package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.siwol025.flight_monitor.fare.FareProviderConfigurationException;
import com.siwol025.flight_monitor.fare.FareProviderUnavailableException;
import com.siwol025.flight_monitor.fare.FareRateLimitedException;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.fare.LowestFare;
import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Duffel 오퍼 검색으로 노선 최저가를 찾는 어댑터. Duffel 형식 변환과 인증이 이 패키지 밖으로 새지 않는다.
 */
@Slf4j
class DuffelLowestFareFinder implements LowestFareFinder {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final DuffelProperties properties;
    private final Clock clock;

    DuffelLowestFareFinder(RestClient restClient, DuffelProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<LowestFare> findLowestFare(RouteKey routeKey) {
        ResponseEntity<OfferRequestResponse> response;
        try {
            response = requestOffers(routeKey);
        } catch (ResourceAccessException e) {
            // 타임아웃·연결 실패: 응답 자체를 받지 못함
            throw new FareProviderUnavailableException("Duffel 응답 없음(타임아웃·연결 실패)", e);
        }
        warnIfRateLimitDiffers(response.getHeaders());
        // 금액 표기 자릿수가 달라도 같은 값으로 보도록 compareTo로 비교한다. 동률이면 응답 순서상 앞의 것
        return response.getBody().data().offers().stream()
                .min(Comparator.comparing(OfferRequestResponse.Offer::totalAmount))
                .map(DuffelLowestFareFinder::toLowestFare);
    }

    // 업체 한도가 바뀌면 앱 호출 한도(업체 한도의 90%)의 산정 근거가 틀어진다
    private void warnIfRateLimitDiffers(HttpHeaders headers) {
        String limit = headers.getFirst("ratelimit-limit");
        if (limit != null && !limit.equals(String.valueOf(properties.getExpectedRateLimit()))) {
            log.warn("[DuffelLowestFareFinder] 응답 ratelimit-limit({})이 설정 expected-rate-limit({})과 다릅니다."
                    + " 앱 호출 한도를 다시 산정하세요", limit, properties.getExpectedRateLimit());
        }
    }

    private ResponseEntity<OfferRequestResponse> requestOffers(RouteKey routeKey) {
        return restClient.post()
                .uri(uri -> uri.path("/air/offer_requests")
                        .queryParam("return_offers", true)
                        .queryParam("supplier_timeout", properties.getSupplierTimeout().toMillis())
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(routeKey))
                .retrieve()
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw new FareRateLimitedException(resetAt(res.getHeaders()),
                            "Duffel 호출 한도 초과: ratelimit-reset=" + res.getHeaders().getFirst("ratelimit-reset"));
                })
                .onStatus(status -> status.value() == 422, (req, res) -> {
                    throw new InvalidRouteException("Duffel이 노선을 거부함(422): " + bodyOf(res));
                })
                // 그 밖의 4xx(401 인증, 400 요청 형식, 403·404 권한·경로)는 노선이 아니라 우리 설정·요청의 문제다.
                // onStatus는 등록 순서대로 검사하므로 429·422가 먼저 걸러진다
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new FareProviderConfigurationException(
                            "Duffel 요청이 거부됨(" + res.getStatusCode().value() + "): " + bodyOf(res));
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new FareProviderUnavailableException(
                            "Duffel 업체 장애(" + res.getStatusCode().value() + "): " + bodyOf(res));
                })
                .toEntity(OfferRequestResponse.class);
    }

    // 오류 본문(errors[]·meta.request_id)은 원인 추적용으로 예외 메시지에 그대로 싣는다
    private static String bodyOf(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ratelimit-reset은 RFC 2616 HTTP 날짜(예: Sun, 25 Oct 2026 16:08:00 GMT). 없거나 해석할 수 없으면
    // 문서의 한도 기간(60초) 뒤로 잡는다 — 전체 정지는 재개 시각이 비지 않는다는 전제 위에 있다
    private Instant resetAt(HttpHeaders headers) {
        String reset = headers.getFirst("ratelimit-reset");
        if (reset != null) {
            try {
                return ZonedDateTime.parse(reset, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException ignored) {
                // 아래 기본값으로 진행
            }
        }
        return clock.instant().plus(RATE_LIMIT_WINDOW);
    }

    // 항공사·편명·출발은 첫 구간, 도착은 마지막 구간(경유편이면 최종 도착)
    private static LowestFare toLowestFare(OfferRequestResponse.Offer offer) {
        List<OfferRequestResponse.Segment> segments = offer.slices().getFirst().segments();
        OfferRequestResponse.Segment first = segments.getFirst();
        return new LowestFare(offer.totalAmount(), offer.totalCurrency(), first.marketingCarrier().iataCode(),
                first.marketingCarrier().name(), first.marketingCarrierFlightNumber(), first.departingAt(),
                segments.getLast().arrivingAt());
    }

    private static OfferRequest toRequest(RouteKey routeKey) {
        OfferRequest.Slice slice = new OfferRequest.Slice(routeKey.departureAirport(), routeKey.arrivalAirport(),
                routeKey.departureDate().toString());
        return new OfferRequest(new OfferRequest.Data(List.of(slice), List.of(new OfferRequest.Passenger("adult")),
                cabinClass(routeKey.seatGrade())));
    }

    private static String cabinClass(SeatGrade seatGrade) {
        return switch (seatGrade) {
            case ECONOMY -> "economy";
            case BUSINESS -> "business";
            case FIRST -> "first";
        };
    }
}
