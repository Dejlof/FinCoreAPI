package com.fincore.api.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72,
                message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "Password must contain upper case, lower case and a digit")
        String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number")
        String phoneNumber
) {}
