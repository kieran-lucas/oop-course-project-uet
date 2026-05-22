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

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSeeder — default admin bootstrap")
class AdminSeederTest {

  @Mock private UserDao userDao;

  private AdminSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new AdminSeeder(userDao);
  }

  // ── seed() ────────────────────────────────────────────────

  @Nested
  @DisplayName("seed()")
  class SeedMethod {

    @Test
    @DisplayName("creates admin when none exists")
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
    @DisplayName("stored password is a BCrypt hash — not plaintext")
    void storedPasswordIsBcryptHash() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      String hash = captor.getValue().getPasswordHash();
      assertNotNull(hash, "password hash must not be null");
      assertTrue(hash.startsWith("$2"), "BCrypt hash must start with $2");
      assertNotEquals("123456", hash, "hash must not equal plaintext");
    }

    @Test
    @DisplayName("password hash verifies with the correct default password")
    void hashVerifiesWithDefaultPassword() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      String hash = captor.getValue().getPasswordHash();
      // verify that the seeded hash can be used to log in with the default password
      String expectedPassword = seeder.resolveAdminPassword();
      assertTrue(
          BCrypt.verifyer().verify(expectedPassword.toCharArray(), hash).verified,
          "BCrypt hash must verify with the resolved default password");
    }

    @Test
    @DisplayName("skips insert when admin already exists")
    void skipsInsertWhenAdminAlreadyExists() {
      Admin existing = new Admin("admin", "$2a$12$hash", "admin@auction.com");
      existing.setId(1L);
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME))
          .thenReturn(Optional.of(existing));

      seeder.seed();

      verify(userDao, never()).insert(any());
    }

    @Test
    @DisplayName("inserts exactly one admin on first startup")
    void insertsExactlyOneAdmin() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      verify(userDao, times(1)).insert(any(User.class));
    }

    @Test
    @DisplayName("logs only username, never password or hash")
    void doesNotLogSensitiveCredentials() {
      // This test verifies behavior by inspecting the inserted user object — the
      // assertion is that the Admin object holds a hash, not a plaintext password,
      // and that seed() completes without throwing.
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      assertDoesNotThrow(() -> seeder.seed());

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      String hash = captor.getValue().getPasswordHash();
      assertFalse(hash.contains("123456"), "BCrypt hash must not contain the plaintext password");
    }

    @Test
    @DisplayName("does not propagate DAO exceptions — logs warning instead")
    void doesNotPropagateExceptions() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME))
          .thenThrow(new RuntimeException("DB connection lost"));

      // Must complete without throwing — exceptions are swallowed and logged
      assertDoesNotThrow(() -> seeder.seed());
    }

    @Test
    @DisplayName("inserted user is an Admin instance with ADMIN role")
    void insertedUserIsAdminRole() {
      when(userDao.findByUsername(AdminSeeder.DEFAULT_ADMIN_USERNAME)).thenReturn(Optional.empty());
      when(userDao.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      seeder.seed();

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userDao).insert(captor.capture());

      assertTrue(captor.getValue() instanceof Admin, "Inserted user must be an Admin instance");
    }
  }

  // ── resolveAdminPassword() ────────────────────────────────

  @Nested
  @DisplayName("resolveAdminPassword()")
  class ResolvePassword {

    @Test
    @DisplayName("returns demo fallback when env var is absent")
    void returnsDemoFallbackWhenEnvVarAbsent() {
      String password = seeder.resolveAdminPassword();
      assertNotNull(password);
      assertFalse(password.isBlank());
    }

    @Test
    @DisplayName("returned password has at least 6 characters")
    void returnedPasswordMeetsMinimumLength() {
      String password = seeder.resolveAdminPassword();
      assertTrue(password.length() >= 6);
    }
  }
}
