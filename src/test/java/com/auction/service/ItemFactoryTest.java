package com.auction.pattern.factory;

import com.auction.dto.CreateItemRequest;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho ItemFactory — Factory Method Pattern.
 *
 * <p>Không cần kết nối database vì ItemFactory chỉ tạo object trong bộ nhớ.
 * Đây là "pure unit test" — nhanh, độc lập, không có side effect.
 *
 * <p>Mục tiêu kiểm tra:
 * <ul>
 *   <li>Factory tạo đúng subclass tương ứng với category</li>
 *   <li>Các thuộc tính riêng của từng subclass được gán đúng</li>
 *   <li>Category không hợp lệ ném đúng exception</li>
 *   <li>Các trường dùng chung (name, description, sellerId) được gán đúng</li>
 * </ul>
 */
@DisplayName("ItemFactory — Factory Method Pattern")
class ItemFactoryTest {

  /** sellerId giả dùng cho tất cả test case */
  private static final Long SELLER_ID = 42L;

  // ═══════════════════════════════════════════════════════════
  // Helper: tạo CreateItemRequest nhanh
  // ═══════════════════════════════════════════════════════════

  private CreateItemRequest buildRequest(String name, String desc, String category, String detail) {
    CreateItemRequest req = new CreateItemRequest();
    req.setName(name);
    req.setDescription(desc);
    req.setCategory(category);
    req.setCategoryDetail(detail);
    return req;
  }

  // ═══════════════════════════════════════════════════════════
  // Electronics
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Tạo Electronics")
  class CreateElectronics {

    @Test
    @DisplayName("Trả về đúng instance Electronics với brand chính xác")
    void testCreateElectronicsReturnsCorrectInstance() {
      CreateItemRequest req =
          buildRequest("Laptop Dell XPS 15", "Laptop cao cấp dành cho lập trình viên",
              "ELECTRONICS", "Dell");

      Item item = ItemFactory.create(req, SELLER_ID);

      assertInstanceOf(Electronics.class, item,
          "Factory phải trả về Electronics khi category = ELECTRONICS");

      Electronics electronics = (Electronics) item;
      assertEquals("Dell", electronics.getBrand(), "Brand phải được gán từ categoryDetail");
    }

    @Test
    @DisplayName("Các trường dùng chung được gán đúng cho Electronics")
    void testCreateElectronicsCommonFieldsCorrect() {
      CreateItemRequest req =
          buildRequest("iPhone 16 Pro", "Điện thoại Apple mới nhất", "ELECTRONICS", "Apple");

      Item item = ItemFactory.create(req, SELLER_ID);

      assertAll("Common fields của Electronics",
          () -> assertEquals("iPhone 16 Pro", item.getName()),
          () -> assertEquals("Điện thoại Apple mới nhất", item.getDescription()),
          () -> assertEquals(SELLER_ID, item.getSellerId()),
          () -> assertEquals("ELECTRONICS", item.getCategory())
      );
    }

