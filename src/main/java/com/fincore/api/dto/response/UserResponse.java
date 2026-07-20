package com.fincore.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String role,
        String status,
        Instant createdAt
) {}
