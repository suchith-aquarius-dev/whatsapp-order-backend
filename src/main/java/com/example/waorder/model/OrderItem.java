package com.example.waorder.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited; // Import Audited

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "order_items")
@Audited // Add Audited annotation
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private String productName;
    private Integer quantity;
    private BigDecimal price;
}
