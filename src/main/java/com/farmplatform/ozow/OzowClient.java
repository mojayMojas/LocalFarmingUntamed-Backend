package com.farmplatform.ozow;

import com.farmplatform.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client for Ozow's payment request API.
 *
 * Request authentication = "ApiKey" header + a SHA512 "HashCheck" field: the request values are concatenated in
 * Ozow's documented order, the merchant PrivateKey is appended, the whole string is lower-cased and hashed.
 * Notifications sent to NotifyUrl are verified the same way. If Ozow's documentation changes, this is the only
 * class that has to change.
 */
@Component
public class OzowClient {
    private static final Logger log = LoggerFactory.getLogger(OzowClient.class);

    /** Fields of the NotifyUrl callback, in the order they take part in the hash (Hash itself is excluded). */
    private static final String[] NOTIFICATION_HASH_FIELDS = {
            "SiteCode", "TransactionId", "TransactionReference", "Amount", "Status",
            "Optional1", "Optional2", "Optional3", "Optional4", "Optional5",
            "CurrencyCode", "IsTest", "StatusMessage"
    };

    private final OzowProperties props;
    private final RestClient http = RestClient.create();

    public OzowClient(OzowProperties props) {
        this.props = props;
    }

    public record PaymentLink(String url, String paymentRequestId) {}

    /** True while credentials have not been supplied: payments go to the built-in fake payment page. */
    public boolean isMock() {
        return blank(props.apiKey()) || blank(props.privateKey()) || blank(props.siteCode());
    }

    /** Mock payments are a development aid only; switch them off in production with OZOW_MOCK_ENABLED=false. */
    public boolean isMockAllowed() {
        return props.mockEnabled();
    }

    public PaymentLink createPaymentRequest(String transactionReference, String bankReference, BigDecimal amount) {
        String amt = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String isTest = String.valueOf(props.test());

        String hash = sha512Lower(props.siteCode() + "ZA" + "ZAR" + amt + transactionReference + bankReference
                + props.cancelUrl() + props.errorUrl() + props.successUrl() + props.notifyUrl()
                + isTest + props.privateKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("SiteCode", props.siteCode());
        body.put("CountryCode", "ZA");
        body.put("CurrencyCode", "ZAR");
        body.put("Amount", new BigDecimal(amt));
        body.put("TransactionReference", transactionReference);
        body.put("BankReference", bankReference);
        body.put("CancelUrl", props.cancelUrl());
        body.put("ErrorUrl", props.errorUrl());
        body.put("SuccessUrl", props.successUrl());
        body.put("NotifyUrl", props.notifyUrl());
        body.put("IsTest", props.test());
        body.put("HashCheck", hash);

        try {
            JsonNode res = http.post()
                    .uri(props.baseUrl() + "/postpaymentrequest")
                    .header("ApiKey", props.apiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String url = text(res, "url");
            if (url == null || url.isBlank()) {
                log.error("Ozow returned no payment URL for reference {}: {}", transactionReference, res);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Ozow did not return a payment link");
            }
            return new PaymentLink(url, text(res, "paymentRequestId"));
        } catch (RestClientException ex) {
            log.error("Ozow payment request failed for reference {}: {}", transactionReference, ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach Ozow. Please try again shortly.");
        }
    }

    /** Verifies the Hash sent in an Ozow notification. Keys of {@code params} must be case-insensitive. */
    public boolean verifyNotificationHash(Map<String, String> params) {
        String received = params.get("Hash");
        if (received == null || received.isBlank()) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (String field : NOTIFICATION_HASH_FIELDS) {
            String v = params.get(field);
            if (v != null) {
                sb.append(v);
            }
        }
        sb.append(props.privateKey());
        byte[] expected = sha512Lower(sb.toString()).getBytes(StandardCharsets.UTF_8);
        byte[] actual = received.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public boolean siteCodeMatches(String siteCode) {
        return siteCode != null && siteCode.equalsIgnoreCase(props.siteCode());
    }

    /**
     * Asks Ozow directly for the status of a transaction (Ozow recommends confirming a notification this way
     * instead of trusting the redirect or the callback alone). Empty if Ozow has no such transaction.
     */
    public Optional<String> getTransactionStatus(String transactionReference) {
        try {
            JsonNode res = http.get()
                    .uri(props.baseUrl() + "/GetTransactionByReference?siteCode={s}&transactionReference={r}&isTest={t}",
                            props.siteCode(), transactionReference, props.test())
                    .header("ApiKey", props.apiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode first = (res != null && res.isArray() && !res.isEmpty()) ? res.get(0) : res;
            return Optional.ofNullable(text(first, "status"));
        } catch (RestClientException ex) {
            log.error("Ozow status check failed for reference {}: {}", transactionReference, ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not confirm the transaction with Ozow");
        }
    }

    // ---------- helpers ----------

    private static String text(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String n = names.next();
            if (n.equalsIgnoreCase(name)) {
                JsonNode v = node.get(n);
                return (v == null || v.isNull()) ? null : v.asText();
            }
        }
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static String sha512Lower(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 not available", e);
        }
    }
}
