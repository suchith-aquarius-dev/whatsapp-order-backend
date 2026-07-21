package com.example.waorder.controller;

import com.example.waorder.config.WhatsAppProperties;
import com.example.waorder.model.Order;
import com.example.waorder.repository.OrderRepository;
import com.example.waorder.service.WhatsAppApiService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for the Meta WhatsApp Cloud API webhook.
 * Register this URL (e.g. https://yourdomain.com/webhook) in the
 * Meta App Dashboard -> WhatsApp -> Configuration -> Webhook.
 */
@RestController
@RequestMapping("/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties properties;
    private final WhatsAppApiService whatsAppApiService;
    private final OrderRepository orderRepository;

    public WhatsAppWebhookController(WhatsAppProperties properties,
                                     WhatsAppApiService whatsAppApiService,
                                     OrderRepository orderRepository) {
        this.properties = properties;
        this.whatsAppApiService = whatsAppApiService;
        this.orderRepository = orderRepository;
    }

    /**
     * Meta calls this once when you save the webhook config, to verify you
     * control the endpoint. Must echo back "hub.challenge" if the verify
     * token matches what you configured.
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && properties.getWebhookVerifyToken().equals(verifyToken)) {
            log.info("Webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Meta POSTs every inbound message / status update here.
     * We only react to genuine inbound user text/button messages and kick
     * off the order-form link. Everything else (status callbacks, read
     * receipts) is safely ignored.
     */
    @PostMapping
    public ResponseEntity<Void> receiveEvent(@RequestBody JsonNode payload) {
        try {
            JsonNode entries = payload.path("entry");
            for (JsonNode entry : entries) {
                JsonNode changes = entry.path("changes");
                for (JsonNode change : changes) {
                    JsonNode value = change.path("value");
                    JsonNode messages = value.path("messages");
                    if (messages.isMissingNode()) {
                        continue; // e.g. a "statuses" callback, not an inbound message
                    }
                    for (JsonNode message : messages) {
                        String waId = message.path("from").asText();
                        String messageType = message.path("type").asText();
                        log.info("Inbound WhatsApp message from {} type={}", waId, messageType);

                        if ("text".equals(messageType)) {
                            String textBody = message.path("text").path("body").asText();
                            if ("ORDER".equalsIgnoreCase(textBody.trim())) {
                                // User sent "ORDER" text, proceed to send the order form link
                                Order newOrder = new Order();
                                newOrder.setWaId(waId);
                                newOrder.setStatus(Order.OrderStatus.CREATED);
                                Order savedOrder = orderRepository.save(newOrder);
                                whatsAppApiService.sendOrderFormLink(waId, savedOrder.getId());
                            } else {
                                // User sent other text, send the interactive welcome message with button
                                whatsAppApiService.sendWelcomeMessageWithOrderButton(waId);
                            }
                        } else if ("interactive".equals(messageType)) {
                            JsonNode interactive = message.path("interactive");
                            String interactiveType = interactive.path("type").asText();

                            if ("button_reply".equals(interactiveType)) {
                                String buttonId = interactive.path("button_reply").path("id").asText();
                                if ("ORDER_BUTTON_CLICK".equals(buttonId)) {
                                    // User clicked the "Order" button, proceed to send the order form link
                                    Order newOrder = new Order();
                                    newOrder.setWaId(waId);
                                    newOrder.setStatus(Order.OrderStatus.CREATED);
                                    Order savedOrder = orderRepository.save(newOrder);
                                    whatsAppApiService.sendOrderFormLink(waId, savedOrder.getId());
                                } else {
                                    log.info("Unhandled interactive button click with ID: {}", buttonId);
                                }
                            } else {
                                log.info("Unhandled interactive message type: {}", interactiveType);
                            }
                        } else {
                            // Ignore other message types (image, video, audio, etc.)
                            log.info("Ignoring non-text/non-interactive message of type: {}", messageType);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload", e);
            // Still return 200 so Meta doesn't retry-storm us; log for investigation instead
        }
        return ResponseEntity.ok().build();
    }
}