    @Test
    @DisplayName("Category không phân biệt hoa thường — 'electronics' vẫn tạo được")
    void testCreateElectronicsCaseInsensitive() {
      CreateItemRequest req =
          buildRequest("Samsung TV", "Smart TV 4K", "electronics", "Samsung");

      // Factory nên normalize category về uppercase trước khi switch
      // Nếu chưa implement, test này sẽ fail → nhắc C thêm toUpperCase()
      assertDoesNotThrow(() -> ItemFactory.create(req, SELLER_ID),
          "Factory nên chấp nhận category không phân biệt hoa thường");
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Art
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Tạo Art")
  class CreateArt {

    @Test
    @DisplayName("Trả về đúng instance Art với artist chính xác")
    void testCreateArtReturnsCorrectInstance() {
      CreateItemRequest req =
          buildRequest("Bức tranh Hồ Gươm", "Tranh sơn dầu vẽ tay, 60x80cm", "ART",
              "Nguyễn Văn Nghĩa");

      Item item = ItemFactory.create(req, SELLER_ID);

      assertInstanceOf(Art.class, item,
          "Factory phải trả về Art khi category = ART");

      Art art = (Art) item;
      assertEquals("Nguyễn Văn Nghĩa", art.getArtist(), "Artist phải được gán từ categoryDetail");
    }

    @Test
    @DisplayName("Các trường dùng chung được gán đúng cho Art")
    void testCreateArtCommonFieldsCorrect() {
      CreateItemRequest req =
          buildRequest("Tượng gốm cổ", "Tượng gốm thời Lý, thế kỷ XI", "ART", "Vô danh");

      Item item = ItemFactory.create(req, SELLER_ID);

      assertAll("Common fields của Art",
          () -> assertEquals("Tượng gốm cổ", item.getName()),
          () -> assertEquals("Tượng gốm thời Lý, thế kỷ XI", item.getDescription()),
          () -> assertEquals(SELLER_ID, item.getSellerId()),
          () -> assertEquals("ART", item.getCategory())
      );
    }

    @Test
    @DisplayName("Artist có thể là tên dài có dấu — Unicode")
    void testCreateArtUnicodeArtistName() {
      String unicodeName = "Lê Văn Tự - Họa sĩ trường Mỹ Thuật Hà Nội";
      CreateItemRequest req =
          buildRequest("Phong cảnh mùa thu", "...", "ART", unicodeName);

      Item item = ItemFactory.create(req, SELLER_ID);
      Art art = (Art) item;

      assertEquals(unicodeName, art.getArtist(), "Artist name phải giữ nguyên Unicode");
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Vehicle
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Tạo Vehicle")
  class CreateVehicle {

    @Test
    @DisplayName("Trả về đúng instance Vehicle với năm sản xuất chính xác")
    void testCreateVehicleReturnsCorrectInstance() {
      CreateItemRequest req =
          buildRequest("Toyota Camry 2022", "Xe sedan 5 chỗ, màu trắng", "VEHICLE", "2022");

      Item item = ItemFactory.create(req, SELLER_ID);

      assertInstanceOf(Vehicle.class, item,
          "Factory phải trả về Vehicle khi category = VEHICLE");

      Vehicle vehicle = (Vehicle) item;
      assertEquals(2022, vehicle.getYear(), "Year phải được parse từ categoryDetail string");
    }

    @Test
    @DisplayName("Các trường dùng chung được gán đúng cho Vehicle")
    void testCreateVehicleCommonFieldsCorrect() {
      CreateItemRequest req =
          buildRequest("Honda Wave Alpha 2019", "Xe số, odo 12.000km", "VEHICLE", "2019");

      Item item = ItemFactory.create(req, SELLER_ID);

      assertAll("Common fields của Vehicle",
          () -> assertEquals("Honda Wave Alpha 2019", item.getName()),
          () -> assertEquals("Xe số, odo 12.000km", item.getDescription()),
          () -> assertEquals(SELLER_ID, item.getSellerId()),
          () -> assertEquals("VEHICLE", item.getCategory())
      );
    }

    @Test
    @DisplayName("categoryDetail không phải số nguyên → NumberFormatException")
    void testCreateVehicleInvalidYearFormatThrowsException() {
      CreateItemRequest req =
          buildRequest("Xe máy Honda", "...", "VEHICLE", "hai-nghìn-hai-mươi");

      // Factory sẽ gọi Integer.parseInt("hai-nghìn-hai-mươi") → NumberFormatException
      // Test này đảm bảo exception không bị nuốt im lặng
      assertThrows(NumberFormatException.class, () -> ItemFactory.create(req, SELLER_ID),
          "Năm sản xuất không phải số → phải throw NumberFormatException");
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Category không hợp lệ
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Category không hợp lệ")
  class InvalidCategory {

    @Test
    @DisplayName("Category lạ → IllegalArgumentException")
    void testInvalidCategoryThrowsIllegalArgumentException() {
      CreateItemRequest req =
          buildRequest("Sản phẩm lạ", "...", "FURNITURE", "gỗ sồi");

      IllegalArgumentException ex = assertThrows(
          IllegalArgumentException.class,
          () -> ItemFactory.create(req, SELLER_ID),
          "Category không tồn tại phải throw IllegalArgumentException"
      );

      // Message phải đủ rõ để developer hiểu ngay vấn đề
      assertNotNull(ex.getMessage(), "Exception phải có message mô tả lỗi");
    }

    @Test
    @DisplayName("Category null → IllegalArgumentException hoặc NullPointerException")
    void testNullCategoryThrowsException() {
      CreateItemRequest req = buildRequest("Item", "...", null, "detail");

      assertThrows(RuntimeException.class, () -> ItemFactory.create(req, SELLER_ID),
          "Category null phải throw exception, không trả về null silently");
    }

    @Test
    @DisplayName("Category rỗng → IllegalArgumentException")
    void testEmptyCategoryThrowsIllegalArgumentException() {
      CreateItemRequest req = buildRequest("Item", "...", "", "detail");

      assertThrows(IllegalArgumentException.class, () -> ItemFactory.create(req, SELLER_ID),
          "Category rỗng phải throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Message exception phải chứa category không hợp lệ để dễ debug")
    void testInvalidCategoryMessageContainsBadValue() {
      String badCategory = "SPACESHIP";
      CreateItemRequest req = buildRequest("Tên quặng", "...", badCategory, "detail");

      IllegalArgumentException ex = assertThrows(
          IllegalArgumentException.class,
          () -> ItemFactory.create(req, SELLER_ID)
      );

      // Message nên chứa giá trị lỗi để log dễ trace
      assertTrue(
          ex.getMessage().contains(badCategory) || ex.getMessage().contains("category"),
          "Exception message nên mention giá trị category không hợp lệ: " + badCategory
      );
    }
  }

  // ═══════════════════════════════════════════════════════════
  // Polymorphism — getCategory() trả đúng giá trị
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Polymorphism — getCategory()")
  class PolymorphismTest {

    @Test
    @DisplayName("Mỗi subclass getCategory() trả đúng string tương ứng")
    void testGetCategoryPolymorphism() {
      Item electronics = ItemFactory.create(
          buildRequest("TV", "...", "ELECTRONICS", "Sony"), SELLER_ID);
      Item art = ItemFactory.create(
          buildRequest("Tranh", "...", "ART", "Picasso"), SELLER_ID);
      Item vehicle = ItemFactory.create(
          buildRequest("Xe", "...", "VEHICLE", "2020"), SELLER_ID);

      assertAll("getCategory() polymorphism",
          () -> assertEquals("ELECTRONICS", electronics.getCategory()),
          () -> assertEquals("ART", art.getCategory()),
          () -> assertEquals("VEHICLE", vehicle.getCategory())
      );
    }
  }
}