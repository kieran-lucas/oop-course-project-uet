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

@DisplayName("ItemFactory")
class ItemFactoryTest {

  private static final Long SELLER_ID = 42L;

  @Test
  @DisplayName("Creates Electronics with brand")
  void createsElectronics() {
    Item item = ItemFactory.create(request("Laptop", "Desc", "ELECTRONICS", "Dell"), SELLER_ID);

    Electronics electronics = assertInstanceOf(Electronics.class, item);
    assertEquals("ELECTRONICS", item.getCategory());
    assertEquals("Dell", electronics.getBrand());
  }

  @Test
  @DisplayName("Creates Art with artist")
  void createsArt() {
    Item item = ItemFactory.create(request("Painting", "Desc", "ART", "Da Vinci"), SELLER_ID);

    Art art = assertInstanceOf(Art.class, item);
    assertEquals("ART", item.getCategory());
    assertEquals("Da Vinci", art.getArtist());
  }

  @Test
  @DisplayName("Creates Vehicle with year")
  void createsVehicle() {
    Item item = ItemFactory.create(request("Car", "Desc", "VEHICLE", "2022"), SELLER_ID);

    Vehicle vehicle = assertInstanceOf(Vehicle.class, item);
    assertEquals("VEHICLE", item.getCategory());
    assertEquals(2022, vehicle.getYear());
  }

  @Test
  @DisplayName("Rejects unknown category")
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
