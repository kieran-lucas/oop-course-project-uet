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
 * HTTP client tĩnh dùng cho JavaFX client giao tiếp với server Javalin qua REST API.
 *
 * <h2>Vị trí trong kiến trúc</h2>
 *
 * Tất cả UI Controller đều gọi {@code RestClient} thay vì tự tạo {@link HttpClient}. {@code
 * RestClient} là lớp duy nhất chịu trách nhiệm:
 *
 * <ul>
 *   <li>Xây dựng URI đầy đủ từ {@link #BASE_URL} + {@code path}.
 *   <li>Gắn header {@code Content-Type: application/json} cho mọi request.
 *   <li>Tự động gắn {@code Authorization: Bearer <token>} nếu người dùng đã đăng nhập — token lấy
 *       từ {@link SceneManager#getJwtToken()}.
 *   <li>Serialize request body và deserialize response body qua {@link ObjectMapper}.
 *   <li>Log request/response ở mức {@code DEBUG} để dễ trace khi phát triển.
 * </ul>
 *
 * <h2>Cách sử dụng</h2>
 *
 * <pre>{@code
 * // GET đơn giản
 * HttpResponse<String> resp = RestClient.get("/api/auctions");
 * if (resp.statusCode() == 200) {
 *     List<AuctionResponse> list = RestClient.parseList(resp.body(), AuctionResponse.class);
 * }
 *
 * // POST với body
 * HttpResponse<String> resp = RestClient.post("/api/bids", new BidRequest(auctionId, amount));
 * if (resp.statusCode() == 201) {
 *     BidResponse bid = RestClient.parse(resp.body(), BidResponse.class);
 * }
 * }</pre>
 *
 * <h2>Xử lý lỗi</h2>
 *
 * Các method HTTP ném {@link RuntimeException} nếu không thể kết nối đến server (timeout, network
 * error). Lỗi HTTP level (4xx, 5xx) <em>không</em> ném exception — caller tự kiểm tra {@code
 * response.statusCode()} để xử lý.
 *
 * <h2>Thread safety</h2>
 *
 * {@link HttpClient} và {@link ObjectMapper} là thread-safe và được tái dùng cho mọi request. Toàn
 * bộ method là {@code static} — không có state instance. Có thể gọi từ bất kỳ thread nào, nhưng nên
 * gọi từ background thread để tránh block FX thread.
 */
public class RestClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestClient.class);

  /**
   * Base URL của server — phải khớp với {@code SERVER_PORT} được cấu hình trong {@code App.java}.
   *
   * <p>Tất cả {@code path} truyền vào các method đều được nối vào sau URL này.
   */
  private static final String BASE_URL = "http://localhost:8080";

  /**
   * HTTP client dùng chung cho toàn bộ request — thread-safe, tái sử dụng connection pool.
   *
   * <p>{@code connectTimeout} 5 giây: nếu server không phản hồi trong thời gian này khi thiết lập
   * kết nối, request sẽ ném {@code HttpConnectTimeoutException}.
   */
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  /**
   * ObjectMapper dùng chung — thread-safe sau khi cấu hình xong.
   *
   * <p>Cấu hình:
   *
   * <ul>
   *   <li>{@link JavaTimeModule}: hỗ trợ serialize/deserialize {@code LocalDateTime}, {@code
   *       LocalDate}, {@code Instant}...
   *   <li>{@code WRITE_DATES_AS_TIMESTAMPS = false}: serialize date dưới dạng ISO-8601 string thay
   *       vì số nguyên Unix timestamp.
   * </ul>
   */
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /** Utility class — không cho phép khởi tạo instance. */
  private RestClient() {}

  // ========== PUBLIC API ==========

  /**
   * Gửi GET request đến {@code BASE_URL + path}.
   *
   * @param path đường dẫn API bắt đầu bằng {@code /}, ví dụ: {@code "/api/auctions"}
   * @return {@link HttpResponse} chứa body JSON dạng String; status code phản ánh kết quả HTTP
   * @throws RuntimeException nếu không thể kết nối đến server (network error, timeout)
   */
  public static HttpResponse<String> get(String path) {
    HttpRequest request = buildRequest(path).GET().build();
    return send(request);
  }

  /**
   * Gửi POST request với body được serialize từ {@code body} thành JSON.
   *
   * @param path đường dẫn API
   * @param body object sẽ được serialize thành JSON; truyền {@code null} để gửi body rỗng
   * @return {@link HttpResponse} chứa body JSON
   * @throws RuntimeException nếu không thể serialize {@code body} hoặc kết nối thất bại
   */
  public static HttpResponse<String> post(String path, Object body) {
    HttpRequest request = buildRequest(path).POST(toBody(body)).build();
    return send(request);
  }

  /**
   * Gửi PUT request với body được serialize từ {@code body} thành JSON.
   *
   * @param path đường dẫn API
   * @param body object sẽ được serialize thành JSON; truyền {@code null} để gửi body rỗng
   * @return {@link HttpResponse} chứa body JSON
   * @throws RuntimeException nếu không thể serialize {@code body} hoặc kết nối thất bại
   */
  public static HttpResponse<String> put(String path, Object body) {
    HttpRequest request = buildRequest(path).PUT(toBody(body)).build();
    return send(request);
  }

  public static HttpResponse<String> patch(String path, Object body) {
    HttpRequest request = buildRequest(path).method("PATCH", toBody(body)).build();
    return send(request);
  }

  /**
   * Gửi DELETE request đến {@code BASE_URL + path}.
   *
   * @param path đường dẫn API
   * @return {@link HttpResponse} chứa body JSON (thường rỗng hoặc là message xác nhận)
   * @throws RuntimeException nếu kết nối thất bại
   */
  public static HttpResponse<String> delete(String path) {
    HttpRequest request = buildRequest(path).DELETE().build();
    return send(request);
  }

  /**
   * Deserialize JSON string thành object của kiểu {@code clazz}.
   *
   * @param <T> kiểu đích
   * @param json JSON string cần parse
   * @param clazz {@link Class} của kiểu đích
   * @return object đã được deserialize
   * @throws RuntimeException nếu JSON không hợp lệ hoặc không khớp với {@code clazz}
   */
  public static <T> T parse(String json, Class<T> clazz) {
    try {
      return MAPPER.readValue(json, clazz);
    } catch (Exception e) {
      throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
    }
  }

  /**
   * Deserialize JSON array string thành {@link java.util.List} của kiểu {@code clazz}.
   *
   * <p>Ví dụ: {@code RestClient.parseList(resp.body(), AuctionResponse.class)} trả về {@code
   * List<AuctionResponse>}.
   *
   * @param <T> kiểu phần tử trong list
   * @param json JSON array string (phải bắt đầu bằng {@code [})
   * @param clazz {@link Class} của kiểu phần tử
   * @return {@link java.util.List} đã được deserialize
   * @throws RuntimeException nếu JSON không phải array hoặc không khớp với {@code clazz}
   */
  public static <T> java.util.List<T> parseList(String json, Class<T> clazz) {
    try {
      var type = MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, clazz);
      return MAPPER.readValue(json, type);
    } catch (Exception e) {
      throw new RuntimeException("JSON list parse error: " + e.getMessage(), e);
    }
  }

  // ========== PRIVATE HELPERS ==========

  /**
   * Xây dựng {@link HttpRequest.Builder} với URI đầy đủ, header chuẩn, và JWT token nếu có.
   *
   * <p>Header được gắn tự động:
   *
   * <ul>
   *   <li>{@code Content-Type: application/json} — luôn có.
   *   <li>{@code Authorization: Bearer <token>} — chỉ gắn khi {@link SceneManager} đã khởi tạo và
   *       token không rỗng. Nếu {@code SceneManager} chưa init (ví dụ: trong unit test), lỗi bị bắt
   *       và bỏ qua — request vẫn được gửi đi, chỉ thiếu header auth.
   * </ul>
   *
   * <p>Request timeout mặc định là 10 giây — tính từ lúc request được gửi đến khi nhận response đầy
   * đủ.
   *
   * @param path đường dẫn API
   * @return builder đã được cấu hình sẵn
   */
  private static HttpRequest.Builder buildRequest(String path) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
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

  /**
   * Serialize {@code body} thành {@link HttpRequest.BodyPublisher} JSON.
   *
   * <p>Trả về {@link HttpRequest.BodyPublishers#noBody()} nếu {@code body} là {@code null}, tránh
   * ném {@code NullPointerException} khi gọi {@link ObjectMapper#writeValueAsString}.
   *
   * @param body object cần serialize; {@code null} được chấp nhận
   * @return body publisher chứa JSON string, hoặc no-body nếu {@code body == null}
   * @throws RuntimeException nếu {@code body} không thể serialize thành JSON
   */
  private static HttpRequest.BodyPublisher toBody(Object body) {
    if (body == null) {
      return HttpRequest.BodyPublishers.noBody();
    }
    try {
      String json = MAPPER.writeValueAsString(body);
      return HttpRequest.BodyPublishers.ofString(json);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize request body: " + e.getMessage(), e);
    }
  }

  /**
   * Gửi request đồng bộ và trả về response.
   *
   * <p>Log method và URI ở mức {@code DEBUG} cả trước và sau khi gửi, giúp trace luồng request khi
   * phát triển. Mọi exception (network error, timeout, interrupt) đều được bọc trong {@link
   * RuntimeException} với message mô tả để caller dễ xử lý.
   *
   * @param request request đã được build sẵn
   * @return {@link HttpResponse} chứa status code và body
   * @throws RuntimeException nếu gửi request thất bại vì bất kỳ lý do nào
   */
  private static HttpResponse<String> send(HttpRequest request) {
    try {
      LOGGER.debug("→ {} {}", request.method(), request.uri());
      HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      LOGGER.debug("← {} {}", response.statusCode(), request.uri());
      return response;
    } catch (Exception e) {
      LOGGER.error("Lỗi gửi HTTP request: {}", e.getMessage());
      throw new RuntimeException("Unable to connect to the server: " + e.getMessage(), e);
    }
  }
}
