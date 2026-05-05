package com.auction.model;

import java.time.LocalDateTime;

/**
 * Phương tiện giao thông — sản phẩm đấu giá có thêm thông tin năm sản xuất.
 *
 * <p>Ví dụ điển hình: chiếc "Toyota Camry 2022" có {@code year = 2022}. Năm sản xuất là
 * yếu tố ảnh hưởng đáng kể đến giá trị thị trường của một phương tiện, nhưng lại không
 * có ý nghĩa với các loại sản phẩm khác như {@link Art} hay {@link Electronics}.
 */
public class Vehicle extends Item {

    private int year;

    /** Constructor mặc định — phục vụ framework/JDBI khi tạo object. */
    public Vehicle() {}

    /**
     * Khởi tạo một phương tiện mới chưa được lưu vào DB.
     *
     * @param name tên sản phẩm (ví dụ: "Toyota Camry")
     * @param description mô tả chi tiết
     * @param sellerId ID của người bán
     * @param year năm sản xuất
     */
    public Vehicle(String name, String description, Long sellerId, int year) {
        super(name, description, sellerId);
        this.year = year;
    }

    /**
     * Khởi tạo một phương tiện từ bản ghi đã tồn tại trong DB.
     *
     * @param id định danh sản phẩm
     * @param name tên sản phẩm
     * @param description mô tả chi tiết
     * @param sellerId ID của người bán
     * @param year năm sản xuất
     * @param createdAt thời điểm bản ghi được tạo
     */
    public Vehicle(
        Long id, String name, String description, Long sellerId, int year, LocalDateTime createdAt) {
        super(id, name, description, sellerId, createdAt);
        this.year = year;
    }

    @Override
    public String getCategory() {
        return "VEHICLE";
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}
