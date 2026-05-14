package com.auction.controller;

import com.auction.service.NotificationService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Controller for notification endpoints owned by the authenticated user. */
public class NotificationController {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationController.class);

  private NotificationController() {}

  public static void register(Javalin app, NotificationService notificationService) {
    app.get("/api/notifications", ctx -> handleGetNotifications(ctx, notificationService));
    app.patch("/api/notifications/{id}/read", ctx -> handleMarkRead(ctx, notificationService));
    app.patch(
        "/api/notifications/mark-all-read", ctx -> handleMarkAllRead(ctx, notificationService));

    LOGGER.info(
        "Registered NotificationController: GET /api/notifications, "
            + "PATCH /api/notifications/:id/read, PATCH /api/notifications/mark-all-read");
  }

  private static void handleGetNotifications(Context ctx, NotificationService notificationService) {
    Long userId = ctx.attribute("userId");
    ctx.json(notificationService.getRecentNotifications(userId));
  }

  private static void handleMarkRead(Context ctx, NotificationService notificationService) {
    Long userId = ctx.attribute("userId");
    Long notificationId = Long.parseLong(ctx.pathParam("id"));

    notificationService.markRead(notificationId, userId);
    ctx.status(204);
  }

  private static void handleMarkAllRead(Context ctx, NotificationService notificationService) {
    Long userId = ctx.attribute("userId");

    int updated = notificationService.markAllRead(userId);
    ctx.json(Map.of("updated", updated));
  }
}
