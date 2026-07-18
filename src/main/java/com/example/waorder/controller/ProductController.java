package com.example.waorder.controller;

import com.example.waorder.model.ProductEntity;
import com.example.waorder.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/products")
@Slf4j
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/add")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new ProductEntity());
        return "add-product";
    }

    @PostMapping("/add")
    public String addProduct(@ModelAttribute ProductEntity product,
                             @RequestParam(value = "imageFiles", required = false) List<MultipartFile> imageFiles,
                             @RequestParam(value = "existingImageFilenames", required = false) List<String> existingImageFilenames, // Added
                             RedirectAttributes redirectAttributes) {
        try {
            // For new products, existingImageFilenames will be null or empty, which saveProduct handles
            productService.saveProduct(product, imageFiles, existingImageFilenames); // Pass existingImageFilenames
            redirectAttributes.addFlashAttribute("message", "Product added successfully!");
        } catch (IOException e) {
            log.error("Error saving product or images", e);
            redirectAttributes.addFlashAttribute("error", "Failed to add product: " + e.getMessage());
        }
        return "redirect:/admin/products/list";
    }

    @GetMapping("/list")
    public String listProducts(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "list-products";
    }

    @GetMapping("/edit/{id}")
    public String showEditProductForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<ProductEntity> productOptional = productService.findProductById(id);
        if (productOptional.isPresent()) {
            model.addAttribute("product", productOptional.get());
            return "add-product"; // Reuse the add-product form for editing
        } else {
            redirectAttributes.addFlashAttribute("error", "Product not found!");
            return "redirect:/admin/products/list";
        }
    }

    @PostMapping("/edit")
    public String updateProduct(@ModelAttribute ProductEntity product,
                                @RequestParam(value = "imageFiles", required = false) List<MultipartFile> imageFiles,
                                @RequestParam(value = "existingImageFilenames", required = false) List<String> existingImageFilenames, // Added
                                RedirectAttributes redirectAttributes) {
        try {
            productService.saveProduct(product, imageFiles, existingImageFilenames); // Pass existingImageFilenames
            redirectAttributes.addFlashAttribute("message", "Product updated successfully!");
        } catch (IOException e) {
            log.error("Error updating product or images", e);
            redirectAttributes.addFlashAttribute("error", "Failed to update product: " + e.getMessage());
        }
        return "redirect:/admin/products/list";
    }

    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            productService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("message", "Product deleted successfully!");
        } catch (Exception e) {
            log.error("Error deleting product {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete product: " + e.getMessage());
        }
        return "redirect:/admin/products/list";
    }

    @GetMapping("/images/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path filePath = productService.getProductImagePath(filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        // Filenames are random UUIDs and never reused for different
                        // content, so it's safe for browsers to cache these
                        // indefinitely - avoids re-downloading the same product
                        // photo on every visit to the order form.
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                        .body(resource);
            } else {
                log.warn("Could not read file: {}", filename);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("Malformed URL for file: {}", filename, e);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("Error probing content type for file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
