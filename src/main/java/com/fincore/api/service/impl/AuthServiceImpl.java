package com.fincore.api.service.impl;

import com.fincore.api.dto.request.LoginRequest;
import com.fincore.api.dto.request.RefreshTokenRequest;
import com.fincore.api.dto.request.RegisterRequest;
import com.fincore.api.dto.response.AuthResponse;
import com.fincore.api.dto.response.UserResponse;
import com.fincore.api.entity.RefreshToken;
import com.fincore.api.entity.User;
import com.fincore.api.exception.ApiException;
import com.fincore.api.exception.DuplicateResourceException;
import com.fincore.api.mapper.UserMapper;
import com.fincore.api.repository.RefreshTokenRepository;
import com.fincore.api.repository.UserRepository;
import com.fincore.api.security.CustomUserDetails;
import com.fincore.api.security.JwtService;
import com.fincore.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phoneNumber(request.phoneNumber())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getId());
        return userMapper.toResponse(user);
    }

    /**
     * Deliberately NOT annotated with @Transactional: if it were, the
     * BadCredentialsException would mark the transaction rollback-only and the
     * failed-attempt counter written in registerFailedAttempt would be lost.
     * Each repository call below commits independently.
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.isLocked()) {
            throw new LockedException("Account locked");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException e) {
            registerFailedAttempt(user);
            throw e;
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user = userRepository.save(user);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (stored.isExpired()) {
            throw new ApiException("Refresh token expired. Please log in again.", HttpStatus.UNAUTHORIZED);
        }

        // Rotation: revoke the used token and issue a fresh pair
        stored.setRevoked(true);
        return issueTokens(stored.getUser());
    }

    @Override
    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email)
                .ifPresent(user -> refreshTokenRepository.revokeAllByUserId(user.getId()));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(
                new CustomUserDetails(user),
                Map.of("role", user.getRole().name(), "uid", user.getId().toString()));

        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .build());

        return AuthResponse.of(accessToken, rawRefreshToken,
                jwtService.getAccessTokenExpirationSeconds(), userMapper.toResponse(user));
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            log.warn("User {} locked after {} failed login attempts", user.getId(), attempts);
        }
        userRepository.save(user);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
