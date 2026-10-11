package com.farmplatform.payfast;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payfast")
public record PayfastProperties(
        String merchantId,
        String merchantKey,
        String passphrase,
        boolean sandbox,
        boolean mockEnabled,
        String notifyUrl,
        String returnUrl,
        String cancelUrl) {
}