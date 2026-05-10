package com.auction;

/**
 * Launcher — wrapper bắt buộc để chạy JavaFX từ fat JAR.
 *
 * <p><b>Tại sao cần class này?</b>
 *
 * <p>Khi đóng gói fat JAR, JVM kiểm tra xem {@code Main-Class} trong {@code MANIFEST.MF} có extends
 * {@code javafx.application.Application} không. Nếu có → JVM ném lỗi vì JavaFX chưa được khởi tạo
 * đúng cách trong môi trường module.
 *
 * <p>Giải pháp chuẩn: dùng một class trung gian <b>không</b> extends {@code Application} làm {@code
 * Main-Class}, class này gọi {@code ClientApp.main()} → JavaFX khởi động bình thường.
 *
 * <p><b>Dùng khi:</b> chạy client độc lập bằng {@code java -jar auction-client.jar}
 *
 * @see ClientApp
 * @see Main
 */
public class Launcher {

  /**
   * Entry point cho fat JAR client.
   *
   * @param args tham số dòng lệnh (được truyền thẳng vào ClientApp)
   */
  public static void main(String[] args) {
    ClientApp.main(args);
  }
}
