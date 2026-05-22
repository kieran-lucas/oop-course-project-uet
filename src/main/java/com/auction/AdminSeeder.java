package com.auction;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.dao.UserDao;
import com.auction.model.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seeds the default admin account on first startup.
 *
 * <p>Password resolution order:
 *
 * <ol>
 *   <li>Environment variable {@code DEFAULT_ADMIN_PASSWORD} (recommended for production).
 *   <li>Fallback demo value {@value #DEMO_FALLBACK_PASSWORD} — acceptable only for classroom demos.
 *       Never use this fallback in production; set the environment variable instead.
 * </ol>
 *
 * <p>The plaintext password and its BCrypt hash are never written to any log. Only the username and
 * the action taken (created / already exists) are logged.
 */
public class AdminSeeder {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminSeeder.class);

  static final String DEFAULT_ADMIN_USERNAME = "admin";
  static final String DEFAULT_ADMIN_EMAIL = "admin@auction.com";
  static final String ENV_PASSWORD_VAR = "DEFAULT_ADMIN_PASSWORD";

  /**
   * Demo-only fallback password. This value is intentionally not logged. Change it via the {@value
   * #ENV_PASSWORD_VAR} environment variable before any real deployment.
   */
  private static final String DEMO_FALLBACK_PASSWORD = "123456";

  private final UserDao userDao;

  public AdminSeeder(UserDao userDao) {
    this.userDao = userDao;
  }

  /**
   * Seeds the admin account if it does not yet exist. Safe to call on every startup — exits early
   * if the admin account already exists without touching any credentials.
   */
  public void seed() {
    try {
      if (userDao.findByUsername(DEFAULT_ADMIN_USERNAME).isPresent()) {
        LOGGER.info("Default admin account already exists — skipping seed.");
        return;
      }

      String password = resolveAdminPassword();
      String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
      Admin admin = new Admin(DEFAULT_ADMIN_USERNAME, hash, DEFAULT_ADMIN_EMAIL);
      userDao.insert(admin);
      LOGGER.info("Default admin account created for username: {}", DEFAULT_ADMIN_USERNAME);

    } catch (Exception e) {
      LOGGER.warn("Could not seed default admin account: {}", e.getMessage());
    }
  }

  /**
   * Resolves the admin password from the environment variable or the demo fallback. The resolved
   * password is never logged regardless of which source is used.
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
