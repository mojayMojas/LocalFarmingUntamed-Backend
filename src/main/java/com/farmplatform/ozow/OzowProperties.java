package com.farmplatform.ozow;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ozow settings, bound from the "ozow" section of application.yml (environment variables OZOW_*).
 * Fill apiKey, privateKey and siteCode with the values from the Ozow merchant dashboard; until then the
 * backend runs in mock mode.
 */
@ConfigurationProperties(prefix = "ozow")
public record OzowProperties(String apiKey,
                             String privateKey,
                             String siteCode,
                             boolean test,
                             boolean mockEnabled,
                             String baseUrl,
                             String notifyUrl,
                             String successUrl,
                             String cancelUrl,
                             String errorUrl) {}
