package com.example.waorder.controller;

import com.example.waorder.config.WhatsAppProperties;
import com.example.waorder.dto.OrderForm;
import com.example.waorder.dto.Product;
import com.example.waorder.model.Order;
import com.example.waorder.model.OrderItem;
import com.example.waorder.repository.OrderRepository;
import com.example.waorder.service.LinkTokenService;
import com.example.waorder.service.WhatsAppApiService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/order")
public class OrderController {

    // Hardcoded catalog for demo purposes - swap for a real ProductRepository/service
    private static final List<Product> CATALOG = List.of(
            new Product("p1", "Margherita Pizza", new BigDecimal("299")),
            new Product("p2", "Veg Burger", new BigDecimal("149")),
            new Product("p3", "Cold Coffee", new BigDecimal("99"))
    );

    private final LinkTokenService linkTokenService;
    private final OrderRepository orderRepository;
    private final WhatsAppApiService whatsAppApiService;
    private final WhatsAppProperties whatsAppProperties;

    public OrderController(LinkTokenService linkTokenService,
                            OrderRepository orderRepository,
                            WhatsAppApiService whatsAppApiService,
                            WhatsAppProperties whatsAppProperties) {
        this.linkTokenService = linkTokenService;
        this.orderRepository = orderRepository;
        this.whatsAppApiService = whatsAppApiService;
        this.whatsAppProperties = whatsAppProperties;
    }

    /**
     * Renders the order form. The signed token (containing wa_id) that we
     * embedded in the CTA-URL button arrives here as a query param.
     */
    @GetMapping
    public String showForm(@RequestParam String token,
                           @RequestParam(required = false) Long orderId, // Added orderId
                           Model model) {
        String waId;
        try {
            waId = linkTokenService.validateAndExtractWaId(token);
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            model.addAttribute("error", "This order link is invalid or has expired. Please message us again on WhatsApp.");
            return "link-error";
        }

        OrderForm form = new OrderForm();
        form.setToken(token);
        form.setOrderId(orderId); // Set orderId in form

        if (orderId != null) {
            Optional<Order> existingOrderOpt = orderRepository.findById(orderId);
            if (existingOrderOpt.isPresent()) {
                Order existingOrder = existingOrderOpt.get();

                // Check if the order is already finalized or payment link sent
                if (existingOrder.getStatus() == Order.OrderStatus.PAID ||
                    existingOrder.getStatus() == Order.OrderStatus.CANCELLED ||
                    existingOrder.getStatus() == Order.OrderStatus.PAYMENT_LINK_SENT) { // Added PAYMENT_LINK_SENT
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
                    return "order-status"; // New template for finalized orders
                }

                // Pre-fill form with existing order details
                form.setCustomerName(existingOrder.getCustomerName());
                form.setProductIds(existingOrder.getItems().stream().map(item -> {
                    // Find product ID from CATALOG based on product name
                    return CATALOG.stream()
                            .filter(p -> p.name().equals(item.getProductName()))
                            .map(Product::id)
                            .findFirst()
                            .orElse(null); // Handle case where product name might not match
                }).filter(java.util.Objects::nonNull).collect(Collectors.toList()));

                form.setQuantities(existingOrder.getItems().stream().map(OrderItem::getQuantity).collect(Collectors.toList()));
            }
        }

        model.addAttribute("orderForm", form);
        model.addAttribute("products", CATALOG);
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

        if (bindingResult.hasErrors()) {
            model.addAttribute("products", CATALOG);
            // If there's an orderId in the form, ensure it's passed back to the view
            if (form.getOrderId() != null) {
                model.addAttribute("orderId", form.getOrderId());
            }
            return "order-form";
        }

        model.addAttribute("whatsappNumber", whatsAppProperties.getWhatsappNumber()); // Always add for redirection

        try {
            Map<String, Product> byId = CATALOG.stream()
                    .collect(java.util.stream.Collectors.toMap(Product::id, p -> p));

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
            List<String> productIds = form.getProductIds();
            List<Integer> quantities = form.getQuantities();

            for (int i = 0; i < productIds.size(); i++) {
                Product product = byId.get(productIds.get(i));
                if (product == null) continue;
                int qty = (quantities != null && i < quantities.size() && quantities.get(i) != null)
                        ? quantities.get(i) : 1;

                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProductName(product.name());
                item.setQuantity(qty);
                item.setPrice(product.price());
                order.getItems().add(item);

                total = total.add(product.price().multiply(BigDecimal.valueOf(qty)));
            }
            order.setTotalAmount(total);
            order.setStatus(Order.OrderStatus.PAYMENT_LINK_SENT);

            Order saved = orderRepository.save(order);

            // Send the user back to WhatsApp with a Pay Now button for this order.
            // If it's been >24h since their last message, swap this for
            // whatsAppApiService.sendPayNowTemplate(saved) using an approved template.
            whatsAppApiService.sendPayNowMessage(saved);

            model.addAttribute("isSuccess", true);
            model.addAttribute("message", "Thank you for your order confirmation! You’ll receive a payment link on your WhatsApp. Please use this link to finalise your payment. If for any reason, you do not receive the link within 2 mins, please initiate a new conversation.");
            model.addAttribute("order", saved); // Pass order details for display if needed

        } catch (Exception e) {
            log.error("Failed to persist order or send WhatsApp message: {}", e.getMessage(), e);
            model.addAttribute("isSuccess", false);
            model.addAttribute("message", "We encountered an issue processing your order. Please click 'Continue' to return to WhatsApp and try again, or initiate a new conversation.");
        }

        return "order-status-alert";
    }
}
