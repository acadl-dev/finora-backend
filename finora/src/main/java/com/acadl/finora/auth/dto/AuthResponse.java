package com.acadl.finora.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {}