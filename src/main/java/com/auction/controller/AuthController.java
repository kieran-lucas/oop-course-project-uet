package com.auction.controller;

import com.auction.dto.LoginRequest;
import com.auction.dto.RegisterRequest;
import com.auction.service.UserService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller xử lý các endpoint xác thực người dùng: đăng ký và đăng nhập.
 *
 * <p>Đây là các endpoint duy nhất trong hệ thống KHÔNG yêu cầu JWT token —
 * vì đây là nơi người dùng lần đầu tiên nhận token. Middleware {@code JwtMiddleware}
 * được cấu hình trong {@link com.auction.App} để bỏ qua tất cả route bắt đầu bằng
 * {@code /api/auth/}.
 *
 * <p>Danh sách endpoints:
 * <ul>
 *   <li>{@code POST /api/auth/register} — Tạo tài khoản mới, trả về JWT token.</li>
 *   <li>{@code POST /api/auth/login} — Đăng nhập, trả về JWT token.</li>
 * </ul>
 *
 * <p>Cách sử dụng (đăng ký route trong {@link com.auction.App}):
 * <pre>
 *   AuthController.register(app, userService);
 * </pre>
 *
 * @see com.auction.service.UserService
 * @see com.auction.config.JwtUtil
 */
public class AuthController {

    /** Logger ghi lại các sự kiện đăng nhập/đăng ký. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    /** Hàm khởi tạo private — lớp này chỉ dùng static methods, không cần instance. */
    private AuthController() {}

    /**
     * Đăng ký tất cả route xác thực vào Javalin instance.
     *
     * <p>Phương thức này được gọi một lần duy nhất trong {@code App.main()} sau khi
     * {@code UserService} đã được khởi tạo với đầy đủ dependency.
     *
     * @param app         Javalin instance đang chạy
     * @param userService service xử lý logic đăng ký, đăng nhập, mã hóa mật khẩu
     */
    public static void register(Javalin app, UserService userService) {
        app.post("/api/auth/register", ctx -> handleRegister(ctx, userService));
        app.post("/api/auth/login", ctx -> handleLogin(ctx, userService));
        LOGGER.info("Đã đăng ký AuthController: POST /api/auth/register, POST /api/auth/login");
    }

    /**
     * Xử lý yêu cầu đăng ký tài khoản mới.
     *
     * <p>Luồng xử lý:
     * <ol>
     *   <li>Parse JSON body thành {@link RegisterRequest}.</li>
     *   <li>Gọi {@code UserService.register()} — validate dữ liệu, mã hóa mật khẩu BCrypt,
     *       kiểm tra username trùng, tạo đúng subclass (Bidder/Seller/Admin), lưu DB.</li>
     *   <li>Gọi {@code UserService.login()} để tạo JWT token ngay sau khi đăng ký
     *       (người dùng không cần đăng nhập lại).</li>
     *   <li>Trả về JSON: {@code {"token": "eyJ...", "role": "BIDDER"}}.</li>
     * </ol>
     *
     * <p>Các lỗi có thể xảy ra (được xử lý bởi exception handler trong {@code App.java}):
     * <ul>
     *   <li>{@code DuplicateException} (409) — username đã tồn tại.</li>
     *   <li>{@code IllegalArgumentException} (400) — dữ liệu không hợp lệ.</li>
     * </ul>
     *
     * <p>Ví dụ request body:
     * <pre>
     *   {
     *     "username": "alice",
     *     "password": "matkhau123",
     *     "email": "alice@example.com",
     *     "role": "BIDDER"
     *   }
     * </pre>
     *
     * @param ctx         Javalin context chứa HTTP request/response
     * @param userService service thực thi logic đăng ký
     */
    private static void handleRegister(Context ctx, UserService userService) {
        // Parse JSON body thành DTO
        RegisterRequest request = ctx.bodyAsClass(RegisterRequest.class);

        // Gọi service: validate + BCrypt hash + insert DB
        userService.register(request);

        // Tạo JWT token ngay sau khi đăng ký (tiện cho client: đăng ký xong → dùng luôn)
        LoginRequest loginRequest = new LoginRequest(request.getUsername(), request.getPassword());
        String token = userService.login(loginRequest);
        long userId = com.auction.config.JwtUtil.verifyToken(token).getClaim("userId").asLong();

        LOGGER.info("Đăng ký thành công: username={}, role={}",
            request.getUsername(), request.getRole());

        // Trả về token + role + userId cho client biết để chuyển màn hình phù hợp
        ctx.status(201).json(Map.of(
            "token", token,
            "role", request.getRole(),
            "username", request.getUsername(),
            "userId", userId
        ));
    }

    /**
     * Xử lý yêu cầu đăng nhập.
     *
     * <p>Luồng xử lý:
     * <ol>
     *   <li>Parse JSON body thành {@link LoginRequest}.</li>
     *   <li>Gọi {@code UserService.login()} — tìm user theo username, verify mật khẩu
     *       với BCrypt, tạo JWT token chứa {userId, username, role}.</li>
     *   <li>Trả về JSON: {@code {"token": "eyJ...", "role": "BIDDER", "username": "alice"}}.</li>
     * </ol>
     *
     * <p>Các lỗi có thể xảy ra:
     * <ul>
     *   <li>{@code NotFoundException} (404) — username không tồn tại.</li>
     *   <li>{@code UnauthorizedException} (401) — mật khẩu sai.</li>
     * </ul>
     *
     * <p>Ví dụ request body:
     * <pre>
     *   {
     *     "username": "alice",
     *     "password": "matkhau123"
     *   }
     * </pre>
     *
     * @param ctx         Javalin context chứa HTTP request/response
     * @param userService service thực thi logic đăng nhập và tạo JWT
     */
    private static void handleLogin(Context ctx, UserService userService) {
        // Parse JSON body thành DTO
        LoginRequest request = ctx.bodyAsClass(LoginRequest.class);

        // Gọi service: tìm user → verify BCrypt → tạo JWT
        String token = userService.login(request);

        // Lấy role và userId từ token để client biết chuyển màn hình nào
        String role = userService.getRoleByUsername(request.getUsername());
        String username = request.getUsername();
        long userId = com.auction.config.JwtUtil.verifyToken(token).getClaim("userId").asLong();

        LOGGER.info("Đăng nhập thành công: username={}", username);

        ctx.status(200).json(Map.of(
            "token", token,
            "role", role,
            "username", username,
            "userId", userId
        ));
    }
}
