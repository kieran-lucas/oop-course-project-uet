package com.auction.service;

import com.auction.dao.NotificationDao;
import java.util.List;
import java.util.Map;

/** Business layer for notification read-state operations. */
public class NotificationService {

  private final NotificationDao notificationDao;

  public NotificationService(NotificationDao notificationDao) {
    this.notificationDao = notificationDao;
  }

  public List<Map<String, Object>> getRecentNotifications(Long userId) {
    return notificationDao.findRecentByUserId(userId);
  }

  public void markRead(Long notificationId, Long userId) {
    notificationDao.markRead(notificationId, userId);
  }

  public int markAllRead(Long userId) {
    return notificationDao.markAllRead(userId);
  }
}
