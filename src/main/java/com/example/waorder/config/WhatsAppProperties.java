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
}
