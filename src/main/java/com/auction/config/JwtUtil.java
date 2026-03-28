package com.auction.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tiện ích tạo và xác thực JWT (JSON Web Token).
 *
 * <h3>JWT là gì?</h3>
 * <p>JWT là một chuỗi text dài (~200 ký tự) chứa thông tin user, được server "ký"
 * bằng secret key. Client giữ chuỗi này, gửi kèm mỗi request. Server verify
 * chữ ký → biết đây là ai → KHÔNG cần tra database.
 *
 * <h3>Cấu trúc JWT:</h3>
 * <pre>
 *   eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjQyLCJ1c2VybmFtZSI6InF1YW4ifQ.ABC123...
 *   ├── Header ────────────┤├── Payload (data) ──────────────────────────────┤├── Signature ┤
 *
 *   Payload khi decode:
 *   {
 *     "userId": 42,
 *     "username": "quan",
 *     "role": "BIDDER",
 *     "exp": 1735689600        ← hết hạn lúc nào (Unix timestamp)
 *   }
 * </pre>
 *
 * <h3>Luồng hoạt động:</h3>
 * <pre>
 *   1. Client gửi POST /api/auth/login { "username": "quan", "password": "123" }
 *   2. AuthController → UserService kiểm tra password (BCrypt)
 *   3. Đúng → JwtUtil.generateToken(userId=42, username="quan", role="BIDDER")
 *   4. Server trả về: { "token": "eyJhbGciOi..." }
 *   5. Client lưu token trong memory
 *   6. Mọi request sau, client gắn header: Authorization: Bearer eyJhbGciOi...
 *   7. Javalin middleware → JwtUtil.verifyToken(token) → biết userId=42, role=BIDDER
 *   8. Nếu token hết hạn hoặc bị sửa → verify thất bại → trả 401 Unauthorized
 * </pre>
 *
 * <h3>Tại sao dùng JWT thay vì Session?</h3>
 * <ul>
 *   <li>Stateless: server không lưu gì, restart không mất session
 *   <li>Hoạt động cho cả REST (header) lẫn WebSocket (query parameter)
 *   <li>Client là JavaFX desktop app, không có cookie → JWT phù hợp hơn session
 * </ul>
 *
 * <h3>Bảo mật:</h3>
 * <ul>
 *   <li>Secret key phải đủ dài và random — trong production đọc từ biến môi trường
 *   <li>Token có thời hạn (24 giờ) — hết hạn phải login lại
 *   <li>Nếu ai đó sửa 1 bit trong token → chữ ký sai → server từ chối
 * </ul>
 *
 * <h3>Liên kết với các file khác:</h3>
 * <ul>
 *   <li><b>AuthController.java</b>: gọi generateToken() khi login thành công
 *   <li><b>App.java</b>: đăng ký Javalin before-handler gọi verifyToken() cho mọi request
 *   <li><b>AuctionWebSocketHandler.java</b>: verify token khi WebSocket connect
 *   <li><b>build.gradle.kts</b>: dependency com.auth0:java-jwt:4.4.0
 * </ul>
 */
public class JwtUtil {

  private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

  // ============================================================================
  // Secret key — dùng để ký và verify token
  // ============================================================================
  // QUAN TRỌNG: trong production, PHẢI đọc từ biến môi trường, không hardcode.
  // Ở đây dùng giá trị mặc định cho development.
  // Nếu ai biết secret key, họ có thể tạo token giả mạo bất kỳ user nào.
  private static final String SECRET =
      System.getenv("JWT_SECRET") != null
          ? System.getenv("JWT_SECRET")
          : "auction-system-dev-secret-key-change-in-production";

