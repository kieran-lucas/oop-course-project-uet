package com.auction.util;

import java.util.prefs.Preferences;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Singleton lưu trữ danh sách thông báo bid trong phiên làm việc hiện tại.
 *
 * <h2>Nguồn dữ liệu</h2>
 *
 * Có hai nguồn đẩy thông báo vào store:
 *
 * <ul>
 *   <li>{@link UserBalanceWatcher} — thông báo liên quan đến biến động số dư (deposit được duyệt /
 *       từ chối), bao gồm cả thông báo offline load khi vừa đăng nhập.
 *   <li>{@link BackgroundBidWatcher} — thông báo realtime khi bị vượt giá, auto-bid kích hoạt, hoặc
 *       phiên đấu giá kết thúc trong khi user đã rời màn hình chi tiết.
 * </ul>
 *
 * <p><b>Quan trọng:</b> Chỉ được gọi {@link #add(String)} từ <em>JavaFX Application Thread</em> (FX
 * thread). Các caller từ background thread phải bọc trong {@code Platform.runLater()}.
 *
 * <h2>Unread count</h2>
 *
 * {@link #unreadCountProperty()} là {@link ReadOnlyIntegerProperty} có thể bind trực tiếp vào UI
 * (badge, label...). Count tăng mỗi khi {@link #add} được gọi và reset về 0 khi user mở panel thông
 * báo (gọi {@link #markAllRead()}).
 *
 * <h2>Vòng đời</h2>
 *
 * Store tồn tại trong suốt phiên. Khi user đăng xuất, {@link SceneManager#logout()} gọi {@link
 * #clear()} để xóa toàn bộ thông báo và reset unread count trước khi điều hướng về {@code
 * welcome.fxml}.
 *
 * @see UserBalanceWatcher
 * @see BackgroundBidWatcher
 * @see com.auction.ui.util.SceneManager#logout()
 */
public class NotificationStore {

  private static final NotificationStore INSTANCE = new NotificationStore();
  private static final String READ_COUNT_KEY = "notifications_read_count";

  /**
   * Danh sách thông báo theo thứ tự thêm vào ngược (thông báo mới nhất ở index 0).
   *
   * <p>{@link ObservableList} cho phép UI (ListView, badge...) tự động cập nhật khi list thay đổi
   * mà không cần polling hay callback thủ công.
   */
  private final ObservableList<String> notifications = FXCollections.observableArrayList();

  private final Preferences preferences = Preferences.userNodeForPackage(NotificationStore.class);

  /**
   * Số lượng thông báo chưa đọc — tăng mỗi khi {@link #add} được gọi, reset khi {@link
   * #markAllRead()} được gọi.
   *
   * <p>Exposed dưới dạng {@link ReadOnlyIntegerProperty} qua {@link #unreadCountProperty()} để bên
   * ngoài có thể observe nhưng không thể set trực tiếp.
   */
  private final SimpleIntegerProperty unreadCount = new SimpleIntegerProperty(0);

  private NotificationStore() {
    refreshUnreadCount();
  }

  /**
   * Trả về instance duy nhất của {@code NotificationStore}.
   *
   * @return singleton instance
   */
  public static NotificationStore getInstance() {
    return INSTANCE;
  }

  /**
   * Thêm một thông báo mới vào đầu danh sách và tăng unread count lên 1.
   *
   * <p>Thông báo mới luôn được chèn tại index 0 để hiển thị ở trên cùng trong UI.
   *
   * <p><b>Thread safety:</b> Method này phải được gọi từ FX thread. Nếu gọi từ background thread,
   * bọc trong {@code Platform.runLater(() -> NotificationStore.getInstance().add(text))}.
   *
   * @param notification nội dung thông báo cần thêm; không được {@code null}
   */
  public void add(String notification) {
    // Deduplicate: bo qua neu thong bao giong het da ton tai trong 5 phan tu dau
    // (phong truong hop loadOfflineNotifications va WebSocket push cung mot thong bao)
    int checkLimit = Math.min(notifications.size(), 5);
    for (int i = 0; i < checkLimit; i++) {
      if (notification.equals(notifications.get(i))) {
        return; // Da co roi, bo qua
      }
    }
    notifications.add(0, notification);
    refreshUnreadCount();
  }

  /**
   * Trả về số lượng thông báo hiện chưa được đọc.
   *
   * @return số thông báo chưa đọc (luôn {@code >= 0})
   */
  public int getUnreadCount() {
    return unreadCount.get();
  }

  /**
   * Trả về {@link ReadOnlyIntegerProperty} của unread count — dùng để bind vào UI hoặc đăng ký
   * {@code ChangeListener}.
   *
   * <p>Ví dụ: {@code SceneManager} lắng nghe property này để cập nhật global notification badge tự
   * động khi có thông báo mới.
   *
   * @return read-only property của unread count
   */
  public ReadOnlyIntegerProperty unreadCountProperty() {
    return unreadCount;
  }

  /**
   * Đánh dấu tất cả thông báo hiện tại là đã đọc bằng cách reset unread count về 0.
   *
   * <p>Không xóa thông báo khỏi danh sách — người dùng vẫn có thể xem lại lịch sử. Thường được gọi
   * khi người dùng mở notification panel.
   */
  public void markAllRead() {
    preferences.putInt(READ_COUNT_KEY, notifications.size());
    unreadCount.set(0);
  }

  /**
   * Trả về danh sách thông báo dưới dạng {@link ObservableList} — có thể bind trực tiếp vào {@code
   * ListView} hoặc các control khác trong JavaFX.
   *
   * <p>Caller không nên modify list trả về trực tiếp; dùng {@link #add} và {@link #clear} thay thế
   * để đảm bảo unread count luôn đồng bộ.
   *
   * @return observable list các thông báo, index 0 là thông báo mới nhất
   */
  public ObservableList<String> getNotifications() {
    return notifications;
  }

  /**
   * Xóa toàn bộ thông báo và reset unread count về 0.
   *
   * <p>Được gọi bởi {@link com.auction.ui.util.SceneManager#logout()} khi người dùng đăng xuất, đảm
   * bảo phiên mới không nhìn thấy thông báo của phiên cũ.
   */
  public void clear() {
    notifications.clear();
    preferences.putInt(READ_COUNT_KEY, 0);
    unreadCount.set(0);
  }

  private void refreshUnreadCount() {
    int savedReadCount = preferences.getInt(READ_COUNT_KEY, 0);
    unreadCount.set(Math.max(0, notifications.size() - savedReadCount));
  }
}
