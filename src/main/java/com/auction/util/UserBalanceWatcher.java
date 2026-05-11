package com.auction.util;

import com.auction.dto.BidUpdateMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton quản lý kết nối WebSocket kênh riêng của user ({@code /ws/user/{id}}).
 *
 * <p>Mở kết nối sau khi đăng nhập thành công và giữ trong suốt phiên làm việc. Khi Admin duyệt hoặc
 * từ chối yêu cầu nạp tiền, server gửi message {@code BALANCE_UPDATED} qua kênh này.
 *
 * <p>UserBalanceWatcher là nguồn DUY NHẤT gọi {@link NotificationStore#add(String)} cho sự kiện
 * deposit. Các controller khác (ProfileController, DepositController) chỉ được cập nhật UI nội bộ
 * của màn hình mình thông qua callback.
 *
 * <p>Các màn hình muốn nhận thông báo đăng ký callback qua {@link #setOnBalanceUpdate(BiConsumer)}.
 * Chỉ có một listener tại một thời điểm — màn hình nào đang hiển thị thì đăng ký, khi rời đi thì
 * hủy bằng cách truyền {@code null}.
 */
public class UserBalanceWatcher {

  private static final UserBalanceWatcher INSTANCE = new UserBalanceWatcher();
  private static final Logger LOGGER = LoggerFactory.getLogger(UserBalanceWatcher.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private final WebSocketClient wsClient = new WebSocketClient();

  /** Callback nhận (newBalance, approved) — chạy trên FX thread. */
  private volatile BiConsumer<BigDecimal, Boolean> onBalanceUpdate;

  private UserBalanceWatcher() {}

  public static UserBalanceWatcher getInstance() {
    return INSTANCE;
  }

  /**
   * Mở kết nối WebSocket user — gọi ngay sau khi đăng nhập thành công.
   *
   * @param userId ID user hiện tại
   * @param token JWT token
   */
  public void connect(Long userId, String token) {
    wsClient.connectUser(
        userId,
        token,
        json -> {
          try {
            BidUpdateMessage msg = MAPPER.readValue(json, BidUpdateMessage.class);
            if (BidUpdateMessage.TYPE_BALANCE_UPDATED.equals(msg.getType())) {
              // FIX Bug 2: đọc field approved riêng thay vì tái dùng autoBid
              boolean approved = msg.isApproved();
              BigDecimal newBalance = msg.getNewBalance();
              LOGGER.info("Nhận BALANCE_UPDATED: approved={}, newBalance={}", approved, newBalance);

              Platform.runLater(
                  () -> {
                    // FIX Bug 1 & 3: UserBalanceWatcher là nguồn DUY NHẤT add vào
                    // NotificationStore.
                    // ProfileController và DepositController KHÔNG được gọi
                    // NotificationStore.add().
                    String text =
                        approved
                            ? "✅ Yêu cầu nạp tiền được duyệt"
                            : "❌ Yêu cầu nạp tiền bị từ chối";
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
    LOGGER.info("UserBalanceWatcher: kết nối kênh user #{}", userId);
  }

  /**
   * Đăng ký callback nhận thông báo biến động số dư.
   *
   * @param callback hàm nhận (newBalance, approved), chạy trên FX thread. Truyền {@code null} để
   *     hủy đăng ký.
   */
  public void setOnBalanceUpdate(BiConsumer<BigDecimal, Boolean> callback) {
    this.onBalanceUpdate = callback;
  }

  /** Đóng kết nối — gọi khi người dùng đăng xuất. */
  public void disconnect() {
    onBalanceUpdate = null;
    wsClient.disconnect();
    LOGGER.info("UserBalanceWatcher: đã ngắt kết nối.");
  }
}
