package com.gims.module.dbsync.entity.copy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Copy 테이블 - Source Orders의 1차 복사본 (Target DB에 저장)
 * source_orders와 동일한 구조 + source_uk (역추적용)
 */
@Entity
@Table(name = "copy_source_orders", indexes = {
    @Index(name = "idx_copy_source_orders_uk", columnList = "source_uk", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopySourceOrders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "copy_id")
    private Long copyId;

    /**
     * Source 역추적용 UK: {sourceTable}_{sourcePK}
     * 예: source_orders_789
     */
    @Column(name = "source_uk", length = 100, nullable = false, unique = true)
    private String sourceUk;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", length = 50, nullable = false)
    private String orderNumber;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "order_status", length = 30)
    private String orderStatus;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "copy_at")
    private LocalDateTime copyAt;

    /**
     * 추가/수정 구분: false=신규추가, true=수정
     */
    @Builder.Default
    @Column(name = "is_updated")
    private Boolean isUpdated = false;
}
