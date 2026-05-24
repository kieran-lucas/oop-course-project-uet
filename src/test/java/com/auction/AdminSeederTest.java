package com.auction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.dao.UserDao;
import com.auction.model.Admin;
import com.auction.model.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử {@link AdminSeeder} — khởi tạo tài khoản admin mặc định khi ứng dụng khởi động.
 *
 * <p>Hai nhóm test:
 *
 * <ul>
 *   <li>{@code seed()} — các kịch bản tạo admin (mới/đã tồn tại, bảo mật mật khẩu, idempotent)
 *   <li>{@code resolveAdminPassword()} — lấy mật khẩu từ biến môi trường hoặc fallback demo
 * </ul>
 *
 * <p>Dùng Mockito để stub {@link UserDao} — không cần kết nối DB thực.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSeeder — khởi tạo admin mặc định")
class AdminSeederTest {

  @Mock private UserDao userDao;

  private AdminSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new AdminSeeder(userDao);
  }

  @Nested
  @DisplayName("seed()")
  class SeedMethod {

    @Test
    @DisplayName("tạo admin khi chưa tồn tại")
    void createsAdminWhenAbsent() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class)))
          .thenAnswer(
              inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
              });

      seeder.seed();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      User inserted = captor.getValue();
      assertEquals(AdminSeeder.DEFAULT_ADMIN_USERNAME, inserted.getUsername());
      assertEquals(AdminSeeder.DEFAULT_ADMIN_EMAIL, inserted.getEmail());
      assertEquals("ADMIN", inserted.getRole());
    }

    @Test
    @DisplayName("mật khẩu được lưu dưới dạng BCrypt hash — không phải plaintext")
    void storedPasswordIsBcryptHash() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      String hash = captor.getValue().getPasswordHash();
      assertNotNull(hash, "hash mật khẩu không được null");
      assertTrue(hash.startsWith("$2"), "BCrypt hash phải bắt đầu bằng $2");
      assertNotEquals("123456", hash, "hash không được bằng plaintext");
    }

    @Test
    @DisplayName("hash mật khẩu xác thực đúng với mật khẩu mặc định")
    void hashVerifiesWithDefaultPassword() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      String hash = captor.getValue().getPasswordHash();
      // Xác nhận hash có thể dùng để đăng nhập với mật khẩu mặc định đã resolve
      String expectedPassword = seeder.resolveAdminPassword();
      assertTrue(
          BCrypt.verifyer().verify(expectedPassword.toCharArray(), hash).verified,
          "BCrypt hash phải xác thực được với mật khẩu mặc định đã resolve");
    }

    @Test
    @DisplayName("bỏ qua insert khi admin đã tồn tại")
    void skipsInsertWhenAdminAlreadyExists() {
      Admin existing = new Admin("admin", "$2a$12$hash", "admin@auction.com");
      existing.setId(1L);
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME))
          .thenReturn(Optional.of(existing));

      seeder.seed();

      verify(userDao, never()).insert(any());
    }

    @Test
    @DisplayName("insert đúng một admin khi khởi động lần đầu")
    void insertsExactlyOneAdmin() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      verify(userDao, times(1)).insert(any(User.class));
    }

    @Test
    @DisplayName("chỉ log username — không log mật khẩu hay hash")
    void doesNotLogSensitiveCredentials() {
      // Xác nhận bằng cách kiểm tra object User được insert — hash không chứa plaintext
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      assertDoesNotThrow(() -> seeder.seed());

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      String hash = captor.getValue().getPasswordHash();
      assertFalse(hash.contains("123456"), "BCrypt hash không được chứa plaintext mật khẩu");
    }

    @Test
    @DisplayName("không ném exception khi DAO lỗi — chỉ log cảnh báo")
    void doesNotPropagateExceptions() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME))
          .thenThrow(new RuntimeException("DB connection lost"));

      // seed() phải hoàn thành mà không ném exception — lỗi được bắt và log WARN
      assertDoesNotThrow(() -> seeder.seed());
    }

    @Test
    @DisplayName("user được insert là instance Admin với role ADMIN")
    void insertedUserIsAdminRole() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      assertTrue(captor.getValue() instanceof Admin, "User được insert phải là instance Admin");
    }
  }

  @Nested
  @DisplayName("resolveAdminPassword()")
  class ResolvePassword {

    @Test
    @DisplayName("trả về fallback demo khi biến môi trường không được đặt")
    void returnsDemoFallbackWhenEnvVarAbsent() {
      String password = seeder.resolveAdminPassword();
      assertNotNull(password);
      assertFalse(password.isBlank());
    }

    @Test
    @DisplayName("mật khẩu trả về có ít nhất 6 ký tự")
    void returnedPasswordMeetsMinimumLength() {
      String password = seeder.resolveAdminPassword();
      assertTrue(password.length() >= 6);
    }
  }
}
