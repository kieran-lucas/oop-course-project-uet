package com.auction.service;

import com.auction.dao.ItemDao;
import com.auction.dto.CreateItemRequest;
import com.auction.exception.NotFoundException;
import com.auction.exception.UnauthorizedException;
import com.auction.model.Item;
import com.auction.pattern.factory.ItemFactory;
import java.util.List;

/**
 * Service xử lý logic nghiệp vụ cho sản phẩm đấu giá (Item).
 *
 * <p>Lớp này là tầng trung gian giữa Controller và DAO — xác thực quyền sở hữu, áp dụng Factory
 * Pattern để tạo đúng loại sản phẩm, sau đó ủy quyền lưu trữ cho {@link ItemDao}.
 *
 * <p>Mọi thay đổi trạng thái sản phẩm liên quan đến đấu giá (AVAILABLE → IN_AUCTION → SOLD) được
 * thực hiện bởi {@link AuctionService} và {@code BidService}, không phải tại đây. Lớp này chỉ xử lý
 * vòng đời nội tại của sản phẩm: tạo mới, đọc, cập nhật thông tin, và xóa.
 */
public class ItemService {

  private static final String STATUS_AVAILABLE = "AVAILABLE";

  private final ItemDao itemDao;

  /**
   * Tạo ItemService với DAO sản phẩm.
   *
   * @param itemDao DAO để truy cập bảng {@code items}
   */
  public ItemService(ItemDao itemDao) {
    this.itemDao = itemDao;
  }

  /**
   * Tạo sản phẩm mới và lưu vào DB.
   *
   * <p>Sử dụng {@link ItemFactory} để tạo đúng loại con (Electronics, Art, Vehicle) dựa trên {@code
   * category} trong request. Sản phẩm được tạo với trạng thái {@code AVAILABLE} mặc định.
   *
   * @param req dữ liệu sản phẩm từ client (tên, mô tả, category, chi tiết theo loại)
   * @param sellerId ID seller đang đăng sản phẩm
   * @return sản phẩm đã được lưu vào DB (có id và createdAt)
   * @throws IllegalArgumentException nếu {@code category} không hợp lệ hoặc dữ liệu không hợp lệ
   */
  public Item create(CreateItemRequest req, Long sellerId) {
    Item item = ItemFactory.create(req, sellerId);
    return itemDao.insert(item);
  }

  /**
   * Lấy tất cả sản phẩm trong hệ thống (không phân trang).
   *
   * @return danh sách tất cả sản phẩm
   */
  public List<Item> getAll() {
    return itemDao.findAll();
  }

  /**
   * Lấy tất cả sản phẩm của một seller cụ thể.
   *
   * @param sellerId ID seller cần truy vấn
   * @return danh sách sản phẩm thuộc seller
   */
  public List<Item> getBySellerId(Long sellerId) {
    return itemDao.findBySellerId(sellerId);
  }

  /**
   * Lấy thông tin một sản phẩm theo ID.
   *
   * @param id ID sản phẩm
   * @return sản phẩm tìm được
   * @throws NotFoundException nếu không tìm thấy sản phẩm với ID đã cho
   */
  public Item getById(Long id) {
    return itemDao
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Item #" + id + " not found"));
  }

  /**
   * Cập nhật thông tin sản phẩm.
   *
   * <p>Tạo đối tượng sản phẩm mới từ request, sau đó gán lại {@code id} và giữ nguyên {@code
   * status} từ bản ghi cũ (không được phép đổi trạng thái từ endpoint này). Chỉ người sở hữu sản
   * phẩm mới được thực hiện thao tác này.
   *
   * @param id ID sản phẩm cần cập nhật
   * @param request dữ liệu mới (tên, mô tả, category, chi tiết theo loại)
   * @param requesterId ID người thực hiện yêu cầu (từ JWT)
   * @return sản phẩm sau khi cập nhật
   * @throws NotFoundException nếu sản phẩm không tồn tại
   * @throws UnauthorizedException nếu người yêu cầu không phải chủ sản phẩm
   * @throws IllegalStateException nếu sản phẩm không còn ở trạng thái AVAILABLE
   */
  public Item update(Long id, CreateItemRequest request, Long requesterId) {
    Item existing = getById(id);
    checkOwnership(existing, requesterId, "update");
    ensureAvailableForMutation(existing, "update");

    Item updatedItem = ItemFactory.create(request, existing.getSellerId());
    updatedItem.setId(id);
    // Giữ nguyên status — trạng thái sản phẩm chỉ được thay đổi qua AuctionService/BidService
    updatedItem.setStatus(existing.getStatus());
    itemDao.update(updatedItem);
    return updatedItem;
  }

  /**
   * Xóa mềm sản phẩm (chuyển status sang {@code REMOVED}).
   *
   * <p>ADMIN có thể xóa bất kỳ sản phẩm nào. SELLER chỉ xóa được sản phẩm của chính mình.
   *
   * @param id ID sản phẩm cần xóa
   * @param requesterId ID người thực hiện yêu cầu (từ JWT)
   * @param requesterRole role của người thực hiện ("ADMIN" hoặc "SELLER")
   * @throws NotFoundException nếu sản phẩm không tồn tại
   * @throws UnauthorizedException nếu SELLER cố xóa sản phẩm của người khác
   * @throws IllegalStateException nếu sản phẩm không còn ở trạng thái AVAILABLE
   */
  public void delete(Long id, Long requesterId, String requesterRole) {
    Item existing = getById(id);

    // ADMIN có quyền xóa bất kỳ sản phẩm nào, không cần kiểm tra quyền sở hữu
    if (!"ADMIN".equals(requesterRole)) {
      checkOwnership(existing, requesterId, "delete");
    }

    ensureAvailableForMutation(existing, "delete");
    itemDao.delete(id);
  }

  /**
   * Kiểm tra người thực hiện có phải là chủ sở hữu sản phẩm không.
   *
   * @param item sản phẩm cần kiểm tra
   * @param requesterId ID người thực hiện
   * @param action tên hành động (dùng trong thông điệp lỗi, ví dụ: "update", "delete")
   * @throws UnauthorizedException nếu người thực hiện không phải chủ sở hữu
   */
  private void checkOwnership(Item item, Long requesterId, String action) {
    if (!item.getSellerId().equals(requesterId)) {
      throw new UnauthorizedException(
          "You do not have permission to "
              + action
              + " item #"
              + item.getId()
              + " because you are not the owner of this item");
    }
  }

  /**
   * Chặn sửa/xóa sản phẩm không còn rảnh. Item đang đấu giá, đã bán hoặc đã bị remove không được
   * mutate qua ItemService vì sẽ phá vỡ vòng đời auction-item.
   */
  private void ensureAvailableForMutation(Item item, String action) {
    if (!STATUS_AVAILABLE.equals(item.getStatus())) {
      throw new IllegalStateException(
          "Cannot "
              + action
              + " item #"
              + item.getId()
              + " because its status is "
              + item.getStatus()
              + ". Only AVAILABLE items can be modified.");
    }
  }
}
