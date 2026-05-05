package com.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDateTime;

/**
 * Lớp trừu tượng đại diện cho một sản phẩm có thể tham gia đấu giá.
 *
 * <p>Hệ thống hỗ trợ ba loại sản phẩm: {@link Electronics}, {@link Art}, và {@link Vehicle}.
 * Các thuộc tính dùng chung (tên sản phẩm, mô tả, người bán) được đặt tại lớp này; những
 * thuộc tính đặc thù — ví dụ {@code brand} cho Electronics, {@code artist} cho Art, hay
 * {@code year} cho Vehicle — sẽ được khai báo tại lớp con tương ứng.
 *
 * <p>Tương tự lớp {@code User}, {@code Item} cung cấp một abstract method
 * {@link #getCategory()} để tận dụng polymorphism khi xác định loại sản phẩm.
 *
 * <p>Các annotation Jackson ở đầu lớp giúp deserialize JSON về đúng subclass dựa vào
 * trường {@code category}.
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
    private Long sellerId; // khóa ngoại tham chiếu đến bảng users

    /** Constructor mặc định — phục vụ framework/JDBI khi tạo object. */
    protected Item() {}

    /**
     * Khởi tạo một sản phẩm mới chưa được lưu vào DB.
     *
     * @param name tên sản phẩm
     * @param description mô tả sản phẩm
     * @param sellerId ID của người bán
     */
    protected Item(String name, String description, Long sellerId) {
        super();
        this.name = name;
        this.description = description;
        this.sellerId = sellerId;
    }

    /**
     * Khởi tạo một sản phẩm từ bản ghi đã tồn tại trong DB.
     *
     * @param id định danh sản phẩm
     * @param name tên sản phẩm
     * @param description mô tả sản phẩm
     * @param sellerId ID của người bán
     * @param createdAt thời điểm bản ghi được tạo
     */
    protected Item(Long id, String name, String description, Long sellerId, LocalDateTime createdAt) {
        super(id, createdAt);
        this.name = name;
        this.description = description;
        this.sellerId = sellerId;
    }

    /**
     * Trả về loại sản phẩm dưới dạng chuỗi: {@code "ELECTRONICS"}, {@code "ART"}, hoặc
     * {@code "VEHICLE"}.
     *
     * <p>POLYMORPHISM: mỗi lớp con override method này. {@code ItemFactory} dựa vào giá trị
     * trả về để quyết định khởi tạo subclass nào, và cột {@code category} trong DB cũng lưu
     * chính giá trị này.
     *
     * @return chuỗi đại diện cho loại sản phẩm
     */
    public abstract String getCategory();

    // === Getters & Setters ===

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    @Override
    public String toString() {
        return getCategory() + "{name='" + name + "', seller=" + sellerId + "}";
    }
}
