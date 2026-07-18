package com.example.waorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class OrderForm {

    @NotBlank
    private String token; // signed token carrying wa_id, from the query param

    private Long orderId; // Added to carry the order ID

    @NotBlank
    private String customerName;

    @NotNull(message = "Please select a delivery date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    @NotNull(message = "Please select a delivery time")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime deliveryTime;

    @NotEmpty
    private List<Long> productVariantIds; // Changed from productIds to productVariantIds

    private List<Integer> quantities;
}
