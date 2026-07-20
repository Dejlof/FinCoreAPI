package com.fincore.api.controller;

import com.fincore.api.dto.request.LoginRequest;
import com.fincore.api.dto.request.RefreshTokenRequest;
import com.fincore.api.dto.request.RegisterRequest;
import com.fincore.api.dto.response.ApiResponse;
import com.fincore.api.dto.response.AuthResponse;
import com.fincore.api.dto.response.UserResponse;
import com.fincore.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Register, login and token management")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful", user));
    }

    @Operation(summary = "Login and receive an access/refresh token pair")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", authService.login(request)));
    }

    @Operation(summary = "Exchange a refresh token for a new token pair (rotation)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(request)));
    }

    @Operation(summary = "Logout - revokes all refresh tokens for the user")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserDetails principal) {
        if (principal != null) {
            authService.logout(principal.getUsername());
        }
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }
}
