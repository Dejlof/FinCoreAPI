package com.fincore.api.service;

import com.fincore.api.dto.request.LoginRequest;
import com.fincore.api.dto.request.RefreshTokenRequest;
import com.fincore.api.dto.request.RegisterRequest;
import com.fincore.api.dto.response.AuthResponse;
import com.fincore.api.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String email);
}
