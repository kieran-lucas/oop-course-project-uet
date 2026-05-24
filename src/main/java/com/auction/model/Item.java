package com.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDateTime;

/**
 * Lớp cơ sở trừu tượng đại diện cho một sản phẩm đấu giá.
 *
 * <p>Chỉ các trường dùng chung cho mọi loại sản phẩm mới được đặt tại đây. Thông tin đặc thù theo
 * danh mục (brand, artist, year) nằm trong các lớp con:
 *
 * <ul>
 *   <li>{@link Electronics} — thêm trường {@code brand} (thương hiệu)
 *   <li>{@link Art} — thêm trường {@code artist} (nghệ sĩ)
 *   <li>{@link Vehicle} — thêm trường {@code year} (năm sản xuất)
 * </ul>
 *
 * <p>Thiết kế single-table inheritance: cả 3 loại được lưu trong bảng {@code items}, phân biệt bởi
 * cột {@code category}. Jackson dùng annotation {@code @JsonTypeInfo} và {@code @JsonSubTypes} để
 * tự động phân tích JSON về đúng subclass.
 *
 * <p>Vòng đời trạng thái sản phẩm:
 *
 * <pre>
 *   AVAILABLE → IN_AUCTION → SOLD
 *            ↘ REMOVED (xóa mềm bởi admin/seller)
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "category", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = Electronics.class, name = "ELECTRONICS"),
  @JsonSubTypes.Type(value = Art.class, name = "ART"),
  @JsonSubTypes.Type(value = Vehicle.class, name = "VEHICLE")
})
public abstract class Item extends Entity {

  private String name;
  private String description;
  private Long sellerId;

  /** Trạng thái sản phẩm: AVAILABLE, IN_AUCTION, SOLD, REMOVED. */
  private String status = "AVAILABLE";

  /** Constructor mặc định dành cho framework/Jackson — không dùng trực tiếp trong code. */
  protected Item() {
    super();
  }

  /**
   * Tạo sản phẩm mới trước khi lưu vào DB (chưa có id — id sẽ được DB sinh khi insert).
   *
   * @param name tên sản phẩm
   * @param description mô tả chi tiết
   * @param sellerId ID người bán sở hữu sản phẩm
   */
  protected Item(String name, String description, Long sellerId) {
    super();
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
  }

  /**
   * Tái tạo sản phẩm từ một hàng đã có trong DB (có id và createdAt). Dùng bởi {@link
   * com.auction.dao.ItemDao.ItemMapper} khi đọc từ ResultSet.
   *
   * @param id khóa chính từ DB
   * @param name tên sản phẩm
   * @param description mô tả chi tiết
   * @param sellerId ID người bán
   * @param status trạng thái hiện tại — mặc định AVAILABLE nếu DB trả về null
   * @param createdAt thời điểm tạo lấy từ DB
   */
  protected Item(
      Long id,
      String name,
      String description,
      Long sellerId,
      String status,
      LocalDateTime createdAt) {
    super(id, createdAt);
    this.name = name;
    this.description = description;
    this.sellerId = sellerId;
    this.status = status == null ? "AVAILABLE" : status;
  }

  /**
   * Trả về tên danh mục của loại sản phẩm này — ví dụ: "ELECTRONICS", "ART", "VEHICLE". Được dùng
   * làm discriminator trong DB và Jackson polymorphism.
   */
  public abstract String getCategory();

  /** Lấy tên sản phẩm. */
  public String getName() {
    return name;
  }

  /** Cập nhật tên sản phẩm. */
  public void setName(String name) {
    this.name = name;
  }

  /** Lấy mô tả chi tiết của sản phẩm. */
  public String getDescription() {
    return description;
  }

  /** Cập nhật mô tả chi tiết của sản phẩm. */
  public void setDescription(String description) {
    this.description = description;
  }

  /** Lấy ID người bán sở hữu sản phẩm. */
  public Long getSellerId() {
    return sellerId;
  }

  /** Cập nhật ID người bán sở hữu sản phẩm. */
  public void setSellerId(Long sellerId) {
    this.sellerId = sellerId;
  }

  /** Lấy trạng thái hiện tại của sản phẩm (AVAILABLE, IN_AUCTION, SOLD, REMOVED). */
  public String getStatus() {
    return status;
  }

  /** Cập nhật trạng thái của sản phẩm. */
  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return getCategory()
        + "{name='"
        + name
        + "', status='"
        + status
        + "', seller="
        + sellerId
        + "}";
  }
}
