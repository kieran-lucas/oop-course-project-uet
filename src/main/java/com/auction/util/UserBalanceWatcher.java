package com.auction.util;

import com.auction.dto.BidUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton quản lý kết nối WebSocket kênh riêng của user ({@code /ws/user/{id}}) trong suốt phiên
 * đăng nhập.
 *
 * <h2>Trách nhiệm</h2>
 *
 * <ul>
 *   <li>Mở và duy trì kết nối WebSocket user channel từ lúc đăng nhập đến lúc đăng xuất.
 *   <li>Xử lý message {@code BALANCE_UPDATED} — được server gửi khi Admin duyệt hoặc từ chối yêu
 *       cầu nạp tiền của user.
 *   <li>Load thông báo offline (thông báo chưa đọc từ server) ngay sau khi kết nối thành công.
 * </ul>
 *
 * <h2>Nguồn thông báo deposit — Quy tắc quan trọng</h2>
 *
 * {@code UserBalanceWatcher} là <b>nguồn DUY NHẤT</b> được phép gọi {@link
 * NotificationStore#add(String)} cho các sự kiện liên quan đến deposit. Các controller khác như
 * {@code ProfileController} hay {@code DepositController} <b>tuyệt đối không được</b> gọi {@code
 * NotificationStore.add()} cho loại sự kiện này — làm vậy sẽ gây thông báo trùng lặp.
 *
 * <p>Nếu các controller kia cần phản ứng với sự kiện balance (ví dụ: cập nhật label số dư), chúng
 * đăng ký callback qua {@link #setOnBalanceUpdate(BiConsumer)}.
 *
 * <h2>Callback listener</h2>
 *
 * Chỉ một listener được đăng ký tại một thời điểm. Màn hình nào đang hiển thị thì đăng ký callback
 * của mình, và hủy (truyền {@code null}) trong {@code onNavigatedFrom()} để tránh memory leak và
 * gọi nhầm controller đã rời đi.
 *
 * <h2>Vòng đời</h2>
 *
 * <ol>
 *   <li>{@link #connect(Long, String)} — gọi ngay sau khi đăng nhập thành công.
 *   <li>{@link #setOnBalanceUpdate(BiConsumer)} — đăng ký/hủy callback từ các màn hình.
 *   <li>{@link #disconnect()} — gọi khi đăng xuất (thường qua {@link
 *       com.auction.ui.util.SceneManager#logout()}).
 * </ol>
 *
 * @see NotificationStore
 * @see BackgroundBidWatcher
 * @see com.auction.ui.util.SceneManager#logout()
 */
public class UserBalanceWatcher {

  private static final UserBalanceWatcher INSTANCE = new UserBalanceWatcher();
  private static final Logger LOGGER = LoggerFactory.getLogger(UserBalanceWatcher.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  /** WebSocket client quản lý kết nối đến kênh {@code /ws/user/{id}}. */
  private final WebSocketClient wsClient = new WebSocketClient();

  /**
   * Callback được gọi mỗi khi nhận được sự kiện {@code BALANCE_UPDATED}.
   *
   * <p>Signature: {@code (BigDecimal newBalance, Boolean approved)} — chạy trên FX thread. {@code
   * volatile} để đảm bảo visibility khi callback được set từ FX thread và đọc từ WebSocket callback
   * thread.
   *
   * <p>Giá trị {@code null} có nghĩa là không có màn hình nào đang lắng nghe — sự kiện vẫn được đẩy
   * vào {@link NotificationStore}, chỉ là không có UI nào phản ứng tức thời.
   */
  private volatile BiConsumer<BigDecimal, Boolean> onBalanceUpdate;

  private UserBalanceWatcher() {}

  /**
   * Trả về instance duy nhất của {@code UserBalanceWatcher}.
   *
   * @return singleton instance
   */
  public static UserBalanceWatcher getInstance() {
    return INSTANCE;
  }

  /**
   * Mở kết nối WebSocket đến kênh user và load thông báo offline.
   *
   * <p>Phải được gọi ngay sau khi đăng nhập thành công. Sau khi kết nối, mọi message {@code
   * BALANCE_UPDATED} nhận được sẽ được xử lý tự động cho đến khi {@link #disconnect()} được gọi.
   *
   * <p>Sau khi kết nối WebSocket, {@link #loadOfflineNotifications(String)} được gọi để lấy các
   * thông báo chưa đọc từ server — đảm bảo user thấy thông báo từ trước khi đăng nhập (ví dụ:
   * request nạp tiền được duyệt khi user offline).
   *
   * @param userId ID của user vừa đăng nhập — dùng để xác định đúng kênh WebSocket
   * @param token JWT token để xác thực kết nối WebSocket và request load offline notifications
   */
  public void connect(Long userId, String token) {
    wsClient.connectUser(
        userId,
        token,
        json -> {
          try {
            BidUpdateMessage msg = MAPPER.readValue(json, BidUpdateMessage.class);
            if (BidUpdateMessage.TYPE_USER_NOTIFICATION.equals(msg.getType())) {
              String text = msg.getMessage();
              if (text != null && !text.isBlank()) {
                LOGGER.info("Nhận USER_NOTIFICATION: {}", text);
                Platform.runLater(() -> NotificationStore.getInstance().add(text));
              }
              return;
            }
            if (BidUpdateMessage.TYPE_BALANCE_UPDATED.equals(msg.getType())) {
              // FIX Bug 2: đọc field approved riêng thay vì tái dùng autoBid
              boolean approved = msg.isApproved();
              BigDecimal newBalance = msg.getNewBalance();
              BigDecimal balanceDelta = msg.getBalanceDelta();
              LOGGER.info("Nhận BALANCE_UPDATED: approved={}, newBalance={}", approved, newBalance);

              String serverMessage = msg.getMessage();
              Platform.runLater(
                  () -> {
                    // FIX Bug 1 & 3: UserBalanceWatcher là nguồn DUY NHẤT add vào
                    // NotificationStore.
                    // ProfileController và DepositController KHÔNG được gọi
                    // NotificationStore.add().
                    String text;
                    if (serverMessage != null && !serverMessage.isBlank()) {
                      // Server đã soạn message (settlement, payout, refund, ...).
                      text = serverMessage;
                    } else {
                      // Fallback cho luồng deposit cũ: server chỉ truyền approved + delta.
                      java.text.NumberFormat vndFmt =
                          java.text.NumberFormat.getNumberInstance(java.util.Locale.of("vi", "VN"));
                      BigDecimal absDelta =
                          balanceDelta != null ? balanceDelta.abs() : BigDecimal.ZERO;
                      text =
                          approved
                              ? "Yêu cầu nạp tiền đã được duyệt. Số dư biến động: + "
                                  + vndFmt.format(absDelta)
                                  + " VND"
                              : "❌ Yêu cầu nạp tiền bị từ chối";
                    }
                    NotificationStore.getInstance().add(text);

                    // Notify listener nếu có (màn hình đang hiển thị — chỉ cập nhật UI nội bộ)
                    BiConsumer<BigDecimal, Boolean> cb = onBalanceUpdate;
                    if (cb != null) {
                      cb.accept(newBalance, approved);
                    }
                  });
            }
          } catch (Exception e) {
            LOGGER.debug("UserBalanceWatcher parse error: {}", e.getMessage());
          }
        });
    loadOfflineNotifications(token);
    LOGGER.info("UserBalanceWatcher: kết nối kênh user #{}", userId);
  }

  /**
   * Đăng ký callback nhận thông báo biến động số dư trực tiếp tại UI.
   *
   * <p>Callback được gọi trên FX thread, nhận hai tham số:
   *
   * <ul>
   *   <li>{@code BigDecimal newBalance} — số dư mới sau khi cập nhật
   *   <li>{@code Boolean approved} — {@code true} nếu request được duyệt, {@code false} nếu bị từ
   *       chối
   * </ul>
   *
   * <p><b>Quy tắc đăng ký/hủy:</b> Controller đăng ký trong {@code onNavigatedTo()} hoặc {@code
   * initialize()}, và <em>bắt buộc</em> hủy bằng cách truyền {@code null} trong {@code
   * onNavigatedFrom()} để tránh gọi nhầm vào controller đã rời màn hình.
   *
   * @param callback hàm xử lý {@code (newBalance, approved)}, chạy trên FX thread; truyền {@code
   *     null} để hủy đăng ký
   */
  public void setOnBalanceUpdate(BiConsumer<BigDecimal, Boolean> callback) {
    this.onBalanceUpdate = callback;
  }

  /**
   * Load các thông báo chưa đọc từ server và đẩy vào {@link NotificationStore}.
   *
   * <p>Gọi HTTP GET {@code /api/notifications} ngay sau khi kết nối WebSocket. Chỉ các thông báo có
   * {@code is_read = false} và {@code message} không rỗng mới được thêm vào store — tránh hiển thị
   * lại thông báo user đã đọc ở phiên trước.
   *
   * <p>Toàn bộ việc thêm vào store được bọc trong {@code Platform.runLater()} vì method này chạy
   * trên background thread (được gọi từ constructor của {@link #connect}).
   *
   * <p>Lỗi HTTP hoặc parse được log ở mức {@code ERROR} nhưng không ném exception — đảm bảo quá
   * trình đăng nhập không bị gián đoạn nếu endpoint này thất bại.
   *
   * @param token JWT token dùng để xác thực request
   */
  private void loadOfflineNotifications(String token) {
    try {
      HttpClient client = HttpClient.newHttpClient();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:8080/api/notifications"))
              .header("Authorization", "Bearer " + token)
              .GET()
              .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        var root = MAPPER.readTree(response.body());
        List<NotificationItem> unreadItems = new ArrayList<>();
        for (var node : root) {
          String message = node.has("message") ? node.get("message").asText() : "";
          boolean isRead = node.has("is_read") && node.get("is_read").asBoolean();
          if (!isRead && !message.isEmpty()) {
            Long id = node.has("id") && !node.get("id").isNull() ? node.get("id").asLong() : null;
            String type =
                node.has("notification_type") && !node.get("notification_type").isNull()
                    ? node.get("notification_type").asText()
                    : null;
            LocalDateTime createdAt = null;
            if (node.has("created_at") && !node.get("created_at").isNull()) {
              try {
                createdAt = MAPPER.convertValue(node.get("created_at"), LocalDateTime.class);
              } catch (Exception ignored) {
                // createdAt remains null if parsing fails
              }
            }
            unreadItems.add(new NotificationItem(id, message, type, false, createdAt));
          }
        }
        Collections.reverse(unreadItems);
        Platform.runLater(
            () -> {
              for (NotificationItem item : unreadItems) {
                NotificationStore.getInstance().add(item);
                LOGGER.info("Đã load offline notification: {}", item.getMessage());
              }
            });
      }
    } catch (Exception e) {
      LOGGER.error("Lỗi load offline notifications: {}", e.getMessage());
    }
  }

  /**
   * Đóng kết nối WebSocket và hủy callback đang đăng ký.
   *
   * <p>Callback được set về {@code null} trước khi đóng kết nối để tránh trường hợp callback bị gọi
   * trong khoảng thời gian giữa lúc disconnect và lúc WebSocket thực sự đóng.
   *
   * <p>Thường được gọi bởi {@link com.auction.ui.util.SceneManager#logout()}.
   */
  public void disconnect() {
    onBalanceUpdate = null;
    wsClient.disconnect();
    LOGGER.info("UserBalanceWatcher: đã ngắt kết nối.");
  }
}
