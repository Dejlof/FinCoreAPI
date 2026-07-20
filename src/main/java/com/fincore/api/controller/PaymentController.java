package com.fincore.api.controller;

import com.fincore.api.dto.request.FundAccountRequest;
import com.fincore.api.dto.response.ApiResponse;
import com.fincore.api.dto.response.FundAccountResponse;
import com.fincore.api.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Fund accounts via external payment providers")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Initiate wallet funding via Paystack (idempotent via Idempotency-Key header); returns a checkout URL to redirect the user to")
    @PostMapping("/fund")
    public ResponseEntity<ApiResponse<FundAccountResponse>> fund(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody FundAccountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        FundAccountResponse response = paymentService.initiateFunding(principal.getUsername(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Payment initialized", response));
    }
}