  // Algorithm HMAC-SHA256: chuẩn phổ biến nhất cho JWT
  // HMAC = symmetric (cùng 1 key để ký và verify)
  // SHA256 = hàm hash 256-bit (đủ mạnh cho mục đích này)
  private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);

  // Thời hạn token: 24 giờ
  // Sau 24 giờ, token hết hạn → client phải login lại
  // Có thể điều chỉnh: ngắn hơn = an toàn hơn nhưng user phải login lại thường xuyên
  private static final long EXPIRATION_HOURS = 24;

  // Issuer: ai tạo token — ghi trong payload để verify
  // Khi verify, nếu issuer trong token khác "auction-system" → từ chối
  private static final String ISSUER = "auction-system";

  // JWTVerifier: object dùng đi dùng lại để verify token (thread-safe)
  // Tạo 1 lần, dùng cho mọi request — không tạo mới mỗi lần verify
  private static final JWTVerifier VERIFIER =
      JWT.require(ALGORITHM).withIssuer(ISSUER).build();

  /** Không cho phép tạo instance — class này chỉ có static methods. */
  private JwtUtil() {}

  /**
   * Tạo JWT token cho user sau khi login thành công.
   *
   * <p>Token chứa:
   * <ul>
   *   <li>userId: ID trong database (dùng để query user info sau này)
   *   <li>username: tên hiển thị
   *   <li>role: "BIDDER", "SELLER", hoặc "ADMIN" (dùng để kiểm tra quyền)
   *   <li>iss: issuer = "auction-system"
   *   <li>exp: thời điểm hết hạn (24 giờ sau khi tạo)
   * </ul>
   *
   * <p>Ví dụ sử dụng trong AuthController:
   * <pre>
   *   // Login thành công, user là Bidder có id=42
   *   String token = JwtUtil.generateToken(42L, "quan", "BIDDER");
   *   ctx.json(Map.of("token", token));
   * </pre>
   *
   * @param userId   ID user trong database
   * @param username tên đăng nhập
   * @param role     vai trò: "BIDDER", "SELLER", "ADMIN"
   * @return JWT token string (bắt đầu bằng "eyJ...")
   */
  public static String generateToken(Long userId, String username, String role) {
    Instant now = Instant.now();
    Instant expiry = now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS);

    String token =
        JWT.create()
            // Claim = 1 cặp key-value trong payload
            .withIssuer(ISSUER) // ai tạo token
            .withIssuedAt(now) // tạo lúc nào
            .withExpiresAt(expiry) // hết hạn lúc nào
            .withClaim("userId", userId) // ID trong database
            .withClaim("username", username) // tên hiển thị
            .withClaim("role", role) // vai trò (dùng kiểm tra quyền)
            .sign(ALGORITHM); // ký bằng secret key → tạo chữ ký

    logger.debug("Đã tạo JWT token cho user: {} (role={})", username, role);
    return token;
  }

  /**
   * Xác thực JWT token và trả về thông tin bên trong.
   *
   * <p>Quá trình verify:
   * <ol>
   *   <li>Decode token (tách Header, Payload, Signature)
   *   <li>Kiểm tra chữ ký: dùng secret key tính lại signature → so sánh
   *      → Nếu ai sửa 1 ký tự trong payload, signature sẽ sai → từ chối
   *   <li>Kiểm tra issuer: payload.iss == "auction-system"?
   *   <li>Kiểm tra thời hạn: payload.exp > thời gian hiện tại?
   *      → Token hết hạn → từ chối
   * </ol>
   *
   * <p>Ví dụ sử dụng trong Javalin middleware:
   * <pre>
   *   String token = ctx.header("Authorization").replace("Bearer ", "");
   *   DecodedJWT jwt = JwtUtil.verifyToken(token);
   *   if (jwt == null) {
   *       throw new UnauthorizedException("Token không hợp lệ");
   *   }
   *   Long userId = jwt.getClaim("userId").asLong();
   *   String role = jwt.getClaim("role").asString();
   * </pre>
   *
   * @param token JWT token string (không bao gồm prefix "Bearer ")
   * @return DecodedJWT chứa thông tin user, hoặc null nếu token không hợp lệ
   */
  public static DecodedJWT verifyToken(String token) {
    try {
      // verify() thực hiện tất cả bước kiểm tra:
      // chữ ký, issuer, thời hạn — nếu bất kỳ bước nào fail → throw exception
      return VERIFIER.verify(token);
    } catch (JWTVerificationException e) {
      // Token không hợp lệ: bị sửa, hết hạn, sai issuer, hoặc format sai
      // Log lý do để debug, nhưng KHÔNG trả lý do cho client (bảo mật)
      // Client chỉ biết "token không hợp lệ" → phải login lại
      logger.debug("JWT verification thất bại: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Trích xuất userId từ DecodedJWT.
   *
   * <p>Tiện ích ngắn gọn thay vì viết jwt.getClaim("userId").asLong() mỗi lần.
   *
   * @param jwt token đã decode (trả về từ verifyToken)
   * @return userId, hoặc null nếu claim không tồn tại
   */
  public static Long getUserId(DecodedJWT jwt) {
    return jwt.getClaim("userId").asLong();
  }

  /**
   * Trích xuất username từ DecodedJWT.
   *
   * @param jwt token đã decode
   * @return username, hoặc null nếu claim không tồn tại
   */
  public static String getUsername(DecodedJWT jwt) {
    return jwt.getClaim("username").asString();
  }

  /**
   * Trích xuất role từ DecodedJWT.
   *
   * <p>Dùng để kiểm tra quyền trong middleware:
   * <pre>
   *   String role = JwtUtil.getRole(jwt);
   *   if (!"SELLER".equals(role)) {
   *       throw new UnauthorizedException("Chỉ Seller mới được tạo sản phẩm");
   *   }
   * </pre>
   *
   * @param jwt token đã decode
   * @return role: "BIDDER", "SELLER", hoặc "ADMIN"
   */
  public static String getRole(DecodedJWT jwt) {
    return jwt.getClaim("role").asString();
  }
}
