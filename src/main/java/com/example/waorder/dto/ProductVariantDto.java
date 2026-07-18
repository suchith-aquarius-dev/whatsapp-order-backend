package com.example.waorder.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDto {
    private Long id;
    private String name; // e.g., "Single", "Pack of 6 Assorted"
    private Integer quantityValue; // The actual number of items this variant represents (e.g., 1, 6)
    private BigDecimal price; // Price for this specific variant
}
