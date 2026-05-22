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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JWT middleware — route classification and token validation")
class JwtMiddlewareTest {

  @Mock private UserDao userDao;
  @Mock private Context ctx;

  @AfterEach
  void tearDown() {
    JwtMiddleware.configure(null);
  }

  // ── Public routes — no auth required ────────────────────

  @Nested
  @DisplayName("Public routes")
  class PublicRoutes {

    @Test
    @DisplayName("POST /api/auth/login passes through without any token check")
    void loginIsPublic() {
      when(ctx.path()).thenReturn("/api/auth/login");
      when(ctx.method()).thenReturn(HandlerType.POST);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("POST /api/auth/register passes through without any token check")
    void registerIsPublic() {
      when(ctx.path()).thenReturn("/api/auth/register");
      when(ctx.method()).thenReturn(HandlerType.POST);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password passes through without any token check")
    void forgotPasswordIsPublic() {
      when(ctx.path()).thenReturn("/api/auth/forgot-password");
      when(ctx.method()).thenReturn(HandlerType.POST);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("GET /api/health passes through without any token check")
    void healthIsPublic() {
      when(ctx.path()).thenReturn("/api/health");
      when(ctx.method()).thenReturn(HandlerType.GET);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }
  }

  // ── Semi-public GET routes ───────────────────────────────

  @Nested
  @DisplayName("Semi-public GET routes")
  class SemiPublicRoutes {

    @Test
    @DisplayName("GET /api/items without Authorization header passes through")
    void getItemsWithoutTokenPassesThrough() {
      when(ctx.path()).thenReturn("/api/items");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn(null);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("GET /api/auctions without Authorization header passes through")
    void getAuctionsWithoutTokenPassesThrough() {
      when(ctx.path()).thenReturn("/api/auctions");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn(null);

      assertDoesNotThrow(() -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("GET /api/auctions with valid Bearer token attaches user claims")
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
    @DisplayName("GET /api/items with invalid Bearer token passes through silently")
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
  @DisplayName("Protected routes")
  class ProtectedRoutes {

    @Test
    @DisplayName("Missing Authorization header throws UnauthorizedException")
    void missingHeaderThrows() {
      when(ctx.path()).thenReturn("/api/users/me");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn(null);

      assertThrows(UnauthorizedException.class, () -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("Non-Bearer scheme throws UnauthorizedException")
    void nonBearerSchemeThrows() {
      when(ctx.path()).thenReturn("/api/users/me");
      when(ctx.method()).thenReturn(HandlerType.GET);
      when(ctx.header("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

      assertThrows(UnauthorizedException.class, () -> JwtMiddleware.handle(ctx));
    }

    @Test
    @DisplayName("Protected request rejects token issued before password change")
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
    @DisplayName("Protected request accepts current tokenVersion and attaches claims")
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
    @DisplayName("Token for non-existent user throws UnauthorizedException")
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
