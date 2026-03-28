package com.auction;

import io.javalin.Javalin;

public class App {

  public static void main(String[] args) {
    Javalin app =
        Javalin.create(
            config -> {
              config.http.defaultContentType = "application/json";
            });

    // Health check endpoint
    app.get("/api/health", ctx -> ctx.json(java.util.Map.of("status", "ok")));

    app.start(8080);
    System.out.println("Server started on http://localhost:8080");
  }
}
