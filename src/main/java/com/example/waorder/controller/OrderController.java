package com.example.waorder.controller;

import com.example.waorder.config.WhatsAppProperties;
import com.example.waorder.dto.OrderForm;
import com.example.waorder.dto.Product;
import com.example.waorder.dto.ProductVariantDto;
import com.example.waorder.model.Order;
import com.example.waorder.model.OrderItem;
import com.example.waorder.model.ProductEntity;
import com.example.waorder.model.ProductVariant;
import com.example.waorder.repository.OrderRepository;
import com.example.waorder.service.LinkTokenService;
import com.example.waorder.service.ProductService;
import com.example.waorder.service.WhatsAppApiService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/order")
public class OrderController {

    private final LinkTokenService linkTokenService;
    private final OrderRepository orderRepository;
    private final WhatsAppApiService whatsAppApiService;
    private final WhatsAppProperties whatsAppProperties;
    private final ProductService productService;

    public OrderController(LinkTokenService linkTokenService,
                            OrderRepository orderRepository,
                            WhatsAppApiService whatsAppApiService,
                            WhatsAppProperties whatsAppProperties,
                            ProductService productService) {
        this.linkTokenService = linkTokenService;
        this.orderRepository = orderRepository;
        this.whatsAppApiService = whatsAppApiService;
        this.whatsAppProperties = whatsAppProperties;
        this.productService = productService;
    }

    /**
     * Renders the order form. The signed token (containing wa_id) that we
     * embedded in the CTA-URL button arrives here as a query param.
     */
    @GetMapping
    public String showForm(@RequestParam String token,
                           @RequestParam(required = false) Long orderId,
                           @RequestParam(required = false) String category, // Added category parameter
                           Model model) {
        String waId;
        try {
            waId = linkTokenService.validateAndExtractWaId(token);
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            // NEW LOGIC: If token is expired but orderId is present, try to show order details
            if (orderId != null) {
                Optional<Order> existingOrderOpt = orderRepository.findById(orderId);
                if (existingOrderOpt.isPresent()) {
                    Order existingOrder = existingOrderOpt.get();
                    model.addAttribute("order", existingOrder);
                    model.addAttribute("whatsappNumber", whatsAppProperties.getWhatsappNumber());
                    model.addAttribute("statusMessage", "This order link has expired, but here are the details of your order.");
                    return "order-status";
                }
            }
            // Original error handling if no orderId or order not found
            model.addAttribute("error", "This order link is invalid or has expired. Please message us again on WhatsApp.");
            return "link-error";
        }

        OrderForm form = new OrderForm();
        form.setToken(token);
        form.setOrderId(orderId);

        // Fetch all categories for the dropdown
        List<String> categories = productService.getAllCategories();
        model.addAttribute("categories", categories);
        model.addAttribute("selectedCategory", category); // Pass selected category back to view

        // Fetch products based on selected category or all if no category is selected
        List<ProductEntity> productEntities;
        if (category != null && !category.isEmpty()) {
            productEntities = productService.getProductsByCategory(category);
        } else {
            productEntities = productService.getAllProducts();
        }

        List<Product> catalog = productEntities.stream()
                                .map(p -> new Product(
                                        p.getId().toString(),
                                        p.getName(),
                                        p.getDescription(),
                                        p.getCategory(),
                                        p.getVariants().stream()
                                                .map(v -> new ProductVariantDto(v.getId(), v.getName(), v.getQuantityValue(), v.getPrice()))
                                                .collect(Collectors.toList()),
                                        p.getImageFilenames()
                                ))
                                .collect(Collectors.toList());


        if (orderId != null) {
            Optional<Order> existingOrderOpt = orderRepository.findById(orderId);
            if (existingOrderOpt.isPresent()) {
                Order existingOrder = existingOrderOpt.get();

                // Check if the order is already finalized or payment link sent
                if (existingOrder.getStatus() == Order.OrderStatus.PAID ||
                    existingOrder.getStatus() == Order.OrderStatus.CANCELLED ||
                    existingOrder.getStatus() == Order.OrderStatus.PAYMENT_LINK_SENT) {
                    model.addAttribute("order", existingOrder);
                    model.addAttribute("whatsappNumber", whatsAppProperties.getWhatsappNumber());

                    String statusMessage;
                    if (existingOrder.getStatus() == Order.OrderStatus.PAID) {
                        statusMessage = "This order has already been paid.";
                    } else if (existingOrder.getStatus() == Order.OrderStatus.CANCELLED) {
                        statusMessage = "This order has been cancelled.";
                    } else { // Order.OrderStatus.PAYMENT_LINK_SENT
                        statusMessage = "Your order has been placed, and a payment link has been sent to your WhatsApp. Please complete the payment there.";
                    }
                    model.addAttribute("statusMessage", statusMessage);
                    return "order-status";
                }

                // Pre-fill form with existing order details
                form.setCustomerName(existingOrder.getCustomerName());
                // Populate productVariantIds based on existing order items
                form.setProductVariantIds(existingOrder.getItems().stream()
                        .map(item -> {
                            // Find the variant that matches the ordered item's name and price
                            return productEntities.stream() // Use filtered productEntities
                                    .flatMap(p -> p.getVariants().stream())
                                    .filter(v -> (v.getProduct().getName() + " - " + v.getName()).equals(item.getProductName()) && v.getPrice().compareTo(item.getPrice()) == 0)
                                    .map(ProductVariant::getId)
                                    .findFirst()
                                    .orElse(null);
                        })
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList()));

                // Note: Quantities are not pre-filled for individual items in the current form structure.
                // User will need to re-enter quantities for selected items.
                // form.setQuantities(existingOrder.getItems().stream().map(OrderItem::getQuantity).collect(Collectors.toList()));
            }
        }

