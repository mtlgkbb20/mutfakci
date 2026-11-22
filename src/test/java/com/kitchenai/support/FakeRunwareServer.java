package com.kitchenai.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.mock.env.MockEnvironment;

/** Local HTTP stand-in for the Runware API: queues canned responses and records requests. */
public final class FakeRunwareServer implements AutoCloseable {
  public record Recorded(String method, String path, String authorization, String body) {}
  private record Canned(int status, String body) {}

  private final HttpServer server;
  private final Deque<Canned> responses = new ArrayDeque<>();
  private final List<Recorded> requests = new ArrayList<>();

  public FakeRunwareServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      synchronized (this) {
        requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"), body));
      }
      Canned canned;
      synchronized (this) { canned = responses.isEmpty() ? new Canned(500, "{\"errors\":[{\"code\":\"no_stub\"}]}") : responses.poll(); }
      byte[] bytes = canned.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(canned.status(), bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
  }

  public synchronized FakeRunwareServer respond(int status, String body) {
    responses.add(new Canned(status, body));
    return this;
  }

  /** Runware task-API shape: {"data":[{"text": "..."}]}. */
  public FakeRunwareServer respondText(String text) {
    return respond(200, "{\"data\":[{\"text\":" + quote(text) + "}]}");
  }

  /** OpenAI-compatible chat completions shape. */
  public FakeRunwareServer respondChat(String content) {
    return respond(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quote(content) + "}}]}");
  }

  public synchronized List<Recorded> requests() { return List.copyOf(requests); }

  public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"; }

  public MockEnvironment environment() {
    return new MockEnvironment().withProperty("app.ai.runware.base-url", baseUrl())
        .withProperty("app.ai.runware.api-key", "test-key");
  }

  public static String quote(String value) {
    try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  @Override public void close() { server.stop(0); }
}
