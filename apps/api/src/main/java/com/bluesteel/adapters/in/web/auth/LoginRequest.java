package com.bluesteel.adapters.in.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Email String email,
    // Max 128 prevents BCrypt CPU exhaustion on pathologically long inputs
    @NotBlank @Size(max = 128) String password) {}
