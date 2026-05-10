package com.auction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main — entry point duy nhất để chạy toàn bộ hệ thống bằng 1 lệnh.
 *
 * <pre>
 *   java -jar auction.jar
 * </pre>
 *
 * <p><b>Thứ tự khởi động:</b>
 *
 * <ol>
 *   <li>Server (Javalin + Database) khởi động trong background thread.
 *   <li>Main thread poll {@code GET /api/health} mỗi 500ms, chờ tối đa 30 giây.
 *   <li>Khi server sẵn sàng → JavaFX client khởi động trên main thread.
 * </ol>
 *
 * <p><b>Lưu ý:</b> JavaFX <b>bắt buộc</b> phải chạy trên main thread. Vì vậy server phải được đẩy
 * vào thread riêng, còn main thread dành cho JavaFX.
 *
 * <p><b>Shutdown:</b> Khi người dùng đóng cửa sổ JavaFX, JVM thoát → server thread (daemon) tự động
 * dừng → {@code DatabaseConfig.shutDown()} được gọi qua shutdown hook trong {@code App}.
 */
public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  /** URL health check — tương ứng với route {@code /api/health} trong App.java */
  private static final String HEALTH_URL = "http://localhost:8080/api/health";

  /** Thời gian tối đa chờ server khởi động (milliseconds) */
  private static final int MAX_WAIT_MS = 30_000;

  /** Khoảng thời gian giữa mỗi lần poll health check (milliseconds) */
  private static final int POLL_INTERVAL_MS = 500;

  /**
   * Entry point chính.
   *
   * @param args tham số dòng lệnh
   */
  public static void main(String[] args) {
    LOGGER.info("=== Online Auction System đang khởi động ===");

    // ── Bước 1: Khởi động server trong background thread ──────────────────
    // setDaemon(true): thread này tự dừng khi main thread (JavaFX) kết thúc
    Thread serverThread =
        new Thread(
            () -> {
              try {
                LOGGER.info("[Server] Đang khởi động...");
                App.main(args);
              } catch (Exception e) {
                LOGGER.error("[Server] Lỗi khởi động server: {}", e.getMessage(), e);
              }
            });
    serverThread.setName("auction-server-thread");
    serverThread.setDaemon(true);
    serverThread.start();

    // ── Bước 2: Chờ server sẵn sàng ──────────────────────────────────────
    LOGGER.info("[Main] Đang chờ server khởi động...");
    boolean serverReady = waitForServer(MAX_WAIT_MS, POLL_INTERVAL_MS);

    if (!serverReady) {
      LOGGER.error("[Main] Server không khởi động được trong {}ms — thoát.", MAX_WAIT_MS);
      System.exit(1);
    }

    LOGGER.info("[Main] Server đã sẵn sàng — khởi động giao diện...");

    // ── Bước 3: Khởi động JavaFX trên main thread ─────────────────────────
    // ClientApp.main() gọi Application.launch() — bắt buộc phải trên main thread
    ClientApp.main(args);
  }

  /**
   * Poll {@code GET /api/health} cho đến khi nhận được HTTP 200 hoặc hết thời gian chờ.
   *
   * @param maxWaitMs thời gian tối đa chờ (ms)
   * @param pollIntervalMs khoảng cách giữa các lần thử (ms)
   * @return {@code true} nếu server sẵn sàng, {@code false} nếu timeout
   */
  private static boolean waitForServer(int maxWaitMs, int pollIntervalMs) {
    HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(pollIntervalMs)).build();

    long deadline = System.currentTimeMillis() + maxWaitMs;
    int attempt = 0;

    while (System.currentTimeMillis() < deadline) {
      attempt++;
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(HEALTH_URL))
                .timeout(Duration.ofMillis(pollIntervalMs))
                .GET()
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        if (response.statusCode() == 200) {
          LOGGER.info("[Main] Server sẵn sàng sau {} lần thử.", attempt);
          return true;
        }

      } catch (Exception e) {
        // Server chưa khởi động xong — bình thường, tiếp tục chờ
        LOGGER.debug("[Main] Lần thử {}: server chưa sẵn sàng ({})", attempt, e.getMessage());
      }

      try {
        Thread.sleep(pollIntervalMs);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    return false;
  }
}
