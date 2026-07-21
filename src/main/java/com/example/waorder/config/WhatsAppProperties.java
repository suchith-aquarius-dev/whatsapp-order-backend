package com.example.waorder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppProperties {
    private String apiBaseUrl;
    private String phoneNumberId;
    private String accessToken;
    private String webhookVerifyToken;
    private String linkSigningSecret;
    private String appBaseUrl;
    private String paymentBaseUrl;
    private String paymentTemplateName;
    private String whatsappNumber; // Added for wa.me links
    private String upiId; // New property for UPI ID
    private String upiQrCodeImageUrl; // New property for UPI QR Code Image URL
}
