package com.hanbay.hotel.authentication.service;

import com.hanbay.hotel.authentication.dto.request.ResetPasswordRequest;
import com.hanbay.hotel.authentication.dto.response.ResetPasswordResponse;
import com.hanbay.hotel.authentication.dto.response.ResetTokenVerificationResponse;
import com.hanbay.hotel.user.domain.User;

public interface PasswordResetTokenService {

    String issueToken(User user);

    void revokeToken(String rawToken);

    ResetTokenVerificationResponse verifyToken(String rawToken);

    ResetPasswordResponse resetPassword(ResetPasswordRequest request);
}
