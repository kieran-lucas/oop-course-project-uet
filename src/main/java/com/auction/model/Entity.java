package com.auction.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lớp trừu tượng gốc cho mọi entity trong hệ thống đấu giá
 *
 * <p>Mọi đối tượng được lưu trong cơ sở dữ liệu đều có hai thuộc tính chung: định danh duy nhất
 * ({@code id}) và thời điểm tạo bản ghi ({@code createdAt}). Thay vì lặp lại hai trường này ở các
 * lớp như {@code User}, {@code Item}, {@code Auction}, hay {@code BidTransaction}, ta khai báo
 * chúng tại đây và để các lớp con kế thừa
 *
 * <p>Đây là một ví dụ điển hình của ABSTRACTION trong OOP: lớp {@code Entity} mô tả tính chất chung
 * "mọi entity đều có id và createdAt" mà không gắn với một loại cụ thể nào.
 */
public abstract class Entity {

  // ENCAPSULATION: các field được khai báo private để ngăn truy cập trực tiếp
  // từ bên ngoài; mọi thao tác đọc/ghi phải đi qua getter/setter
  private Long id;
  private LocalDateTime createdAt;

  // === Constructors ===

  /**
   * Constructor mặc định — sử dụng khi khởi tạo một object mới chưa được lưu vào DB
   *
   * <p>{@code createdAt} được gán bằng thời điểm hiện tại; {@code id} sẽ được DAO gán sau khi
   * INSERT thành công.
   */
  protected Entity() {
    this.createdAt = LocalDateTime.now();
  }

  /**
   * Constructor đầy đủ — sử dụng khi tái tạo entity từ một bản ghi đã tồn tại trong DB
   *
   * @param id định danh duy nhất của entity
   * @param createdAt thời điểm bản ghi được tạo
   */
  protected Entity(Long id, LocalDateTime createdAt) {
    this.id = id;
    this.createdAt = createdAt;
  }

  // === Getters & Setters ===
  // Đây là cửa ngõ duy nhất để đọc/ghi các field private
  // Lưu ý: setId() chỉ nên được gọi từ DAO layer ngay sau khi INSERT để gán ID
  // do database sinh ra; không nên gọi từ business logic

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  // === equals & hashCode ===
  // Hai entity được coi là "bằng nhau" khi cùng class cụ thể và cùng id.
  // Việc override hai method này là cần thiết cho:
  //   - tìm kiếm trong các Collection (List, Set);
  //   - sử dụng entity làm key trong Map;
  //   - các phép so sánh trong unit test.
  // Checkstyle (rule EqualsHashCode) cũng yêu cầu: đã override equals thì
  // bắt buộc override hashCode.

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Entity entity = (Entity) o;
    return Objects.equals(id, entity.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
