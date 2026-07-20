package com.fincore.api.integration.paystack.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaystackWebhookEvent(String event, WebhookData data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookData(String reference) {}
}
