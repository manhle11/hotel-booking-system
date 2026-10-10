package com.hanbay.hotel.authentication.service;

import com.hanbay.hotel.authentication.dto.request.RefreshTokenRequest;
import com.hanbay.hotel.authentication.dto.response.RefreshTokenResponse;
import com.hanbay.hotel.user.domain.User;

public interface RefreshTokenService {

    RefreshTokenResponse refreshAccessToken(RefreshTokenRequest request);

    String issueRefreshToken(User user);
}
