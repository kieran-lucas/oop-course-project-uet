package com.auction.middleware;

import com.auction.config.JwtUtil;
import com.auction.dao.UserDao;
import com.auction.exception.UnauthorizedException;
import com.auction.model.User;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;

/**
 * Middleware xử lý xác thực JWT cho toàn bộ ứng dụng.
 *
 * <p>Được đăng ký như một Javalin before-handler, tức là sẽ chạy trước mọi request. Logic phân
 * luồng gồm 3 tầng:
 *
 * <ol>
 *   <li>Route public hoàn toàn (login, register, ...) → bỏ qua xác thực.
 *   <li>GET /api/items và /api/auctions → public, nhưng vẫn parse token nếu có.
 *   <li>Còn lại → bắt buộc có token hợp lệ, ném {@link UnauthorizedException} nếu không.
 * </ol>
 */
public class JwtMiddleware {

  private static volatile UserDao userDao;

  public static void configure(UserDao configuredUserDao) {
    userDao = configuredUserDao;
  }

  /**
   * Entry point của middleware. Javalin gọi phương thức này cho mỗi HTTP request đến.
   *
   * @param ctx Javalin context chứa request/response và attribute bag dùng chung với các handler
   *     sau.
   * @throws UnauthorizedException nếu route yêu cầu xác thực nhưng token thiếu hoặc không hợp lệ.
   */
  public static void handle(Context ctx) {
    String path = ctx.path();
    String method = ctx.method().name(); // "GET", "POST", "PUT", "DELETE"

    // -------------------------------------------------------------------------
    // 1. Public routes - Bỏ qua hoàn toàn xác thực
    //    Các endpoint này không cần đăng nhập: ai cũng có thể gọi tự do.
    //    Thêm route mới vào đây nếu cần mở public hoàn toàn.
    // -------------------------------------------------------------------------
    if (path.equals("/api/auth/login")
        || path.equals("/api/auth/register")
        || path.equals("/api/auth/forgot-password")
        || path.equals("/api/health")) {
      return;
    }

    // -------------------------------------------------------------------------
    // 2. Semi-public routes: GET /api/items và GET /api/auctions (bao gồm sub-path
    //    như /api/auctions/:id/bids) cho phép truy cập không cần token.
    //
    //    Tuy nhiên, nếu client gửi kèm token hợp lệ, ta vẫn parse và gắn thông
    //    tin người dùng vào context. Điều này hữu ích khi:
    //      - Frontend cần biết user đang xem phiên đấu giá của chính mình không.
    //      - Các handler muốn tuỳ chỉnh response theo vai trò (role-based UI).
    //
    //    Token không hợp lệ ở tầng này KHÔNG bị từ chối — lỗi được bỏ qua âm thầm
    //    và request vẫn được xử lý như anonymous.
    // -------------------------------------------------------------------------
    if ("GET".equals(method)
        && (path.startsWith("/api/items") || path.startsWith("/api/auctions"))) {

      String authHeader = ctx.header("Authorization");

      // Chỉ thử parse khi header Authorization thực sự tồn tại và đúng scheme "Bearer"
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        try {
          String token = authHeader.substring(7); // Cắt bỏ prefix "Bearer "
          DecodedJWT jwt = JwtUtil.verifyToken(token);

          // Gắn thông tin người dùng vào attribute bag để các handler sau có thể đọc
          attachUserClaims(ctx, jwt);
        } catch (Exception e) {
          // Token lỗi (hết hạn, sai chữ ký, ...) nhưng đây là GET public
          // → không ném exception, chỉ đánh dấu userId = null (anonymous)
          ctx.attribute("userId", null);
        }
      }

      return; // Kết thúc xử lý middleware cho semi-public GET, tiếp tục tới handler
    }

    // -------------------------------------------------------------------------
    // 3. Protected routes - BẮT BUỘC phải có token hợp lệ.
    //    Mọi request không thuộc tầng 1 hoặc 2 đều rơi vào đây:
    //      POST/PUT/DELETE /api/items, /api/auctions, /api/users, v.v.
    // -------------------------------------------------------------------------

    String authHeader = ctx.header("Authorization");

    // Kiểm tra sự tồn tại và định dạng của header trước khi parse
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new UnauthorizedException("Thiếu hoặc sai định dạng token. Vui lòng đăng nhập.");
    }

    try {
      String token = authHeader.substring(7); // Cắt bỏ prefix "Bearer "

      // Xác thực chữ ký và thời hạn của token
      DecodedJWT jwt = JwtUtil.verifyToken(token);
      validateTokenVersion(jwt);

      // Đẩy thông tin người dùng vào attribute bag của Javalin context.
      // Các Controller ở downstream chỉ cần ctx.attribute("userId") để dùng,
      // không cần parse lại JWT lần nữa.
      ctx.attribute("userId", jwt.getClaim("userId").asLong());
      ctx.attribute("username", jwt.getClaim("username").asString());
      ctx.attribute("role", jwt.getClaim("role").asString()); // Dùng cho phân quyền RBAC

    } catch (Exception e) {
      // Các lỗi thường gặp: TokenExpiredException, SignatureVerificationException, ...
      // Bọc lại thành UnauthorizedException để GlobalExceptionHandler trả về HTTP 401.
      throw new UnauthorizedException("Token không hợp lệ hoặc đã hết hạn: " + e.getMessage());
    }
  }

  private static void attachUserClaims(Context ctx, DecodedJWT jwt) {
    validateTokenVersion(jwt);
    ctx.attribute("userId", jwt.getClaim("userId").asLong());
    ctx.attribute("username", jwt.getClaim("username").asString());
    ctx.attribute("role", jwt.getClaim("role").asString());
  }

  private static void validateTokenVersion(DecodedJWT jwt) {
    UserDao configuredUserDao = userDao;
    if (configuredUserDao == null) {
      return;
    }

    Long userId = jwt.getClaim("userId").asLong();
    if (userId == null) {
      throw new UnauthorizedException("Token is missing userId.");
    }

    User currentUser =
        configuredUserDao
            .findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Token user no longer exists."));
    int tokenVersion = extractTokenVersion(jwt);
    if (currentUser.getTokenVersion() != tokenVersion) {
      throw new UnauthorizedException("Token has been invalidated. Please log in again.");
    }
  }

  private static int extractTokenVersion(DecodedJWT jwt) {
    Claim claim = jwt.getClaim("tokenVersion");
    if (claim.isMissing() || claim.isNull()) {
      return 0;
    }
    Integer version = claim.asInt();
    return version != null ? version : 0;
  }
}
