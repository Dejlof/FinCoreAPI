package com.fincore.api.integration.paystack;

import com.fincore.api.integration.paystack.dto.InitializeTransactionData;
import com.fincore.api.integration.paystack.dto.PaystackApiResponse;
import com.fincore.api.integration.paystack.dto.VerifyTransactionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaystackClient {

    private final RestClient paystackRestClient;
    private final WebClient paystackWebClient;

    public InitializeTransactionData initializeTransaction(
            String email, BigDecimal amountNaira, String reference, String callbackUrl) {
        long amountKobo = amountNaira.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        Map<String, Object> body = Map.of(
                "email", email,
                "amount", amountKobo,
                "reference", reference,
                "callback_url", callbackUrl);

        PaystackApiResponse<InitializeTransactionData> response = execute(
                () -> paystackRestClient.post()
                        .uri("/transaction/initialize")
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<PaystackApiResponse<InitializeTransactionData>>() {}),
                "initialize");

        if (response == null || !response.status() || response.data() == null) {
            throw new PaystackException("Paystack rejected the initialize request");
        }
        return response.data();
    }

    public VerifyTransactionData verifyTransaction(String reference) {
        PaystackApiResponse<VerifyTransactionData> response = execute(
                () -> paystackRestClient.get()
                        .uri("/transaction/verify/{reference}", reference)
                        .retrieve()
                        .body(new ParameterizedTypeReference<PaystackApiResponse<VerifyTransactionData>>() {}),
                "verify");

        if (response == null || response.data() == null) {
            throw new PaystackException("Paystack returned an empty verification response");
        }
        return response.data();
    }

    private <T> T execute(java.util.function.Supplier<T> call, String operation) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            log.error("Paystack {} call failed: {} - {}", operation, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaystackException("Unable to complete Paystack " + operation + " request");
        }
    }

    public Mono<VerifyTransactionData> verifyTransactionAsync(String reference) {
        return paystackWebClient.get()
                .uri("/transaction/verify/{reference}", reference)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<PaystackApiResponse<VerifyTransactionData>>() {})
                .mapNotNull(PaystackApiResponse::data)
                .doOnError(ex -> log.error("Paystack verify async call failed for reference {}", reference, ex))
                .onErrorMap(ex -> new PaystackException("Unable to complete Paystack verify request"));
    }

    public Mono<InitializeTransactionData> initializeTransactionAsync(
            String email, BigDecimal amountNaira, String reference, String callbackUrl) {
        long amountKobo = amountNaira.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        Map<String, Object> body = Map.of(
                "email", email,
                "amount", amountKobo,
                "reference", reference,
                "callback_url", callbackUrl);

        return paystackWebClient.post()
                .uri("/transaction/initialize")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<PaystackApiResponse<InitializeTransactionData>>() {})
                .mapNotNull(PaystackApiResponse::data)
                .doOnError(ex -> log.error("Paystack initialize async call failed", ex))
                .onErrorMap(ex -> new PaystackException("Unable to complete Paystack initialize request"));
    }
}
