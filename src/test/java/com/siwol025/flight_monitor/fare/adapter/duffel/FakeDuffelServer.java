package com.siwol025.flight_monitor.fare.adapter.duffel;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * Duffel API를 흉내 내는 테스트용 HTTP 서버(JDK 내장 HttpServer, 실제 소켓).
 * 받은 요청을 모두 기록하고, 지정한 상태·헤더·본문을 지정한 지연 뒤에 돌려준다.
 * 메서드·경로별로 쌓아 둔 응답({@link #enqueue})이 있으면 순서대로 먼저 쓰고, 없으면 기본 응답({@link #respond})을 쓴다.
 */
class FakeDuffelServer implements AutoCloseable {

    record RecordedRequest(String method, String path, String query, Headers headers, String body) {
    }

    private record Response(int status, Map<String, String> headers, byte[] body, boolean gzip, Duration delay) {
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private volatile int status = 201;
    private volatile Map<String, String> headers = Map.of();
    private volatile byte[] body = "{\"data\":{\"offers\":[]}}".getBytes(UTF_8);
    private volatile boolean gzip;
    private volatile Duration delay = Duration.ZERO;
    private volatile RecordedRequest lastRequest;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Queue<Response>> queued = new ConcurrentHashMap<>();

    FakeDuffelServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respond(int status, Map<String, String> headers, String body) {
        this.status = status;
        this.headers = headers;
        this.body = body.getBytes(UTF_8);
        this.gzip = false;
    }

    /** 실제 Duffel처럼 본문을 gzip으로 압축해 Content-Encoding: gzip으로 돌려준다. */
    void respondGzip(int status, Map<String, String> headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
        this.gzip = true;
    }

    void delay(Duration delay) {
        this.delay = delay;
    }

    /** 이 메서드·경로의 다음 요청 하나에 실제 Duffel처럼 gzip 본문을 지연 뒤에 돌려준다. 여러 번 부르면 순서대로 쓴다 */
    void enqueue(String method, String path, int status, byte[] body, Duration delay) {
        queued.computeIfAbsent(method + " " + path, key -> new ConcurrentLinkedQueue<>())
                .add(new Response(status, Map.of(), body, true, delay));
    }

    RecordedRequest lastRequest() {
        return lastRequest;
    }

    List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
            lastRequest = new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders(), requestBody);
            requests.add(lastRequest);
            Queue<Response> queue = queued.get(lastRequest.method() + " " + lastRequest.path());
            Response queuedResponse = queue == null ? null : queue.poll();
            Response response = queuedResponse != null ? queuedResponse
                    : new Response(status, headers, body, gzip, delay);
            Thread.sleep(response.delay().toMillis());

            byte[] out = response.gzip() ? gzip(response.body()) : response.body();
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            if (response.gzip()) {
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            }
            exchange.sendResponseHeaders(response.status(), out.length);
            exchange.getResponseBody().write(out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(buffer)) {
            out.write(raw);
        }
        return buffer.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
