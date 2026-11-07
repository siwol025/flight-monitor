package com.siwol025.flight_monitor.payment.adapter.toss;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 토스페이먼츠 API를 흉내 내는 테스트용 HTTP 서버(JDK 내장 HttpServer, 실제 소켓). 받은 요청을 모두 기록한다.
 *
 * <p>승인({@code POST /v1/payments/confirm})은 실측 멱등 의미론을 따른다(1단계 6장, 2단계 A·G·I).</p>
 * <ul>
 *     <li>같은 Idempotency-Key는 첫 응답(성공·4xx·5xx 모두)을 재생한다. 재생 응답은 trace-id가 두 개(새 값, 원래 값)다</li>
 *     <li>같은 키의 첫 요청이 아직 처리 중이면 409 {@code IDEMPOTENT_REQUEST_PROCESSING}(이 응답은 저장하지 않는다)</li>
 *     <li>이미 승인된 paymentKey를 다른 키·키 없이 승인하면 400 {@code ALREADY_PROCESSED_PAYMENT}</li>
 *     <li>같은 paymentKey를 다른 키로 처리하는 중에 또 승인하면 400 {@code ALREADY_PROCESSING_REQUEST}(그 키는 이 에러를
 *     계속 재생한다, 2단계 I_01~I_05)</li>
 * </ul>
 * 새로 처리하는 승인은 쌓아 둔 응답({@link #enqueue})을 순서대로 먼저 쓰고, 없으면 승인 성공 본문({@link #confirmSucceeds})을 쓴다.
 * 조회 등 그 밖의 요청은 쌓아 둔 응답을 쓰고, 없으면 404 {@code NOT_FOUND_PAYMENT}다.
 */
public class FakeTossServer implements AutoCloseable {

    public static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public record RecordedRequest(String method, String path, Headers headers, String body) {
    }

    private record Response(int status, String body, Duration delay, String traceId) {
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Queue<Response>> queued = new ConcurrentHashMap<>();
    /** 멱등키별 첫 응답. 처리 중이면 값이 PROCESSING이다 */
    private final Map<String, Response> idempotent = new ConcurrentHashMap<>();
    private final Set<String> approvedPaymentKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> processingPaymentKeys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger approvedCount = new AtomicInteger();
    /** reset마다 바뀐다. 이전 테스트에서 아직 처리 중이던 승인이 끝나도 지금 상태에 남기지 않는다 */
    private final AtomicInteger generation = new AtomicInteger();
    private static final Response PROCESSING = new Response(0, null, Duration.ZERO, null);

    private volatile String confirmSuccessBody = "{}";
    private volatile Duration confirmDelay = Duration.ZERO;

    public FakeTossServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 새로 처리하는 승인이 쌓아 둔 응답 없이 성공할 때 돌려줄 본문(200) */
    public void confirmSucceeds(String body) {
        this.confirmSuccessBody = body;
    }

    /** 새로 처리하는 승인마다 응답 전에 기다리는 시간(처리 중 409·읽기 타임아웃 재현) */
    public void confirmDelay(Duration delay) {
        this.confirmDelay = delay;
    }

    /** 이 메서드·경로의 다음 요청 하나(승인은 새로 처리하는 요청 하나)에 줄 응답. 본문이 빈 문자열이면 빈 본문이다 */
    public void enqueue(String method, String path, int status, String body) {
        enqueue(method, path, status, body, Duration.ZERO);
    }

    public void enqueue(String method, String path, int status, String body, Duration delay) {
        queued.computeIfAbsent(method + " " + path, key -> new ConcurrentLinkedQueue<>())
                .add(new Response(status, body, delay, null));
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public RecordedRequest lastRequest() {
        return requests.getLast();
    }

    /** 테스트 사이에 기록·쌓아 둔 응답·멱등 저장소·승인 상태를 비운다 */
    public void reset() {
        generation.incrementAndGet();
        requests.clear();
        queued.clear();
        idempotent.clear();
        approvedPaymentKeys.clear();
        processingPaymentKeys.clear();
        approvedCount.set(0);
        confirmSuccessBody = "{}";
        confirmDelay = Duration.ZERO;
    }

    /** 이 메서드·경로로 받은 요청 수 */
    public long count(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).count();
    }

    /** 실제로 승인(200)을 처리한 횟수. 같은 키의 재생은 세지 않는다 */
    public int approvedCount() {
        return approvedCount.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            RecordedRequest request = new RecordedRequest(exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(), exchange.getRequestHeaders(),
                    new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            requests.add(request);
            String traceId = UUID.randomUUID().toString().replace("-", "");
            if ("POST".equals(request.method()) && CONFIRM_PATH.equals(request.path())) {
                confirm(exchange, request, traceId);
            } else {
                Response response = poll(request.method() + " " + request.path());
                send(exchange, response != null ? response
                        : new Response(404, "{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"존재하지 않는 결제 정보 입니다.\"}",
                        Duration.ZERO, null), List.of(traceId));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void confirm(HttpExchange exchange, RecordedRequest request, String traceId)
            throws IOException, InterruptedException {
        String key = request.headers().getFirst("Idempotency-Key");
        if (key != null) {
            Response previous = idempotent.putIfAbsent(key, PROCESSING);
            if (previous == PROCESSING) {
                send(exchange, new Response(409,
                        "{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\",\"message\":\"이전 멱등 요청이 처리중입니다.\"}",
                        Duration.ZERO, null), List.of(traceId));
                return;
            }
            if (previous != null) {
                send(exchange, previous, List.of(traceId, previous.traceId()));
                return;
            }
        }
        int started = generation.get();
        Response response = process(request, traceId, started);
        if (key != null && started == generation.get()) {
            idempotent.put(key, response);
        }
        send(exchange, response, List.of(traceId));
    }

    // 멱등 저장소에 없는 승인을 새로 처리한다. 응답 지연은 처리 중 상태로 보낸다
    private Response process(RecordedRequest request, String traceId, int started)
            throws IOException, InterruptedException {
        String paymentKey = paymentKeyOf(request.body());
        if (paymentKey != null && approvedPaymentKeys.contains(paymentKey)) {
            return new Response(400, "{\"code\":\"ALREADY_PROCESSED_PAYMENT\",\"message\":\"이미 처리된 결제 입니다.\"}",
                    Duration.ZERO, traceId);
        }
        if (paymentKey != null && !processingPaymentKeys.add(paymentKey)) {
            return new Response(400, "{\"code\":\"ALREADY_PROCESSING_REQUEST\",\"message\":\"이미 처리중인 요청입니다.\"}",
                    Duration.ZERO, traceId);
        }
        try {
            Response scripted = poll("POST " + CONFIRM_PATH);
            Response response = scripted != null ? new Response(scripted.status(), scripted.body(), scripted.delay(), traceId)
                    : new Response(200, confirmSuccessBody, confirmDelay, traceId);
            Thread.sleep(response.delay().toMillis());
            if (response.status() == 200 && started == generation.get()) {
                approvedCount.incrementAndGet();
                if (paymentKey != null) {
                    approvedPaymentKeys.add(paymentKey);
                }
            }
            return response;
        } finally {
            if (paymentKey != null && started == generation.get()) {
                processingPaymentKeys.remove(paymentKey);
            }
        }
    }

    private Response poll(String route) {
        Queue<Response> queue = queued.get(route);
        return queue == null ? null : queue.poll();
    }

    private static String paymentKeyOf(String body) throws IOException {
        if (body.isBlank()) {
            return null;
        }
        JsonNode node = OBJECT_MAPPER.readTree(body).get("paymentKey");
        return node == null ? null : node.asText();
    }

    private static void send(HttpExchange exchange, Response response, List<String> traceIds) throws IOException {
        traceIds.forEach(id -> exchange.getResponseHeaders().add("x-tosspayments-trace-id", id));
        byte[] out = response.body().getBytes(UTF_8);
        if (out.length == 0) {
            // 게이트웨이 거절처럼 content-type 없는 빈 본문
            exchange.sendResponseHeaders(response.status(), -1);
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), out.length);
        exchange.getResponseBody().write(out);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
