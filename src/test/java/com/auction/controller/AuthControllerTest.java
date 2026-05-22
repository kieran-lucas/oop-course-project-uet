package com.auction.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.auction.config.JwtUtil;
import com.auction.dto.ForgotPasswordRequest;
import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.service.PasswordResetService;
import com.auction.service.UserService;
import io.javalin.http.Context;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthController — handler method coverage")
class AuthControllerTest {

  @Mock(answer = Answers.RETURNS_SELF)
  private Context ctx;

  @Mock private UserService userService;

  @Mock private PasswordResetService resetService;

  private static void invokeRegister(Context ctx, UserService svc) throws Exception {
    Method m =
        AuthController.class.getDeclaredMethod("handleRegister", Context.class, UserService.class);
    m.setAccessible(true);
    try {
      m.invoke(null, ctx, svc);
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      if (cause instanceof RuntimeException re) throw re;
      throw ite;
    }
  }

  private static void invokeLogin(Context ctx, UserService svc) throws Exception {
    Method m =
        AuthController.class.getDeclaredMethod("handleLogin", Context.class, UserService.class);
    m.setAccessible(true);
    try {
      m.invoke(null, ctx, svc);
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      if (cause instanceof RuntimeException re) throw re;
      throw ite;
    }
  }

  private static void invokeForgotPassword(Context ctx, PasswordResetService svc) throws Exception {
    Method m =
        AuthController.class.getDeclaredMethod(
            "handleForgotPassword", Context.class, PasswordResetService.class);
    m.setAccessible(true);
    try {
      m.invoke(null, ctx, svc);
    } catch (InvocationTargetException ite) {
      Throwable cause = ite.getCause();
      if (cause instanceof RuntimeException re) throw re;
      throw ite;
    }
  }

  // ── handleRegister() ──────────────────────────────────────

  @Nested
  @DisplayName("handleRegister()")
  class HandleRegister {

    @Test
    @DisplayName("successful registration returns 201 with token and user info")
    void successfulRegistration() throws Exception {
      RegisterRequest req = new RegisterRequest("alice", "pass123", "alice@ex.com", "BIDDER");
      User newUser = new Bidder("alice", "hash", "alice@ex.com");
      newUser.setId(1L);
      newUser.setTokenVersion(0);

      when(ctx.bodyAsClass(RegisterRequest.class)).thenReturn(req);
      when(userService.register(req)).thenReturn(newUser);

      invokeRegister(ctx, userService);

      verify(userService).register(req);
      verify(ctx).status(201);
      verify(ctx).json(any());
    }
  }

  // ── handleLogin() ─────────────────────────────────────────

  @Nested
  @DisplayName("handleLogin()")
  class HandleLogin {

    @Test
    @DisplayName("successful login returns 200 with token and role")
    void successfulLogin() throws Exception {
      LoginRequest req = new LoginRequest("alice", "pass123");
      String token = JwtUtil.createToken(1L, "alice", "BIDDER", 0);

      when(ctx.bodyAsClass(LoginRequest.class)).thenReturn(req);
      when(userService.login(req)).thenReturn(token);
      when(userService.getRoleByUsername("alice")).thenReturn("BIDDER");

      invokeLogin(ctx, userService);

      verify(userService).login(req);
      verify(userService).getRoleByUsername("alice");
      verify(ctx).status(200);
      verify(ctx).json(any());
    }
  }

  // ── handleForgotPassword() ────────────────────────────────

  @Nested
  @DisplayName("handleForgotPassword()")
  class HandleForgotPassword {

    @Test
    @DisplayName("valid email delegates to resetService and returns 200")
    void validEmailReturns200() throws Exception {
      ForgotPasswordRequest req = new ForgotPasswordRequest();
      req.setEmail("alice@ex.com");

      when(ctx.bodyAsClass(ForgotPasswordRequest.class)).thenReturn(req);

      invokeForgotPassword(ctx, resetService);

      verify(resetService).requestReset("alice@ex.com");
      verify(ctx).status(200);
    }

    @Test
    @DisplayName("null email returns 400 without calling resetService")
    void nullEmailReturns400() throws Exception {
      ForgotPasswordRequest req = new ForgotPasswordRequest();
      req.setEmail(null);

      when(ctx.bodyAsClass(ForgotPasswordRequest.class)).thenReturn(req);

      invokeForgotPassword(ctx, resetService);

      verify(resetService, never()).requestReset(any());
      verify(ctx).status(400);
    }

    @Test
    @DisplayName("blank email returns 400 without calling resetService")
    void blankEmailReturns400() throws Exception {
      ForgotPasswordRequest req = new ForgotPasswordRequest();
      req.setEmail("   ");

      when(ctx.bodyAsClass(ForgotPasswordRequest.class)).thenReturn(req);

      invokeForgotPassword(ctx, resetService);

      verify(resetService, never()).requestReset(any());
      verify(ctx).status(400);
    }
  }
}
