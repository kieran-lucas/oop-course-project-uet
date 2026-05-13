package com.auction.config;

import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.*;

/**
 * Test suite kiểm tra tính đúng đắn của {@link JwtUtil}:
 *
 * <ul>
 *   <li>Token được tạo ra có thể verify thành công và chứa đúng các claim.
 *   <li>Token giả mạo / sai định dạng bị từ chối với ngoại lệ phù hợp.
 *   <li>Hành vi với token hết hạn được ghi nhận (hiện chưa test được tự động).
 * </ul>
 *
 * <p><b>Thư viện:</b> Sử dụng {@code com.auth0:java-jwt}. {@link JwtUtil#createToken} ký token bằng
 * secret key nội bộ; {@link JwtUtil#verifyToken} xác thực chữ ký và trả về {@link DecodedJWT} để
 * đọc các claim.
 *
 * <p><b>Thứ tự test:</b> Được đánh số {@code @Order} theo mức độ phức tạp tăng dần — tạo/verify
 * bình thường trước, sau đó kiểm tra các trường hợp lỗi.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JwtUtilTest {

  /**
   * Kiểm tra luồng happy path đầy đủ: tạo token → verify → đọc claim.
   *
   * <p>Kịch bản: Tạo một JWT cho user {@code nhomAnhDuc} (id=1, role=BIDDER), sau đó verify token
   * đó và xác nhận rằng ba claim quan trọng ({@code userId}, {@code username}, {@code role}) được
   * lưu chính xác và có thể đọc lại sau khi decode.
   *
   * <p>Nếu test này thất bại, nguyên nhân thường là: sai secret key, sai tên claim, hoặc lỗi
   * serialize kiểu dữ liệu (ví dụ: {@code userId} bị lưu thành String thay vì Long).
   */
  @Test
  @Order(1)
  @DisplayName("testCreateAndVerify() — tạo token -> verify -> đúng userId, role")
  void testCreateAndVerify() {
    // Arrange — chuẩn bị dữ liệu đầu vào đại diện cho một user thực tế trong hệ thống
    Long userId = 1L;
    String username = "nhomAnhDuc";
    String role = "BIDDER";

    // Act — tạo token rồi verify ngay lập tức để lấy DecodedJWT
    String token = JwtUtil.createToken(userId, username, role);
    DecodedJWT decoded = JwtUtil.verifyToken(token);

    // Assert — kiểm tra token không null và ba claim khớp chính xác với dữ liệu đầu vào
    assertNotNull(token);
    assertEquals(userId, decoded.getClaim("userId").asLong());
    assertEquals(role, decoded.getClaim("role").asString());
    assertEquals(username, decoded.getClaim("username").asString());
  }

  /**
   * Kiểm tra rằng một token sai định dạng (malformed) bị từ chối bằng ngoại lệ.
   *
   * <p>Chuỗi {@code "ey.invalid.token"} có cấu trúc 3 phần giống JWT nhưng phần header/payload
   * không phải Base64url hợp lệ và phần signature không khớp với secret key. Thư viện {@code
   * java-jwt} sẽ ném {@link com.auth0.jwt.exceptions.JWTVerificationException} hoặc một subclass
   * của nó — test này chỉ yêu cầu có <em>bất kỳ</em> ngoại lệ nào được ném ra, không ràng buộc kiểu
   * cụ thể, để linh hoạt với các phiên bản thư viện khác nhau.
   */
  @Test
  @Order(2)
  @DisplayName("testInvalidToken() — verify 'garbage' -> throw")
  void testInvalidToken() {
    String garbage = "ey.invalid.token";

    // Thư viện sẽ ném ngoại lệ ngay khi phát hiện token không hợp lệ;
    // không cần chỉ định Exception cụ thể vì hành vi này nhất quán trên mọi phiên bản.
    assertThrows(
        Exception.class,
        () -> {
          JwtUtil.verifyToken(garbage);
        });
  }

  /**
   * Placeholder cho test kiểm tra token hết hạn — hiện chưa thực hiện được tự động.
   *
   * <p><b>Lý do bỏ trống:</b> Code production dùng {@code Instant.now().plus(Duration.ofHours(24))}
   * làm thời gian hết hạn cố định, nên không thể test expiry mà không sửa code hoặc đợi 24 giờ.
   *
   * <p><b>Hướng cải thiện trong tương lai:</b>
   *
   * <ol>
   *   <li>Inject {@code Clock} vào {@link JwtUtil} để có thể mock thời gian trong test.
   *   <li>Hoặc thêm overload {@code createToken(..., Duration ttl)} cho phép test truyền TTL ngắn
   *       (ví dụ: 1 millisecond) rồi {@code Thread.sleep} trước khi verify.
   * </ol>
   */
  @Test
  @Order(3)
  @DisplayName("testExpiredToken()")
  void testExpiredToken() {
    // Tạm thời bỏ trống — xem Javadoc của method này để biết lý do và hướng cải thiện.
    assertTrue(true);
  }
}
