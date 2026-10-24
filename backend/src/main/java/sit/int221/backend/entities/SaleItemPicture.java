// src/main/java/sit/int221/backend/entities/SaleItemPicture.java
package sit.int221.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sale_item_pictures")
public class SaleItemPicture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // FK -> sale_items(productId)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", referencedColumnName = "productId", nullable = false)
    private SaleItem saleItem;

    // map field fileName -> column "name"
    @Column(name = "name", nullable = false, length = 255)
    private String fileName;

    @Column(length = 500)
    private String path;

    @Column(length = 50)
    private String type;

    @Column
    private Integer size;

    // map field displayOrder -> column "display_order"
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "create_at",
            updatable = false,
            insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Instant createAt;
}
