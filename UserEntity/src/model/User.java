package model;
import java.time.LocalDateTime;
import java.util.Objects;

public abstract class User {
    private Long id;
    private String username;
    private String password;
    private Role role;
    private String email;
    private LocalDateTime createdAt;
    private boolean isActive;

    public enum Role {
        BIDDER,
        SELLER,
        ADMIN
    }

    protected User() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
    }

    private String validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " không được để trống.");
        }
        return value.trim();
    }

    protected User(String username, String password, Role role, String email) {
        this.username  = validateNotBlank(username, "Username");
        this.password  = validateNotBlank(password, "Password");
        this.email     = validateNotBlank(email, "Email");
        this.role      = Objects.requireNonNull(role, "Role không được null");
        this.createdAt = LocalDateTime.now();
        this.isActive  = true;
    }

    public abstract void printInfo();

    public abstract String getRoleDescription();

    public boolean login(String inputUsername, String inputPassword) {
        if (!isActive) {
            System.out.println("Tài khoản đã bị vô hiệu hóa.");
            return false;
        }
        boolean success = this.username.equals(inputUsername)
            && this.password.equals(inputPassword);
        if (success) {
            System.out.println("Đăng nhập thành công. Xin chào, " + username + "!");
        } else {
            System.out.println("Sai tên đăng nhập hoặc mật khẩu.");
        }
        return success;
    }

    public void logout() {
        System.out.println(username + " đã đăng xuất.");
        // TODO: invalidate session token ở đây
    }

    public boolean hasRole(Role requiredRole) {
        return this.role == requiredRole;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) {
        this.username = validateNotBlank(username, "Username");
    }

    public String getPassword() { return password; }
    public void setPassword(String password) {
        this.password = validateNotBlank(password, "Password");
    }

    public Role getRole() { return role; }
    // role không có setter — vai trò không được thay đổi sau khi tạo

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = validateNotBlank(email, "Email");
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    // createdAt không có setter — thời điểm tạo là bất biến

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return String.format("User{id=%d, username='%s', role=%s, active=%b}",
            id, username, role, isActive);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        if (id != null && other.id != null) return id.equals(other.id);
        return username.equals(other.username);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(username);
    }

}

