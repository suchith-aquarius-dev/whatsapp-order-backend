package com.example.waorder.service;

import com.example.waorder.config.WhatsAppProperties;
import com.example.waorder.model.Order;
import com.example.waorder.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.format.DateTimeFormatter;
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
     * Sends an interactive message with a CTA-URL button that
     * opens our order form, with a signed token identifying them embedded
     * in the URL.
     */
    public void sendOrderFormLink(String waId, Long orderId) {
        String token = linkTokenService.generateToken(waId);
        String formUrl = properties.getAppBaseUrl() + "/order?token=" + token + "&orderId=" + orderId;

        String orderFormDescription = "Tap below to browse our menu and place your order!";

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", waId,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "cta_url",
                        "body", Map.of("text", orderFormDescription),
                        "action", Map.of(
                                "name", "cta_url",
                                "parameters", Map.of(
                                        "display_text", "Menu",
                                        "url", formUrl
                                )
                        )
                )
        );
        postToGraphApi(body);
    }

    /**
     * Sends an interactive welcome message with an "Order" button.
     */
    public void sendWelcomeMessageWithOrderButton(String waId) {
        String welcomeText = "Welcome to Morav’s Patisserie! 🥐✨\n\n" +
                             "How can we help you today?\n\n" +
                             "You can click the below button to Order.\n\n" +
                             "To speak with our team directly, call us at [+91-8123144096]";

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", waId,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", welcomeText),
                        "action", Map.of(
                                "buttons", List.of(
                                        Map.of(
                                                "type", "reply",
                                                "reply", Map.of(
                                                        "id", "ORDER_BUTTON_CLICK", // Custom ID for our webhook to recognize
                                                        "title", "Order"
                                                )
                                        )
                                )
                        )
                )
        );
        postToGraphApi(body);
    }

    /**
     * Step 2: form was submitted. Send a detailed order summary with "Pay Now"
     * and "Cancel Request" buttons.
     *
     * This method is being replaced by sendOrderConfirmationWithUpiPayment.
     */
    @Deprecated
    public void sendPayNowMessage(Order order) {
        // This method is no longer used as per new requirements.
        // The logic has been moved and adapted into sendOrderConfirmationWithUpiPayment.
        log.warn("sendPayNowMessage is deprecated and should not be called. Order ID: {}", order.getId());
    }

    /**
     * Sends the order confirmation details followed by UPI payment instructions.
     */
    public void sendOrderConfirmationWithUpiPayment(Order order) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        // 1. Build and send the detailed order summary message (text only)
        StringBuilder orderSummaryBuilder = new StringBuilder();
        orderSummaryBuilder.append("Thank you for your order! Here are the details:\n\n");
        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItem item = order.getItems().get(i);
            orderSummaryBuilder.append(String.format("%d. %s (x%d) - ₹%s\n",
                    i + 1, item.getProductName(), item.getQuantity(), item.getPrice()));
        }
        orderSummaryBuilder.append("\nTotal: ₹").append(order.getTotalAmount());
        if (order.getDeliveryDate() != null) {
            orderSummaryBuilder.append("\nDelivery Date: ").append(order.getDeliveryDate().format(dateFormatter));
            if (order.getDeliveryTime() != null) {
                orderSummaryBuilder.append("\nDelivery Time: ").append(order.getDeliveryTime().format(timeFormatter));
            }
        }
        orderSummaryBuilder.append("\nCustomer Name: ").append(order.getCustomerName());
        orderSummaryBuilder.append("\nOrder ID: ").append(order.getId());

        sendTextMessage(order.getWaId(), orderSummaryBuilder.toString());

        String upiPaymentMessage = String.format(
                "Please pay the total amount of ₹%s to confirm the order. You can scan the QR code or send the amount to the UPI ID: %s",
                order.getTotalAmount(),
                properties.getUpiId()
        );

        // 2. Send the UPI QR code image
        if (properties.getUpiQrCodeImageUrl() != null && !properties.getUpiQrCodeImageUrl().isEmpty()) {
            // Construct the full public URL for the static resource
            String fullUpiQrCodeUrl = properties.getAppBaseUrl() + properties.getUpiQrCodeImageUrl();
            sendImageMessage(order.getWaId(), fullUpiQrCodeUrl, upiPaymentMessage);
        }

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
     * Sends an image message to the specified WhatsApp ID.
     */
    public void sendImageMessage(String waId, String imageUrl, String caption) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", waId,
                "type", "image",
                "image", Map.of(
                        "link", imageUrl,
                        "caption", caption
                )
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
