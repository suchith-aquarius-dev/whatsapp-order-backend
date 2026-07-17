package com.example.waorder.service;

import com.example.waorder.config.WhatsAppProperties;
import com.example.waorder.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class WhatsAppApiService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppApiService.class);

    private final RestClient restClient;
    private final WhatsAppProperties properties;
    private final LinkTokenService linkTokenService;

    public WhatsAppApiService(RestClient restClient, WhatsAppProperties properties, LinkTokenService linkTokenService) {
        this.restClient = restClient;
        this.properties = properties;
        this.linkTokenService = linkTokenService;
    }

    /**
     * Step 1: user has just messaged us. Reply with a CTA-URL button that
     * opens our order form, with a signed token identifying them embedded
     * in the URL. This works freely inside the 24h customer-service window,
     * no template approval required.
     */
    public void sendOrderFormLink(String waId, Long orderId) { // Modified to accept orderId
        String token = linkTokenService.generateToken(waId);
        // Include orderId in the formUrl
        String formUrl = properties.getAppBaseUrl() + "/order?token=" + token + "&orderId=" + orderId;

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", waId,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "cta_url",
                        "body", Map.of("text", "Thanks for reaching out! Tap below to place your order."),
                        "action", Map.of(
                                "name", "cta_url",
                                "parameters", Map.of(
                                        "display_text", "Order Now",
                                        "url", formUrl
                                )
                        )
                )
        );
        postToGraphApi(body);
    }

    /**
     * Step 2: form was submitted. If we're still inside the 24h session
     * window this sends a free-form interactive message with a "Pay Now"
     * button linking to the hosted checkout page for this order.
     */
    public void sendPayNowMessage(Order order) {
        String payUrl = properties.getPaymentBaseUrl() + "/" + order.getId();

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", order.getWaId(),
                "type", "interactive",
                "interactive", Map.of(
                        "type", "cta_url",
                        "body", Map.of("text", "Your order #" + order.getId() + " total is ₹"
                                + order.getTotalAmount() + ". Tap below to pay."),
                        "action", Map.of(
                                "name", "cta_url",
                                "parameters", Map.of(
                                        "display_text", "Pay Now",
                                        "url", payUrl
                                )
                        )
                )
        );
        postToGraphApi(body);
    }

    /**
     * Sends a simple text message to the specified WhatsApp ID.
     * This can be used for order confirmations, cancellations, etc.
     */
    public void sendTextMessage(String waId, String message) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", waId,
                "type", "text",
                "text", Map.of("body", message)
        );
        postToGraphApi(body);
    }

    /**
     * Fallback for when we're OUTSIDE the 24h session window: an approved
     * Utility template with a dynamic URL button must be used instead of a
     * free-form message. Template must already exist & be approved in Meta
     * Business Manager, with one URL button whose {{1}} is the order id.
     */
    public void sendPayNowTemplate(Order order) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", order.getWaId(),
                "type", "template",
                "template", Map.of(
                        "name", properties.getPaymentTemplateName(),
                        "language", Map.of("code", "en"),
                        "components", List.of(
                                Map.of(
                                        "type", "button",
                                        "sub_type", "url",
                                        "index", "0",
                                        "parameters", List.of(
                                                Map.of("type", "text", "text", String.valueOf(order.getId()))
                                        )
                                )
                        )
                )
        );
        postToGraphApi(body);
    }

    private void postToGraphApi(Map<String, Object> body) {
        String url = properties.getApiBaseUrl() + "/" + properties.getPhoneNumberId() + "/messages";
        try {
            String response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("WhatsApp API response: {}", response);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message: {}", e.getMessage(), e);
            // In production: push to a retry queue instead of swallowing this
        }
    }
}
