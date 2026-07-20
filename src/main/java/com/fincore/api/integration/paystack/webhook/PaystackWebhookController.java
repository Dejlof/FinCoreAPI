package com.fincore.api.integration.paystack.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Paystack event callbacks. Never trusts the payload amount/status
 * directly - charge.success only triggers a server-side verify call before
 * any balance is credited.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/paystack")
@RequiredArgsConstructor
public class PaystackWebhookController {

    private final PaystackSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        if (!signatureVerifier.isValid(payload, signature)) {
            log.warn("Rejected Paystack webhook with invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PaystackWebhookEvent event;
        try {
            event = objectMapper.readValue(payload, PaystackWebhookEvent.class);
        } catch (JsonProcessingException e) {
            log.warn("Rejected malformed Paystack webhook payload");
            return ResponseEntity.badRequest().build();
        }

        if ("charge.success".equals(event.event()) && event.data() != null) {
            paymentService.confirmFunding(event.data().reference());
            paymentService.settleFunding(event.data().reference());
        }

        return ResponseEntity.ok().build();
    }
}
