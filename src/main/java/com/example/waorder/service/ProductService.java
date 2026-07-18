package com.example.waorder.service;

import com.example.waorder.model.ProductEntity;
import com.example.waorder.model.ProductVariant;
import com.example.waorder.repository.ProductRepository;
import com.example.waorder.repository.ProductVariantRepository;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductService {

    @Value("${product.image.upload-dir}")
    private String uploadDir;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    public ProductService(ProductRepository productRepository,
                          ProductVariantRepository productVariantRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Transactional
    public ProductEntity saveProduct(ProductEntity product,
                                     List<MultipartFile> newImageFiles,
                                     List<String> existingImageFilenames) throws IOException {
        // Ensure the upload directory exists
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        List<String> finalImageFilenames = new ArrayList<>();

        // 1. Handle existing images (those not removed by the user)
        if (existingImageFilenames != null) {
            finalImageFilenames.addAll(existingImageFilenames);
        }

        // 2. Handle new image file uploads - resized & compressed before saving,
        // instead of storing whatever resolution/size the admin's phone camera
        // produced. This is the main lever for faster page loads: a raw phone
        // photo can be 3-5MB; the version we actually need to display in a
        // small product card is a fraction of that.
        if (newImageFiles != null && !newImageFiles.isEmpty()) {
            for (MultipartFile file : newImageFiles) {
                if (!file.isEmpty()) {
                    // Always save as .jpg: consistent, well-compressed output
                    // regardless of the source format (heic/png/jpg from
                    // different phones). Fine for product photos; only a
                    // concern if you need transparent PNGs, which product
                    // photography usually doesn't.
                    String uniqueFilename = UUID.randomUUID().toString() + ".jpg";
                    Path filePath = uploadPath.resolve(uniqueFilename);

                    Thumbnails.of(file.getInputStream())
                            // Cap the longest side at 900px - plenty sharp for
                            // a phone-screen product card, a fraction of the
                            // size of an original camera photo.
                            .size(900, 900)
                            .outputQuality(0.72)
                            .outputFormat("jpg")
                            .toFile(filePath.toFile());

                    finalImageFilenames.add(uniqueFilename);
                }
            }
        }

        // 3. If it's an update, identify and delete removed images from disk
        if (product.getId() != null) {
            Optional<ProductEntity> existingProductOpt = productRepository.findById(product.getId());
            if (existingProductOpt.isPresent()) {
                ProductEntity existingProduct = existingProductOpt.get();
                Set<String> currentImageFilenames = existingProduct.getImageFilenames().stream().collect(Collectors.toSet());
                Set<String> keptImageFilenames = finalImageFilenames.stream().collect(Collectors.toSet());

                currentImageFilenames.stream()
                        .filter(filename -> !keptImageFilenames.contains(filename))
                        .forEach(filename -> {
                            try {
                                Files.deleteIfExists(uploadPath.resolve(filename));
                                log.info("Deleted image file: {}", filename);
                            } catch (IOException e) {
                                log.error("Failed to delete image file: {}", filename, e);
                            }
                        });
            }
        }

        // Update the product's image filenames list with the final combined list
        product.setImageFilenames(finalImageFilenames);

        // Set the product reference for each variant before saving the product
        if (product.getVariants() != null) {
            product.getVariants().forEach(variant -> variant.setProduct(product));
        }

        return productRepository.save(product);
    }

    public List<ProductEntity> getAllProducts() {
        return productRepository.findAll();
    }

    // New method to get products by category
    public List<ProductEntity> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    // New method to get all unique categories
    public List<String> getAllCategories() {
        return productRepository.findAll().stream()
                .map(ProductEntity::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Optional<ProductEntity> findProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Optional<ProductEntity> productOpt = productRepository.findById(id);
        if (productOpt.isPresent()) {
            ProductEntity product = productOpt.get();
            // Delete associated image files from disk
            Path uploadPath = Paths.get(uploadDir);
            product.getImageFilenames().forEach(filename -> {
                try {
                    Files.deleteIfExists(uploadPath.resolve(filename));
                    log.info("Deleted image file for product {}: {}", product.getId(), filename);
                } catch (IOException e) {
                    log.error("Failed to delete image file {} for product {}: {}", filename, product.getId(), e.getMessage());
                }
            });
            productRepository.deleteById(id);
        } else {
            log.warn("Attempted to delete non-existent product with ID: {}", id);
        }
    }

    public Optional<ProductVariant> getProductVariantById(Long variantId) {
        return productVariantRepository.findById(variantId);
    }

    public Path getProductImagePath(String filename) {
        return Paths.get(uploadDir).resolve(filename);
    }
}
