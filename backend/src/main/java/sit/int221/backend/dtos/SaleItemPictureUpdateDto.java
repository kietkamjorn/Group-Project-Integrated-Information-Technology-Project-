package sit.int221.backend.dtos;

import lombok.Data;

/**
 * สำหรับแก้ไขข้อมูลรูปภาพ (ตอนนี้รองรับเฉพาะ displayOrder)
 * ถ้าภายหลังอยากรองรับ rename fileName / อื่น ๆ ก็เติมฟิลด์ได้
 */
@Data
public class SaleItemPictureUpdateDto {
    private Integer displayOrder;   // ลำดับใหม่ (1..N)
}
