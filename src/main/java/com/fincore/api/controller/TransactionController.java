package com.fincore.api.controller;

import com.fincore.api.dto.request.DepositRequest;
import com.fincore.api.dto.request.TransferRequest;
import com.fincore.api.dto.response.ApiResponse;
import com.fincore.api.dto.response.PagedResponse;
import com.fincore.api.dto.response.TransactionResponse;
import com.fincore.api.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transactions", description = "Money movement and history")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Transfer between accounts (idempotent via Idempotency-Key header)")
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse txn = transactionService.transfer(principal.getUsername(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Transfer completed", txn));
    }

    @Operation(summary = "Deposit into an account (admin/teller only)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse txn = transactionService.deposit(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Deposit completed", txn));
    }

    @Operation(summary = "Get a transaction by its reference")
    @GetMapping("/{reference}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByReference(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.ok(
                transactionService.getByReference(principal.getUsername(), reference)));
    }

    @Operation(summary = "Paginated transaction history for an account")
    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> history(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.ok(
                transactionService.getAccountHistory(principal.getUsername(), accountNumber, pageable)));
    }
}
