package com.gims.module.dbsync.entity.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Source DB 주문 Entity
 */
@Entity
@Table(name = "source_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceOrders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}
