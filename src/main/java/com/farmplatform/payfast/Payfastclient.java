package com.farmplatform.payfast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class Payfastclient {
    private static final Logger log = LoggerFactory.getLogger(Payfastclient.class);
    private final PayfastProperties props;
    private final RestClient http = RestClient.create();

    public Payfastclient(PayfastProperties props) {
        this.props = props;
    }

    /** When true, the fake payment page is used instead of PayFast (development only). */
    public boolean isMock() {
        return props.mockEnabled();
    }

    private String host() {
        return props.sandbox() ? "https://sandbox.payfast.co.za" : "https://www.payfast.co.za";
    }

    /** Builds the URL to redirect the customer to. paymentRef must be unique per payment. */
    public String buildPaymentUrl(String paymentRef, BigDecimal amount, String itemName) {
        Map<String, String> p = new LinkedHashMap<>();
        // Order matters: the signature is built in this exact order.
        p.put("merchant_id", props.merchantId());
        p.put("merchant_key", props.merchantKey());
        p.put("return_url", props.returnUrl());
        p.put("cancel_url", props.cancelUrl());
        p.put("notify_url", props.notifyUrl());
        p.put("m_payment_id", paymentRef);
        p.put("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        p.put("item_name", itemName);

        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            if (qs.length() > 0) qs.append('&');
            qs.append(e.getKey()).append('=').append(enc(e.getValue().trim()));
        }
        String signature = md5(withPassphrase(qs.toString()));
        return host() + "/eng/process?" + qs + "&signature=" + signature;
    }

    /** Verifies an ITN. Pass the RAW form body exactly as PayFast sent it. */
    public boolean verifySignature(String rawBody) {
        String received = null;
        StringBuilder sb = new StringBuilder();
        for (String pair : rawBody.split("&")) {
            if (pair.startsWith("signature=")) {
                received = pair.substring("signature=".length());
                continue;
            }
            if (sb.length() > 0) sb.append('&');
            sb.append(pair);
        }
        if (received == null) return false;
        String expected = md5(withPassphrase(sb.toString()));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** Asks PayFast's server to confirm the ITN is genuine. */
    public boolean confirmWithPayfast(String rawBody) {
        try {
            String res = http.post()
                    .uri(host() + "/eng/query/validate")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(rawBody)
                    .retrieve()
                    .body(String.class);
            return res != null && res.trim().equalsIgnoreCase("VALID");
        } catch (RestClientException ex) {
            log.error("PayFast validate call failed: {}", ex.getMessage());
            return false;
        }
    }

    /** Parses the raw ITN body into a map (decoded values). */
    public static Map<String, String> parse(String rawBody) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String pair : rawBody.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    public static boolean isComplete(Map<String, String> itn) {
        return "COMPLETE".equalsIgnoreCase(itn.get("payment_status"));
    }

    public static boolean amountMatches(Map<String, String> itn, BigDecimal expected) {
        try {
            return new BigDecimal(itn.get("amount_gross")).compareTo(expected.setScale(2, RoundingMode.HALF_UP)) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String withPassphrase(String s) {
        String pass = props.passphrase();
        return (pass == null || pass.isBlank()) ? s : s + "&passphrase=" + enc(pass.trim());
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String md5(String input) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}