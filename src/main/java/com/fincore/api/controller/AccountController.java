package com.fincore.api.controller;

import com.fincore.api.dto.request.CreateAccountRequest;
import com.fincore.api.dto.response.AccountResponse;
import com.fincore.api.dto.response.ApiResponse;
import com.fincore.api.dto.response.PagedResponse;
import com.fincore.api.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Accounts", description = "Account creation and enquiry")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Open a new account for the authenticated user")
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = accountService.createAccount(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created", account));
    }

    @Operation(summary = "List the authenticated user's accounts (paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AccountResponse>>> list(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(accountService.getUserAccounts(principal.getUsername(), pageable)));
    }

    @Operation(summary = "Get a single account by account number")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getOne(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getByAccountNumber(principal.getUsername(), accountNumber)));
    }
}
