package com.auction;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.dao.UserDao;
import com.auction.model.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Khởi tạo tài khoản admin mặc định khi ứng dụng khởi động lần đầu tiên.
 *
 * <p><b>Thứ tự ưu tiên khi lấy mật khẩu:</b>
 *
 * <ol>
 *   <li>Biến môi trường {@code DEFAULT_ADMIN_PASSWORD} — khuyến nghị cho môi trường production.
 *   <li>Giá trị demo fallback {@value #DEMO_FALLBACK_PASSWORD} — chỉ dùng cho demo trên lớp, tuyệt
 *       đối không dùng trong môi trường thật. Khi fallback được dùng, hệ thống sẽ log cảnh báo rõ
 *       ràng.
 * </ol>
 *
 * <p><b>Bảo mật:</b> Mật khẩu plaintext và hash BCrypt của nó không bao giờ được ghi vào log. Chỉ
 * username và hành động thực hiện (created / already exists) mới được log.
 *
 * <p>Method {@link #seed()} được thiết kế idempotent — gọi nhiều lần (mỗi lần khởi động) là an
 * toàn: nếu tài khoản admin đã tồn tại thì thoát sớm mà không thay đổi gì.
 */
public class AdminSeeder {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminSeeder.class);

  /** Username mặc định của tài khoản admin. */
  static final String DEFAULT_ADMIN_USERNAME = "admin";

  /** Email mặc định của tài khoản admin. */
  static final String DEFAULT_ADMIN_EMAIL = "admin@auction.com";

  /** Tên biến môi trường cần đặt để override mật khẩu demo. */
  static final String ENV_PASSWORD_VAR = "DEFAULT_ADMIN_PASSWORD";

  /**
   * Mật khẩu fallback chỉ dùng cho demo — không ghi vào log dù dùng hay không. Luôn đặt biến môi
   * trường {@value #ENV_PASSWORD_VAR} trước khi deploy thực tế.
   */
  private static final String DEMO_FALLBACK_PASSWORD = "123456";

  private final UserDao userDao;

  /**
   * Tạo AdminSeeder với DAO người dùng.
   *
   * @param userDao DAO để kiểm tra và tạo tài khoản admin
   */
  public AdminSeeder(UserDao userDao) {
    this.userDao = userDao;
  }

  /**
   * Seed tài khoản admin nếu chưa tồn tại.
   *
   * <p>Kiểm tra username {@value #DEFAULT_ADMIN_USERNAME} trước khi tạo — thoát sớm nếu đã có. Nếu
   * chưa có, lấy mật khẩu từ biến môi trường hoặc fallback demo, hash với BCrypt cost=12, rồi lưu
   * vào DB.
   *
   * <p>Mọi exception được bắt và log ở mức WARN — không để lỗi seed admin làm crash ứng dụng.
   */
  public void seed() {
    try {
      if (userDao.findByUsername(DEFAULT_ADMIN_USERNAME).isPresent()) {
        LOGGER.info("Default admin account already exists — skipping seed.");
        return;
      }

      String password = resolveAdminPassword();
      // BCrypt cost=12: đủ chậm để chống brute-force nhưng không ảnh hưởng đến trải nghiệm
      // (chỉ hash một lần lúc khởi động, không phải mỗi lần đăng nhập)
      String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
      Admin admin = new Admin(DEFAULT_ADMIN_USERNAME, hash, DEFAULT_ADMIN_EMAIL);
      userDao.insert(admin);
      LOGGER.info("Default admin account created for username: {}", DEFAULT_ADMIN_USERNAME);

    } catch (Exception e) {
      LOGGER.warn("Could not seed default admin account: {}", e.getMessage());
    }
  }

  /**
   * Lấy mật khẩu admin từ biến môi trường hoặc fallback demo.
   *
   * <p>Nếu biến môi trường {@value #ENV_PASSWORD_VAR} không được đặt, log cảnh báo rõ ràng để người
   * vận hành biết đang dùng mật khẩu yếu — giúp phát hiện cấu hình thiếu sót trước khi đưa lên
   * production.
   *
   * @return mật khẩu plaintext (không bao giờ được log giá trị trả về)
   */
  String resolveAdminPassword() {
    String envPassword = System.getenv(ENV_PASSWORD_VAR);
    if (envPassword != null && !envPassword.isBlank()) {
      return envPassword;
    }
    LOGGER.warn(
        "Environment variable {} is not set. Using demo fallback password. "
            + "Set {} before deploying to production.",
        ENV_PASSWORD_VAR,
        ENV_PASSWORD_VAR);
    return DEMO_FALLBACK_PASSWORD;
  }
}
