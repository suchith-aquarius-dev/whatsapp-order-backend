package com.example.waorder.controller;

import com.example.waorder.config.WhatsAppProperties;
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

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppApiService whatsAppApiService) {
        this.properties = properties;
        this.whatsAppApiService = whatsAppApiService;
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
                        String type = message.path("type").asText();
                        log.info("Inbound WhatsApp message from {} type={}", waId, type);

                        // Any inbound message (text, or tapping a quick reply) triggers
                        // sending the order-form link. Customize this condition to match
                        // on specific keywords ("order", "menu") if needed.
                        whatsAppApiService.sendOrderFormLink(waId);
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
