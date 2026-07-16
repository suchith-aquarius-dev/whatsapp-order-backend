package com.example.waorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderForm {

    @NotBlank
    private String token; // signed token carrying wa_id, from the query param

    @NotBlank
    private String customerName;

    @NotEmpty
    private List<String> productIds;

    private List<Integer> quantities;
}