        model.addAttribute("orderForm", form);
        model.addAttribute("products", catalog); // Pass dynamic catalog to view
        return "order-form";
    }

    /**
     * Handles form submission: creates the order, then immediately triggers
     * the WhatsApp service to send the user a "Pay Now" message back on the
     * same wa_id we validated from the token.
     */
    @PostMapping
    public String submitForm(@Valid @ModelAttribute("orderForm") OrderForm form,
                              BindingResult bindingResult,
                              Model model) {

        String waId;
        try {
            waId = linkTokenService.validateAndExtractWaId(form.getToken());
        } catch (Exception e) {
            log.error("Error validating token on form submission: {}", e.getMessage());
            model.addAttribute("error", "This order link is invalid or has expired. Please message us again on WhatsApp.");
            return "link-error";
        }

        // Fetch products from database for validation and calculation
        List<ProductEntity> productEntities = productService.getAllProducts();
        Map<Long, ProductVariant> allVariants = productEntities.stream()
                .flatMap(p -> p.getVariants().stream())
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));


        if (bindingResult.hasErrors()) {
            // Re-map ProductEntities to Product DTOs for the view if there are errors
            List<Product> catalogForError = productEntities.stream()
                    .map(p -> new Product(
                            p.getId().toString(),
                            p.getName(),
                            p.getDescription(),
                            p.getCategory(),
                            p.getVariants().stream()
                                    .map(v -> new ProductVariantDto(v.getId(), v.getName(), v.getQuantityValue(), v.getPrice()))
                                    .collect(Collectors.toList()),
                            p.getImageFilenames()
                    ))
                    .collect(Collectors.toList());
            model.addAttribute("products", catalogForError);
            // Also pass categories and selected category back on error
            model.addAttribute("categories", productService.getAllCategories());
            model.addAttribute("selectedCategory", null); // Or try to retain previous selection
            if (form.getOrderId() != null) {
                model.addAttribute("orderId", form.getOrderId());
            }
            return "order-form";
        }

        model.addAttribute("whatsappNumber", whatsAppProperties.getWhatsappNumber());

        try {
            Order order;
            if (form.getOrderId() != null) {
                // Update existing order
                order = orderRepository.findById(form.getOrderId())
                        .orElseThrow(() -> new IllegalArgumentException("Order not found for ID: " + form.getOrderId()));
                order.setCustomerName(form.getCustomerName());
                order.getItems().clear(); // Clear existing items to replace
            } else {
                // Create new order
                order = new Order();
                order.setWaId(waId);
                order.setCustomerName(form.getCustomerName());
            }

            BigDecimal total = BigDecimal.ZERO;
            List<Long> productVariantIds = form.getProductVariantIds();
            List<Integer> quantities = form.getQuantities();

            for (int i = 0; i < productVariantIds.size(); i++) {
                Long variantId = productVariantIds.get(i);
                ProductVariant variant = allVariants.get(variantId); // Get variant by ID
                if (variant == null) {
                    log.warn("Selected product variant with ID {} not found. Skipping.", variantId);
                    continue;
                }
                int qty = (quantities != null && i < quantities.size() && quantities.get(i) != null)
                        ? quantities.get(i) : 1;

                OrderItem item = new OrderItem();
                item.setOrder(order);
                // Store product name + variant name for clarity
                item.setProductName(variant.getProduct().getName() + " - " + variant.getName());
                item.setQuantity(qty);
                item.setPrice(variant.getPrice()); // Use variant's price
                order.getItems().add(item);

                total = total.add(variant.getPrice().multiply(BigDecimal.valueOf(qty)));
            }
            order.setTotalAmount(total);
            order.setStatus(Order.OrderStatus.PAYMENT_LINK_SENT);

            Order saved = orderRepository.save(order);

            whatsAppApiService.sendPayNowMessage(saved);

            model.addAttribute("isSuccess", true);
            model.addAttribute("message", "Thank you for your order confirmation! You’ll receive a payment link on your WhatsApp. Please use this link to finalise your payment. If for any reason, you do not receive the link within 2 mins, please initiate a new conversation.");
            model.addAttribute("order", saved);

        } catch (Exception e) {
            log.error("Failed to persist order or send WhatsApp message: {}", e.getMessage(), e);
            model.addAttribute("isSuccess", false);
            model.addAttribute("message", "We encountered an issue processing your order. Please click 'Continue' to return to WhatsApp and try again, or initiate a new conversation.");
        }

        return "order-status-alert";
    }
}
