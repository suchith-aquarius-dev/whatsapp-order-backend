package com.example.waorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderForm {

    @NotBlank
    private String token; // signed token carrying wa_id, from the query param

    private Long orderId; // Added to carry the order ID

    @NotBlank
    private String customerName;

    @NotEmpty
    private List<Long> productVariantIds; // Changed from productIds to productVariantIds

    private List<Integer> quantities;
}
