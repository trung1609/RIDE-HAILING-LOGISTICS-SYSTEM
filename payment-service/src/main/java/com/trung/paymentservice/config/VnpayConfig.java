package com.trung.paymentservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Data
public class VnpayConfig {
    private String tmnCode;
    private String hashSecret;
    private String endpoint;
    private String returnUrl;
}