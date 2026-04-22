package com.auction.pattern.factory;

import com.auction.dto.CreateItemRequest;
import com.auction.model.Art;
import com.auction.model.Electronics;
import com.auction.model.Item;
import com.auction.model.Vehicle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemFactoryTest {

    private final Long SELLER_ID = 1L;

    @Test
    void testCreateElectronics() {
        // Chuẩn bị đầu vào
        CreateItemRequest req = new CreateItemRequest();
        req.setName("iPhone 15");
        req.setDescription("Mới 99%");
        req.setCategory("ELECTRONICS");
        req.setCategoryDetail("Apple"); // categoryDetail của Electronics là brand

        // Hành động
        Item item = ItemFactory.create(req, SELLER_ID);

        // Kiểm tra
        assertNotNull(item);
        assertTrue(item instanceof Electronics, "Phải tạo ra đối tượng Electronics");
        Electronics electronics = (Electronics) item;
        assertEquals("Apple", electronics.getBrand(), "Brand (Thương hiệu) phải được map chính xác");
    }

    @Test
    void testCreateArt() {
        CreateItemRequest req = new CreateItemRequest();
        req.setName("Mona Lisa Replica");
        req.setCategory("ART");
        req.setCategoryDetail("Leonardo da Vinci"); // artist

        Item item = ItemFactory.create(req, SELLER_ID);

        assertTrue(item instanceof Art, "Phải tạo ra đối tượng Art");
        Art art = (Art) item;
        assertEquals("Leonardo da Vinci", art.getArtist(), "Artist (Nghệ sĩ) phải được map chính xác");
    }

    @Test
    void testCreateVehicle() {
        CreateItemRequest req = new CreateItemRequest();
        req.setName("Toyota Camry");
        req.setCategory("VEHICLE");
        req.setCategoryDetail("2024"); // year

        Item item = ItemFactory.create(req, SELLER_ID);

        assertTrue(item instanceof Vehicle, "Phải tạo ra đối tượng Vehicle");
        Vehicle vehicle = (Vehicle) item;
        assertEquals(2024, vehicle.getYear(), "Year (Năm sản xuất) phải được map chính xác");
    }

    @Test
    void testInvalidCategory() {
        CreateItemRequest req = new CreateItemRequest();
        req.setCategory("FURNITURE"); // Một hạng mục không tồn tại

        // Kiểm tra xem hệ thống có bắt lỗi và ném ra IllegalArgumentException không
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ItemFactory.create(req, SELLER_ID);
        });
        
        assertNotNull(exception);
    }
}