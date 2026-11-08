package com.siwol025.flight_monitor.fare.adapter.duffel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.fare.FlightOfferFinder;
import com.siwol025.flight_monitor.fare.FlightOrder;
import com.siwol025.flight_monitor.fare.FlightOrderGateway;
import com.siwol025.flight_monitor.fare.Gender;
import com.siwol025.flight_monitor.fare.HoldOrderCommand;
import com.siwol025.flight_monitor.fare.Passenger;
import com.siwol025.flight_monitor.fare.PassengerTitle;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 실제 Duffel 테스트 모드 API로 hold 주문·조회·결제를 확인하는 선택 실행 테스트(계획 17·17-1장).
 * DUFFEL_LIVE_TEST=true이고 DUFFEL_API_TOKEN이 테스트 모드 키(duffel_test_)일 때만 실행하므로 기본 ./gradlew test는 건너뛴다.
 * 노선은 ZZ(Duffel Airways) hold·결제·documents가 실측된 JFK→EWR이다. 만든 주문은 매 테스트 뒤 취소한다.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "DUFFEL_LIVE_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "DUFFEL_API_TOKEN", matches = "duffel_test_.+")
class DuffelLiveSmokeTest {

    private static final String BASE_URL = "https://api.duffel.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Passenger PASSENGER = new Passenger(PassengerTitle.MR, "Tony", "Stark", Gender.M,
            LocalDate.of(1980, 7, 24), "tony@example.com", "+442080160508");

    private final List<String> createdOrderIds = new ArrayList<>();
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setUp() {
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(DuffelHttpClientConfig.class)
                .withPropertyValues(
                        "fare-provider.duffel-booking.base-url=" + BASE_URL,
                        "fare-provider.duffel-booking.api-key=" + System.getenv("DUFFEL_API_TOKEN"),
                        "fare-provider.duffel.base-url=http://duffel.invalid");
    }

    // 결제한 hold도 테스트 모드에서는 취소된다(실측 s04_c2). 취소 실패는 남은 주문으로 드러나도록 출력한다
    @AfterEach
    void cancelCreatedOrders() {
        RestClient client = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + System.getenv("DUFFEL_API_TOKEN"))
                .defaultHeader("Duffel-Version", "v2")
                .build();
        int cancelled = 0;
        for (String orderId : createdOrderIds) {
            try {
                String quote = client.post().uri("/air/order_cancellations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("data", Map.of("order_id", orderId)))
                        .retrieve().body(String.class);
                String quoteId = OBJECT_MAPPER.readTree(quote).path("data").path("id").asText();
                client.post().uri("/air/order_cancellations/{id}/actions/confirm", quoteId)
                        .retrieve().toBodilessEntity();
                cancelled++;
            } catch (RuntimeException | JsonProcessingException e) {
                System.out.println("[live] 주문 취소 실패 order=" + orderId + " cause=" + e.getMessage());
            }
        }
        System.out.println("[live] 정리: 만든 주문 " + createdOrderIds.size() + "건, 취소 " + cancelled + "건");
        assertThat(cancelled).as("취소하지 못한 테스트 주문").isEqualTo(createdOrderIds.size());
    }

    @Test
    void live_JFK_EWR_hold결제_documents() {
        contextRunner.run(context -> {
            FlightOrderGateway gateway = context.getBean(FlightOrderGateway.class);
            String bookingId = "live-" + UUID.randomUUID();
            FlightOrder held = hold(context.getBean(FlightOfferFinder.class), gateway, bookingId);

            assertThat(held.paidAt()).isNull();
            assertThat(held.paymentRequiredBy()).isNotNull();
            FlightOrder beforePay = timed("hold 조회", () -> gateway.findOrder(held.orderId())).orElseThrow();
            assertThat(beforePay.paidAt()).isNull();

            timed("balance 결제", () -> {
                gateway.payHeldOrder(held.orderId(), beforePay.amount(), beforePay.currency(), bookingId);
                return null;
            });

            FlightOrder paid = paidWithDocuments(gateway, held.orderId());
            System.out.println("[live] 결제 후 paidAt=" + paid.paidAt() + " 항공권 " + paid.ticketNumbers().size()
                    + "건, PNR 있음=" + (paid.bookingReference() != null));
            assertThat(paid.paidAt()).isNotNull();
            assertThat(paid.ticketNumbers()).isNotEmpty();
        });
    }

    @Test
    void live_hold_metadata_booking_id반영() {
        contextRunner.run(context -> {
            FlightOrderGateway gateway = context.getBean(FlightOrderGateway.class);
            String bookingId = "live-" + UUID.randomUUID();
            FlightOrder held = hold(context.getBean(FlightOfferFinder.class), gateway, bookingId);

            FlightOrder found = timed("hold 조회", () -> gateway.findOrder(held.orderId())).orElseThrow();
            List<FlightOrder> byOffer = timed("offer_id 목록 조회", () -> gateway.findOrdersByOffer(held.offerId()));
            System.out.println("[live] metadata 반영: 생성응답=" + bookingId.equals(held.bookingId())
                    + " 조회=" + bookingId.equals(found.bookingId())
                    + " 목록=" + byOffer.stream().anyMatch(order -> bookingId.equals(order.bookingId())));

            assertThat(held.bookingId()).isEqualTo(bookingId);
            assertThat(found.bookingId()).isEqualTo(bookingId);
            assertThat(byOffer).extracting(FlightOrder::orderId).containsExactly(held.orderId());
            assertThat(byOffer).extracting(FlightOrder::bookingId).containsExactly(bookingId);
        });
    }

    // JFK→EWR(한 달 뒤)에서 ZZ hold 가능한 offer를 골라 hold 주문을 만든다. 만든 주문은 정리 대상에 올린다
    private FlightOrder hold(FlightOfferFinder finder, FlightOrderGateway gateway, String bookingId) {
        RouteKey route = new RouteKey("JFK", "EWR", LocalDate.now().plusDays(30), SeatGrade.ECONOMY);
        List<FlightOffer> offers = timed("검색", () -> finder.searchOffers(route));
        FlightOffer offer = offers.stream()
                .filter(candidate -> "ZZ".equals(candidate.terms().ownerCode()))
                .filter(candidate -> !candidate.terms().requiresInstantPayment())
                .findFirst()
                .orElseThrow(() -> new AssertionError("JFK→EWR에 hold 가능한 ZZ offer 없음(전체 " + offers.size() + "건)"));
        FlightOrder held = timed("hold 주문", () -> gateway.createHoldOrder(new HoldOrderCommand(offer.offerId(),
                offer.terms().passengerIds().getFirst(), PASSENGER, bookingId)));
        createdOrderIds.add(held.orderId());
        return held;
    }

    // 결제 직후 documents가 늦게 붙는 경우(계획 위험 U2)를 대비해 잠시 다시 읽는다
    private static FlightOrder paidWithDocuments(FlightOrderGateway gateway, String orderId) throws InterruptedException {
        FlightOrder order = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            order = timed("결제 후 조회 " + attempt, () -> gateway.findOrder(orderId)).orElseThrow();
            if (order.paidAt() != null && !order.ticketNumbers().isEmpty()) {
                return order;
            }
            Thread.sleep(2000);
        }
        return order;
    }

    private static <T> T timed(String step, Supplier<T> call) {
        long started = System.nanoTime();
        try {
            return call.get();
        } finally {
            System.out.println("[live] " + step + " " + (System.nanoTime() - started) / 1_000_000 + "ms");
        }
    }
}
