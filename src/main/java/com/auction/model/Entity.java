package com.auction.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lớp trừu tượng gốc cho mọi entity trong hệ thống.
 *
 * <p>Mọi đối tượng lưu trong database đều có id và thời gian tạo.
 * Thay vì viết lại 2 field này trong User, Item, Auction, BidTransaction,
 * ta viết 1 lần ở đây → tất cả kế thừa.
 *
 * <p>Đây là ABSTRACTION trong OOP: Entity định nghĩa "mọi thứ trong hệ thống
 * đều có id và createdAt", nhưng không nói cụ thể đó là User hay Item.
 */
public abstract class Entity {

  // === Fields ===
  // private = ENCAPSULATION: bên ngoài không truy cập trực tiếp,
  // phải dùng getId() / setId()
  private Long id;
  private LocalDateTime createdAt;

  // === Constructors ===

  /** Constructor mặc định — dùng khi tạo object mới chưa có id (chưa lưu DB). */
  protected Entity() {
    this.createdAt = LocalDateTime.now();
  }

  /** Constructor đầy đủ — dùng khi đọc từ database (đã có id). */
  protected Entity(Long id, LocalDateTime createdAt) {
    this.id = id;
    this.createdAt = createdAt;
  }

  // === Getters & Setters ===
  // Đây là cửa ngõ duy nhất để đọc/ghi field private.
  // Setter cho id chỉ nên được gọi bởi DAO layer sau khi INSERT vào DB.

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
  // Hai entity "bằng nhau" nếu cùng class và cùng id.
  // Cần cho: tìm kiếm trong List, dùng làm key trong Map, so sánh trong test.
  // Checkstyle rule EqualsHashCode bắt buộc: override equals → phải override hashCode.

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
