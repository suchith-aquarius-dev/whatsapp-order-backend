package com.example.waorder.controller;

import com.example.waorder.dto.OrderForm;
import com.example.waorder.dto.Product;
import com.example.waorder.model.Order;
import com.example.waorder.model.OrderItem;
import com.example.waorder.repository.OrderRepository;
import com.example.waorder.service.LinkTokenService;
import com.example.waorder.service.WhatsAppApiService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    public OrderController(LinkTokenService linkTokenService,
                            OrderRepository orderRepository,
                            WhatsAppApiService whatsAppApiService) {
        this.linkTokenService = linkTokenService;
        this.orderRepository = orderRepository;
        this.whatsAppApiService = whatsAppApiService;
    }

    /**
     * Renders the order form. The signed token (containing wa_id) that we
     * embedded in the CTA-URL button arrives here as a query param.
     */
    @GetMapping
    public String showForm(@RequestParam String token, Model model) {
        try {
            // Validate up front so the user sees an error page instead of a
            // broken form if the link expired or was tampered with.
            linkTokenService.validateAndExtractWaId(token);
        } catch (Exception e) {
            model.addAttribute("error", "This order link is invalid or has expired. Please message us again on WhatsApp.");
            return "link-error";
        }

        OrderForm form = new OrderForm();
        form.setToken(token);
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
            model.addAttribute("error", "This order link is invalid or has expired. Please message us again on WhatsApp.");
            return "link-error";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("products", CATALOG);
            return "order-form";
        }

        Map<String, Product> byId = CATALOG.stream()
                .collect(java.util.stream.Collectors.toMap(Product::id, p -> p));

        Order order = new Order();
        order.setWaId(waId);
        order.setCustomerName(form.getCustomerName());

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

        model.addAttribute("order", saved);
        return "thank-you";
    }
}
