package sit.int221.backend.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import sit.int221.backend.dtos.SaleItemBySellerCreateDto;
import sit.int221.backend.dtos.SaleItemGalleryDto;
import sit.int221.backend.entities.Brand;
import sit.int221.backend.entities.SaleItem;
import sit.int221.backend.entities.User;
import sit.int221.backend.exceptions.ApiException;
import sit.int221.backend.repositories.BrandRepository;
import sit.int221.backend.repositories.SaleItemRepository;
import sit.int221.backend.repositories.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerSaleItemService {
    private final SaleItemRepository saleItemRepo;
    private final UserRepository userRepo;
    private final BrandRepository brandRepo;

    /* ---------------- GET: Sale items by seller ---------------- */
    public List<SaleItemGalleryDto> getItemsBySeller(Integer sellerId, Integer currentUserId) {
        User seller = userRepo.findById(sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Seller not found"));

        // ✅ ตรวจสอบสิทธิ์
        if (!seller.getId().equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "You are not allowed to access sellerId=" + sellerId);
        }

        List<SaleItem> items = saleItemRepo.findBySeller_Id(seller.getId());
        return items.stream().map(this::toDto).toList();
    }

    /* ---------------- POST: Create sale item by seller ---------------- */
    public SaleItemGalleryDto createSaleItem(Integer sellerId, SaleItemBySellerCreateDto dto, Integer currentUserId) {
        User seller = userRepo.findById(sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Seller not found"));

        // ✅ ตรวจสอบสิทธิ์
        if (!seller.getId().equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "You are not allowed to create sale item for sellerId=" + sellerId);
        }

        Brand brand = brandRepo.findById(dto.getBrandId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Brand not found"));

        // ✅ Validation
        if (dto.getModel() == null || dto.getModel().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Model is required");
        }
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Description is required");
        }

        // ✅ สร้าง SaleItem
        SaleItem item = new SaleItem();
        item.setBrand(brand);
        item.setSeller(seller);

        item.setModel(dto.getModel().trim());
        item.setDescription(dto.getDescription().trim());
        item.setPrice(dto.getPrice());
        item.setQuantity(dto.getQuantity());                     // ใช้ค่าจาก DTO (ผ่าน validation แล้ว)
        item.setStorageGb(dto.getStorageGb());
        item.setRamGb(dto.getRamGb());
        item.setScreenSizeInch(dto.getScreenSizeInch());         // BigDecimal
        item.setColor(dto.getColor().trim());

        SaleItem saved = saleItemRepo.save(item);
        return toDto(saved);
    }

    /* ---------------- Helper: Convert Entity → DTO ---------------- */
    private SaleItemGalleryDto toDto(SaleItem item) {
        SaleItemGalleryDto dto = new SaleItemGalleryDto();
        dto.setId(item.getProductId());
        dto.setModel(item.getModel());
        dto.setDescription(item.getDescription());
        dto.setPrice(item.getPrice());
        dto.setQuantity(item.getQuantity());
        dto.setStorageGb(item.getStorageGb());
        dto.setRamGb(item.getRamGb());
        dto.setScreenSizeInch(item.getScreenSizeInch());
        dto.setColor(item.getColor());
        dto.setBrandName(item.getBrand() != null ? item.getBrand().getName() : null);
        dto.setCreatedOn(item.getCreatedOn());
        dto.setUpdatedOn(item.getUpdatedOn());
        return dto;
    }

    @Transactional
    public void deleteSaleItemBySeller(Integer sellerId, Integer saleItemId, Integer loginUserId) {
        // ตรวจสอบสิทธิ์ก่อน
        if (!sellerId.equals(loginUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not authorized to delete this item");
        }

        // หา sale item ที่ต้องการลบ
        SaleItem saleItem = saleItemRepo.findById(saleItemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale item not found"));

        // ตรวจสอบว่าเป็นของ seller คนนี้จริงไหม
        if (saleItem.getSeller() == null || !saleItem.getSeller().getId().equals(sellerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This sale item does not belong to you");
        }

        // ลบออกจากระบบ
        saleItemRepo.delete(saleItem);

        // (optional) แสดงใน log
        System.out.println("✅ Sale item ID " + saleItemId + " deleted by seller ID " + sellerId);
    }

}
