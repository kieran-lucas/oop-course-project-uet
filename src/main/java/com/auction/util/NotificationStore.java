package com.auction.util;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Singleton lưu trữ thông báo bid trong phiên — chỉ thêm từ FX thread.
 * Số lượng thông báo chưa đọc được reset khi người dùng mở panel.
 */
public class NotificationStore {

  private static final NotificationStore INSTANCE = new NotificationStore();

  private final ObservableList<String> notifications = FXCollections.observableArrayList();
  private final SimpleIntegerProperty unreadCount = new SimpleIntegerProperty(0);

  private NotificationStore() {}

  public static NotificationStore getInstance() {
    return INSTANCE;
  }

  public void add(String notification) {
    notifications.add(0, notification);
    unreadCount.set(unreadCount.get() + 1);
  }

  public int getUnreadCount() {
    return unreadCount.get();
  }

  public ReadOnlyIntegerProperty unreadCountProperty() {
    return unreadCount;
  }

  public void markAllRead() {
    unreadCount.set(0);
  }

  public ObservableList<String> getNotifications() {
    return notifications;
  }

  public void clear() {
    notifications.clear();
    unreadCount.set(0);
  }
}
