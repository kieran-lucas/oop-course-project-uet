package com.auction.pattern.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.auction.dto.CreateItemRequest;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử {@link ItemFactory} — factory tạo đúng kiểu con Item từ yêu cầu tạo sản phẩm.
 *
 * <p>Bao phủ ba loại sản phẩm hợp lệ (Electronics, Art, Vehicle) và trường hợp category không hợp
 * lệ. Không cần kết nối DB — pure unit test.
 */
@DisplayName("ItemFactory")
class ItemFactoryTest {

  private static final Long SELLER_ID = 42L;

  @Test
  @DisplayName("tạo Electronics với brand từ categoryDetail")
  void createsElectronics() {
    Item item = ItemFactory.create(request("Laptop", "Desc", "ELECTRONICS", "Dell"), SELLER_ID);

    Electronics electronics = assertInstanceOf(Electronics.class, item);
    assertEquals("ELECTRONICS", item.getCategory());
    assertEquals("Dell", electronics.getBrand());
  }

  @Test
  @DisplayName("tạo Art với artist từ categoryDetail")
  void createsArt() {
    Item item = ItemFactory.create(request("Painting", "Desc", "ART", "Da Vinci"), SELLER_ID);

    Art art = assertInstanceOf(Art.class, item);
    assertEquals("ART", item.getCategory());
    assertEquals("Da Vinci", art.getArtist());
  }

  @Test
  @DisplayName("tạo Vehicle với year được parse từ categoryDetail")
  void createsVehicle() {
    Item item = ItemFactory.create(request("Car", "Desc", "VEHICLE", "2022"), SELLER_ID);

    Vehicle vehicle = assertInstanceOf(Vehicle.class, item);
    assertEquals("VEHICLE", item.getCategory());
    assertEquals(2022, vehicle.getYear());
  }

  @Test
  @DisplayName("ném IllegalArgumentException khi category không hợp lệ")
  void rejectsUnknownCategory() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ItemFactory.create(request("Desk", "Desc", "FURNITURE", "Oak"), SELLER_ID));
  }

  private CreateItemRequest request(
      String name, String description, String category, String detail) {
    return new CreateItemRequest(name, description, category, detail);
  }
}
