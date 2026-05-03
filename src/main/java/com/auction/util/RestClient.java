package com.auction.util;

import com.auction.ui.util.SceneManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility client HTTP dùng cho client JavaFX giao tiếp với server Javalin.
 *
 * <p><b>Mục đích:</b>
 * Cung cấp các phương thức gửi HTTP request (GET, POST, PUT, DELETE) đến REST API
 * của server, tự động gắn JWT token vào header {@code Authorization: Bearer <token>}
 * nếu người dùng đã đăng nhập (lấy từ {@link SceneManager}).
 *
 * <p><b>Các phương thức chính:</b>
 * <ul>
 *   <li>{@link #get(String)} — Gửi GET request, trả về JSON string.</li>
 *   <li>{@link #post(String, Object)} — Gửi POST request với body JSON.</li>
 *   <li>{@link #put(String, Object)} — Gửi PUT request với body JSON.</li>
 *   <li>{@link #delete(String)} — Gửi DELETE request.</li>
 * </ul>
 *
 * <p><b>Vị trí trong kiến trúc:</b>
 * Tất cả UI Controller đều gọi RestClient để tương tác với server.
 * RestClient là cầu nối giữa tầng UI (JavaFX) và tầng API (Javalin REST).
 *
 * <p><b>Ví dụ sử dụng:</b>
 * <pre>
 *   HttpResponse&lt;String&gt; resp = RestClient.get("/api/auctions");
 *   if (resp.statusCode() == 200) {
 *     List&lt;AuctionResponse&gt; list = RestClient.parseList(resp.body(), AuctionResponse.class);
 *   }
 * </pre>
 */
public class RestClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestClient.class);

  /** URL gốc của server — khớp với SERVER_PORT trong App.java */
  private static final String BASE_URL = "http://localhost:8080";

  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private RestClient() {}

  // ========== PUBLIC API ==========

  /**
   * Gửi GET request đến {@code BASE_URL + path}.
   *
   * @param path đường dẫn API (ví dụ: "/api/auctions")
   * @return HttpResponse chứa body JSON dạng String
   * @throws RuntimeException nếu gửi request thất bại
   */
  public static HttpResponse<String> get(String path) {
    HttpRequest request = buildRequest(path)
        .GET()
        .build();
    return send(request);
  }

  /**
   * Gửi POST request với body JSON serialize từ {@code body}.
   *
   * @param path  đường dẫn API
   * @param body  object sẽ được serialize thành JSON
   * @return HttpResponse chứa body JSON
   */
  public static HttpResponse<String> post(String path, Object body) {
    HttpRequest request = buildRequest(path)
        .POST(toBody(body))
        .build();
    return send(request);
  }

  /**
   * Gửi PUT request với body JSON serialize từ {@code body}.
   *
   * @param path  đường dẫn API
   * @param body  object sẽ được serialize thành JSON
   * @return HttpResponse chứa body JSON
   */
  public static HttpResponse<String> put(String path, Object body) {
    HttpRequest request = buildRequest(path)
        .PUT(toBody(body))
        .build();
    return send(request);
  }

  /**
   * Gửi DELETE request.
   *
   * @param path đường dẫn API
   * @return HttpResponse chứa body JSON
   */
  public static HttpResponse<String> delete(String path) {
    HttpRequest request = buildRequest(path)
        .DELETE()
        .build();
    return send(request);
  }

  /**
   * Parse JSON string thành object của kiểu {@code clazz}.
   *
   * @param json  JSON string cần parse
   * @param clazz kiểu đích
   * @return object đã parse
   */
  public static <T> T parse(String json, Class<T> clazz) {
    try {
      return MAPPER.readValue(json, clazz);
    } catch (Exception e) {
      throw new RuntimeException("Lỗi parse JSON: " + e.getMessage(), e);
    }
  }

  /**
   * Parse JSON string thành List của kiểu {@code clazz}.
   *
   * @param json  JSON array string
   * @param clazz kiểu phần tử trong list
   * @return List đã parse
   */
  public static <T> java.util.List<T> parseList(String json, Class<T> clazz) {
    try {
      var type = MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, clazz);
      return MAPPER.readValue(json, type);
    } catch (Exception e) {
      throw new RuntimeException("Lỗi parse JSON list: " + e.getMessage(), e);
    }
  }

  // ========== PRIVATE HELPERS ==========

  /**
   * Tạo HttpRequest.Builder với URI đầy đủ, Content-Type JSON,
   * và Authorization header nếu đã đăng nhập.
   */
  private static HttpRequest.Builder buildRequest(String path) {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + path))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(10));

    // Tự động gắn JWT token nếu đã đăng nhập
    try {
      String token = SceneManager.getInstance().getJwtToken();
      if (token != null && !token.isEmpty()) {
        builder.header("Authorization", "Bearer " + token);
      }
    } catch (IllegalStateException e) {
      // SceneManager chưa khởi tạo (ví dụ: trong test) — bỏ qua
    }

    return builder;
  }

  /** Serialize object thành HttpRequest.BodyPublisher JSON. */
  private static HttpRequest.BodyPublisher toBody(Object body) {
    try {
      String json = MAPPER.writeValueAsString(body);
      return HttpRequest.BodyPublishers.ofString(json);
    } catch (Exception e) {
      throw new RuntimeException("Lỗi serialize request body: " + e.getMessage(), e);
    }
  }

  /** Gửi request và trả về response, throw RuntimeException nếu thất bại. */
  private static HttpResponse<String> send(HttpRequest request) {
    try {
      LOGGER.debug("→ {} {}", request.method(), request.uri());
      HttpResponse<String> response = HTTP_CLIENT.send(
          request, HttpResponse.BodyHandlers.ofString());
      LOGGER.debug("← {} {}", response.statusCode(), request.uri());
      return response;
    } catch (Exception e) {
      LOGGER.error("Lỗi gửi HTTP request: {}", e.getMessage());
      throw new RuntimeException("Không thể kết nối đến server: " + e.getMessage(), e);
    }
  }
}
