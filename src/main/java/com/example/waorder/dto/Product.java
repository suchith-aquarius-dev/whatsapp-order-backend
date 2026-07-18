package com.example.waorder.dto;

import java.util.List;

public record Product(String id, String name, String description, String category, List<ProductVariantDto> variants, List<String> imageFilenames) { // Replaced price with variants list
}
