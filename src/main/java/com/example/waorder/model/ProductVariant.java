package com.example.waorder.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(nullable = false)
    private String name; // e.g., "Single", "Pack of 6 Assorted"

    @Column(nullable = false)
    private Integer quantityValue; // The actual number of items this variant represents (e.g., 1, 6)

    @Column(nullable = false)
    private BigDecimal price; // Price for this specific variant
}
