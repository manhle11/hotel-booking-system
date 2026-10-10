package com.hanbay.hotel.authentication.dto.response;

public record RefreshTokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {}
