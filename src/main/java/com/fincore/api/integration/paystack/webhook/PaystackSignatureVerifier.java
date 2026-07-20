package com.fincore.api.integration.paystack.webhook;

import com.fincore.api.integration.paystack.PaystackProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies the x-paystack-signature header: HMAC-SHA512 of the raw request
 * body, keyed with the account secret key.
 */
@Component
@RequiredArgsConstructor
public class PaystackSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA512";

    private final PaystackProperties properties;

    public boolean isValid(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.secretKey().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
