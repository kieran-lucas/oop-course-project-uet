package com.auction.middleware;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT middleware token version")
class JwtMiddlewareTest {

  @Mock private UserDao userDao;
  @Mock private Context ctx;

  @AfterEach
  void tearDown() {
    JwtMiddleware.configure(null);
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

  private static User userWithTokenVersion(int tokenVersion) {
    User user = new Bidder("alice", "hash", "alice@example.com");
    user.setId(1L);
    user.setTokenVersion(tokenVersion);
    return user;
  }
}
