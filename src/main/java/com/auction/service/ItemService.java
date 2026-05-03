package com.auction.service;

import com.auction.dao.ItemDao;
import com.auction.dto.CreateItemRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Item;
import com.auction.pattern.factory.ItemFactory;
import java.util.List;

/**
 * Service xử lý toàn bộ business logic liên quan đến sản phẩm (Item).
 *
 * <h2>Trách nhiệm của ItemService</h2>
 * <ul>
 *   <li>Tạo item đúng subclass thông qua {@link ItemFactory} (Factory Method pattern)</li>
 *   <li>Kiểm tra quyền sở hữu (ownership) trước khi cho phép sửa/xóa</li>
 *   <li>Delegate tất cả SQL operation xuống {@link ItemDao}</li>
 * </ul>
 *
 * <h2>Vị trí trong kiến trúc</h2>
 * <pre>
 * ItemController (A viết)
 *       │  gọi
 *       ▼
 * ItemService  ◄─── bạn đang ở đây
 *   │         │
 *   │ dùng    │ delegate SQL
 *   ▼         ▼
 * ItemFactory  ItemDao (A viết)
 * </pre>
 *
 * <h2>Nguyên tắc phân tầng</h2>
 * <p>ItemService không biết về HTTP, không biết về SQL. Nó chỉ biết về business rules:
 * "ai được tạo item?", "ai được sửa item?", "item được tạo bằng cách nào?"
 * Mọi chi tiết HTTP thuộc về Controller, mọi chi tiết SQL thuộc về DAO.
 */
public class ItemService {

  private final ItemDao itemDao;

  /**
   * Constructor injection — ItemDao được inject từ bên ngoài để dễ mock trong test.
   *
   * @param itemDao DAO để thực hiện các thao tác SQL trên bảng items
   */
  public ItemService(ItemDao itemDao) {
    this.itemDao = itemDao;
  }

  /**
   * Tạo sản phẩm mới cho seller.
   *
   * <p>Luồng xử lý:
   * <ol>
   *   <li>Delegate sang {@link ItemFactory#create} để tạo đúng subclass theo category</li>
   *   <li>Persist item mới xuống database qua {@link ItemDao#insert}</li>
   *   <li>Trả về item đã được gán id từ DB</li>
   * </ol>
   *
   * @param req      request chứa name, description, category, categoryDetail
   * @param sellerId ID của seller đang tạo — lưu vào item để check ownership sau này
   * @return item đã lưu vào DB với id được gán
   * @throws IllegalArgumentException nếu category không hợp lệ (từ ItemFactory)
   */
  public Item create(CreateItemRequest req, Long sellerId) {
    // Factory tạo đúng subclass (Electronics/Art/Vehicle) dựa trên req.getCategory()
    Item item = ItemFactory.create(req, sellerId);

    // Persist xuống DB — DAO trả về item với id đã được gán
    return itemDao.insert(item);
  }

  /**
   * Lấy tất cả sản phẩm trong hệ thống.
   *
   * <p>Không có filter — dùng cho admin hoặc danh sách công khai.
   * Xem {@link #getBySellerId} nếu cần lọc theo seller.
   *
   * @return danh sách tất cả items, có thể rỗng nếu chưa có item nào
   */
  public List<Item> getAll() {
    return itemDao.findAll();
  }

  /**
   * Lấy tất cả sản phẩm của một seller cụ thể.
   *
   * <p>Dùng trong form tạo phiên đấu giá: seller chọn item của mình từ danh sách này.
   *
   * @param sellerId ID của seller cần lấy danh sách item
   * @return danh sách items của seller, có thể rỗng
   */
  public List<Item> getBySellerId(Long sellerId) {
    return itemDao.findBySellerId(sellerId);
  }

  /**
   * Lấy thông tin chi tiết một sản phẩm theo ID.
   *
   * @param id ID của item cần lấy
   * @return item tìm thấy
   * @throws NotFoundException nếu không có item với id này
   */
  public Item getById(Long id) {
    return itemDao.findById(id)
        .orElseThrow(() -> new NotFoundException("Sản phẩm #" + id + " không tồn tại"));
  }

  /**
   * Cập nhật thông tin sản phẩm.
   *
   * <p>Chỉ seller sở hữu item mới được phép cập nhật.
   * Admin không có quyền sửa item (chỉ có quyền xóa).
   *
   * @param id          ID của item cần cập nhật
   * @param updatedItem item với thông tin mới (name, description, v.v.)
   * @param requesterId ID của người đang thực hiện request (từ JWT)
   * @throws NotFoundException      nếu item không tồn tại
   * @throws UnauthorizedException  nếu requester không phải seller sở hữu item này
   */
  public Item update(Long id, Item updatedItem, Long requesterId) {
    Item existing = getById(id); // throw NotFoundException nếu không có
    checkOwnership(existing, requesterId, "cập nhật");

    updatedItem.setId(id);
    updatedItem.setSellerId(existing.getSellerId()); // sellerId không thể thay đổi
    return itemDao.update(updatedItem);
  }

  /**
   * Xóa sản phẩm khỏi hệ thống.
   *
   * <p>Seller chỉ được xóa item của mình. Admin có thể xóa bất kỳ item nào.
   *
   * <p><b>Lưu ý:</b> Nếu item đang được dùng trong phiên RUNNING, việc xóa có thể
   * gây lỗi foreign key constraint ở DB — cần xử lý cascade hay block ở tầng DB.
   *
   * @param id          ID của item cần xóa
   * @param requesterId ID của người thực hiện request
   * @param requesterRole role của người thực hiện ("SELLER", "ADMIN")
   * @throws NotFoundException     nếu item không tồn tại
   * @throws UnauthorizedException nếu SELLER cố xóa item của người khác
   */
  public void delete(Long id, Long requesterId, String requesterRole) {
    Item existing = getById(id); // throw NotFoundException nếu không có

    // Admin có thể xóa mọi item. Seller chỉ xóa item của mình
    if (!"ADMIN".equals(requesterRole)) {
      checkOwnership(existing, requesterId, "xóa");
    }

    itemDao.deleteById(id);
  }

  /**
   * Kiểm tra quyền sở hữu: người request phải là seller của item.
   *
   * @param item        item cần kiểm tra
   * @param requesterId ID người đang request
   * @param action      tên hành động (dùng trong error message, ví dụ: "cập nhật", "xóa")
   * @throws UnauthorizedException nếu requesterId khác sellerId của item
   */
  private void checkOwnership(Item item, Long requesterId, String action) {
    if (!item.getSellerId().equals(requesterId)) {
      throw new UnauthorizedException(
          "Bạn không có quyền " + action + " sản phẩm #" + item.getId()
              + " vì bạn không phải người tạo sản phẩm này"
      );
    }
  }
}
