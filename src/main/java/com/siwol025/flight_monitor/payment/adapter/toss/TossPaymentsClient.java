package com.siwol025.flight_monitor.payment.adapter.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.payment.PaymentFailureKind;
import com.siwol025.flight_monitor.payment.PaymentGateway;
import com.siwol025.flight_monitor.payment.PaymentGatewayException;
import com.siwol025.flight_monitor.payment.PaymentSnapshot;
import com.siwol025.flight_monitor.payment.PaymentStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 토스페이먼츠 승인·조회로 {@link PaymentGateway}를 구현하는 어댑터. Toss 형식 변환이 이 패키지 밖으로 새지 않는다.
 * 재요청하지 않는다(HTTP 클라이언트 자동 재시도도 꺼져 있다). 시크릿 키·인증 헤더는 로그·예외에 싣지 않는다.
 */
@Slf4j
class TossPaymentsClient implements PaymentGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TRACE_ID = "x-tosspayments-trace-id";

    private final RestClient restClient;

    TossPaymentsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PaymentSnapshot confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        // 빈 키는 Toss가 거절하지 않아 다른 요청의 저장 응답을 받을 수 있다
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Toss 승인에는 빈 멱등키를 보내지 않는다");
        }
        return call(() -> restClient.post()
                .uri("/v1/payments/confirm")
                // Content-Type이 없으면 Toss는 400이 아니라 500 COMMON_ERROR를 준다
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(new TossConfirmRequest(paymentKey, orderId, amount))
                .exchange(this::toSnapshot));
    }

    @Override
    public PaymentSnapshot find(String paymentKey) {
        return call(() -> restClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .exchange(this::toSnapshot));
    }

    // 같은 키 재요청은 첫 응답을 재생하며 trace-id가 두 개(새 값, 원래 값) 온다. 사후에 재생 여부를 알 수 있게 모두 남긴다
    private PaymentSnapshot toSnapshot(HttpRequest request, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().isError()) {
            throw failure(request, response);
        }
        TossPaymentResponse payment = response.bodyTo(TossPaymentResponse.class);
        log.info("[TossPaymentsClient] Toss 응답: {} {} status={} paymentStatus={} traceId={}", request.getMethod(),
                request.getURI().getPath(), response.getStatusCode().value(), payment.status(), traceIds(response));
        return new PaymentSnapshot(payment.paymentKey(), payment.orderId(), PaymentStatus.valueOf(payment.status()),
                payment.totalAmount(), payment.approvedAt() == null ? null : payment.approvedAt().toInstant());
    }

    // 응답을 받지 못한 호출. 연결 실패여도 승인 여부를 모르는 것으로 둔다(호출한 쪽은 조회부터 한다)
    private static PaymentSnapshot call(Supplier<PaymentSnapshot> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayException(PaymentFailureKind.UNKNOWN, "Toss 응답 없음(타임아웃·연결 실패)", e);
        }
    }

    /**
     * 비 2xx 응답. 오류 본문은 실측 3종({code,message}, 빈 본문, message가 객체인 본문)이라 트리로 읽고,
     * code·message가 문자열이 아니면 null로 두어 상태 코드로 분류한다.
     */
    private static PaymentGatewayException failure(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        JsonNode body = parse(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
        String code = textOf(body, "code");
        String message = textOf(body, "message");
        String traceId = traceIds(response);
        PaymentFailureKind kind = TossErrorClassifier.classify(status, code);
        log.warn("[TossPaymentsClient] Toss 비 2xx 응답: {} {} status={} code={} kind={} traceId={}",
                request.getMethod(), request.getURI().getPath(), status, code, kind, traceId);
        return new PaymentGatewayException(kind, code, message,
                "Toss 승인·조회 실패(status=" + status + ", code=" + code + ", traceId=" + traceId + "): " + message);
    }

    private static String traceIds(ClientHttpResponse response) {
        List<String> values = response.getHeaders().get(TRACE_ID);
        return values == null ? null : String.join(", ", values);
    }

    private static JsonNode parse(String body) {
        if (body.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String textOf(JsonNode body, String field) {
        JsonNode node = body == null ? null : body.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }
}
