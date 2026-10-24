package sit.int221.backend.dtos;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SaleItemBySellerCreateDto {
    @NotNull
    private Integer brandId;                       // FE ส่ง brand.id -> map เป็น brandId

    @NotBlank
    @Size(min = 1, max = 60)
    private String model;

    @NotBlank
    @Size(min = 1, max = 16384)
    private String description;

    @NotNull
    @Min(0)
    private Integer price;                         // จำนวนเต็ม (เช่น 48900)

    @NotNull
    @Min(1)
    private Integer storageGb;

    @NotBlank
    @Size(max = 40)
    private String color;

    @NotNull
    @Min(0)
    private Integer quantity;                      // เทสกรอก 10 → บังคับให้มีจะชัวร์สุด

    @NotNull
    @Min(1)
    private Integer ramGb;                         // ต้องมี (Cypress เช็ค .itbms-ramGb)

    // ทศนิยมไม่เกิน 2 ตำแหน่ง
    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal screenSizeInch;             // ต้องมี (Cypress เช็ค .itbms-screenSizeInch)
}
