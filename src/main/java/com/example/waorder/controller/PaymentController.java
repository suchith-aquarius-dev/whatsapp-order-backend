package com.example.waorder.controller;

import com.example.waorder.config.WhatsAppProperties;
import com.example.waorder.model.Order;
import com.example.waorder.repository.OrderRepository;
import com.example.waorder.service.WhatsAppApiService; // Import WhatsAppApiService
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

@Slf4j
@Controller
@RequestMapping("/pay")
public class PaymentController {

    private final OrderRepository orderRepository;
    private final WhatsAppProperties whatsAppProperties;
    private final WhatsAppApiService whatsAppApiService; // Inject WhatsAppApiService

    public PaymentController(OrderRepository orderRepository,
                            WhatsAppProperties whatsAppProperties,
                            WhatsAppApiService whatsAppApiService) { // Add to constructor
        this.orderRepository = orderRepository;
        this.whatsAppProperties = whatsAppProperties;
        this.whatsAppApiService = whatsAppApiService; // Initialize
    }

    @GetMapping("/{orderId}")
    public String showPaymentPage(@PathVariable Long orderId, Model model) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order Id:" + orderId));
        model.addAttribute("order", order);

        // Check if the order is already finalized (paid or cancelled)
        if (order.getStatus() == Order.OrderStatus.PAID) {
            model.addAttribute("isOrderFinalized", true);
            model.addAttribute("statusMessage", "This order has already been paid.");
        } else if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            model.addAttribute("isOrderFinalized", true);
            model.addAttribute("statusMessage", "This order has already been cancelled.");
        } else {
            model.addAttribute("isOrderFinalized", false);
        }

        return "payment-page";
    }

    @PostMapping("/{orderId}/success")
    public RedirectView makePaymentSuccess(@PathVariable Long orderId) {
        log.info("Payment success for order {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order Id:" + orderId));
        
        // Prevent status change if already paid or cancelled
        if (order.getStatus() == Order.OrderStatus.PAID || order.getStatus() == Order.OrderStatus.CANCELLED) {
            log.warn("Attempted to mark order {} as paid, but it's already in status: {}", orderId, order.getStatus());
            return redirectToWhatsApp(); // Redirect without changing status or sending message again
        }

        order.setStatus(Order.OrderStatus.PAID);
        orderRepository.save(order);
        whatsAppApiService.sendTextMessage(order.getWaId(), "Your order #" + order.getId() + " has been confirmed and paid. Thank you!"); // Send confirmation message
        return redirectToWhatsApp();
    }

    @PostMapping("/{orderId}/cancel")
    public RedirectView cancelOrder(@PathVariable Long orderId) {
        log.info("Payment failed for order {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order Id:" + orderId));
        
        // Prevent status change if already paid or cancelled
        if (order.getStatus() == Order.OrderStatus.PAID || order.getStatus() == Order.OrderStatus.CANCELLED) {
            log.warn("Attempted to mark order {} as cancelled, but it's already in status: {}", orderId, order.getStatus());
            return redirectToWhatsApp(); // Redirect without changing status or sending message again
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
        whatsAppApiService.sendTextMessage(order.getWaId(), "Your order #" + order.getId() + " has been cancelled. If this was a mistake, please start a new order."); // Send cancellation message
        return redirectToWhatsApp();
    }

    private RedirectView redirectToWhatsApp() {
        String whatsappUrl = "https://wa.me/" + whatsAppProperties.getWhatsappNumber();
        return new RedirectView(whatsappUrl);
    }
}
