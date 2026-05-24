package com.auction.middleware;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Bidder;
import com.auction.model.User;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit test kiểm tra logic phân loại route và xác thực token của {@link JwtMiddleware}.
 *
 * <p>Middleware chia route thành ba nhóm: <em>public</em> (không cần token), <em>semi-public</em>
 * (GET với token tùy chọn — nếu có token hợp lệ thì gắn claims, nếu không thì đi qua), và
 * <em>protected</em> (bắt buộc phải có token hợp lệ và đúng {@code token_version}).
 *
 * <p>Cơ chế {@code token_version} cho phép vô hiệu hóa tất cả token cũ khi user đổi mật khẩu: token
 * với {@code version} nhỏ hơn giá trị hiện tại trong DB sẽ bị từ chối.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JWT middleware — phân loại route và xác thực token")
class JwtMiddlewareTest {

  @Mock private UserDao userDao;
  @Mock private Context ctx;

  @AfterEach
  void tearDown() {
    JwtMiddleware.configure(null);
  }

  // ── Public routes — no auth required ────────────────────

  @Nested
  @DisplayName("Route công khai")
  class PublicRoutes {

    @Test
    @DisplayName("POST /api/auth/login không cần token — được đi qua")
    void loginIsPublic() {
      when(ctx.path()).thenReturn("/api/auth/login");
      when(ctx.method()).thenReturn(HandlerType.POST);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("POST /api/auth/register không cần token — được đi qua")
    void registerIsPublic() {
      when(ctx.path()).thenReturn("/api/auth/register");
      when(ctx.method()).thenReturn(HandlerType.POST);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password không cần token — được đi qua")
    void forgotPasswordIsPublic() {
      when(ctx.path()).thenReturn("/api/auth/forgot-password");
      when(ctx.method()).thenReturn(HandlerType.POST);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("GET /api/health không cần token — được đi qua")
    void healthIsPublic() {
      when(ctx.path()).thenReturn("/api/health");
      when(ctx.method()).thenReturn(HandlerType.GET);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }
  }

  // ── Semi-public GET routes ───────────────────────────────

  @Nested
  @DisplayName("Route GET nửa công khai")
  class SemiPublicRoutes {

    @Test
    @DisplayName("GET /api/items không có header Authorization — được đi qua")
    void getItemsWithoutTokenPassesThrough() {
      when(ctx.path()).thenReturn("/api/items");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn(null);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("GET /api/auctions không có header Authorization — được đi qua")
    void getAuctionsWithoutTokenPassesThrough() {
      when(ctx.path()).thenReturn("/api/auctions");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn(null);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("GET /api/auctions với token hợp lệ — gắn thông tin user vào context")
    void getAuctionsWithValidTokenAttachesClaims() {
      User user = userWithTokenVersion(0);
      String token = JwtUtil.createToken(1L, "alice", "BIDDER", 0);
      JwtMiddleware.configure(userDao);
      when(ctx.path()).thenReturn("/api/auctions");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn("Bearer " + token);
      when(userDao.findById(1L)).thenReturn(Optional.of(user));

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));

      verify(ctx).attribute("userId", 1L);
      verify(ctx).attribute("username", "alice");
      verify(ctx).attribute("role", "BIDDER");
    }

    @Test
    @DisplayName("GET /api/items với token không hợp lệ — đi qua silently, không throw")
    void getItemsWithInvalidTokenPassesThroughSilently() {
      when(ctx.path()).thenReturn("/api/items");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn("Bearer invalid.jwt.token");

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));

      verify(ctx).attribute("userId", null);
    }
  }

  // ── Protected routes — token required ───────────────────

  @Nested
  @DisplayName("Route được bảo vệ")
  class ProtectedRoutes {

    @Test
    @DisplayName("Thiếu header Authorization — ném UnauthorizedException")
    void missingHeaderThrows() {
      when(ctx.path()).thenReturn("/api/users/me");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn(null);

      assertThrows(UnauthorizedException.class, () -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("Scheme không phải Bearer — ném UnauthorizedException")
    void nonBearerSchemeThrows() {
      when(ctx.path()).thenReturn("/api/users/me");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

      assertThrows(UnauthorizedException.class, () -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("Request protected từ chối token phát hành trước khi đổi mật khẩu")
    void rejectsOldTokenVersion() {
      User currentUser = userWithTokenVersion(2);
      String oldToken = JwtUtil.createToken(1L, "alice", "BIDDER", 1);
      JwtMiddleware.configure(userDao);
      when(ctx.path()).thenReturn("/api/users/me");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn("Bearer " + oldToken);
      when(userDao.findById(1L)).thenReturn(Optional.of(currentUser));

      assertThrows(UnauthorizedException.class, () -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("Request protected chấp nhận tokenVersion hiện tại và gắn claims")
    void acceptsCurrentTokenVersion() {
      User currentUser = userWithTokenVersion(2);
      String currentToken = JwtUtil.createToken(1L, "alice", "BIDDER", 2);
      JwtMiddleware.configure(userDao);
      when(ctx.path()).thenReturn("/api/users/me");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn("Bearer " + currentToken);
      when(userDao.findById(1L)).thenReturn(Optional.of(currentUser));

      JwtMiddleware.handle(ctx);

      verify(ctx).attribute("userId", 1L);
      verify(ctx).attribute("username", "alice");
      verify(ctx).attribute("role", "BIDDER");
    }

    @Test
    @DisplayName("Token của user không tồn tại — ném UnauthorizedException")
    void tokenForDeletedUserThrows() {
      String token = JwtUtil.createToken(99L, "ghost", "BIDDER", 0);
      JwtMiddleware.configure(userDao);
      when(ctx.path()).thenReturn("/api/users/me");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn("Bearer " + token);
      when(userDao.findById(99L)).thenReturn(Optional.empty());

      assertThrows(UnauthorizedException.class, () -> JwtMiddleware.handle(ctx));
    }
  }

  private static User userWithTokenVersion(int tokenVersion) {
    User user = new Bidder("alice", "hash", "alice@example.com");
    user.setId(1L);
    user.setTokenVersion(tokenVersion);
    return user;
  }
}
