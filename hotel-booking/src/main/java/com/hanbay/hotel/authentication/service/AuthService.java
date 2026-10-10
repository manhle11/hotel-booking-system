package com.hanbay.hotel.authentication.service;

import com.hanbay.hotel.authentication.dto.request.ForgotPasswordRequest;
import com.hanbay.hotel.authentication.dto.request.GuestRegisterRequest;
import com.hanbay.hotel.authentication.dto.request.LoginRequest;
import com.hanbay.hotel.authentication.dto.request.ResetPasswordRequest;
import com.hanbay.hotel.authentication.dto.response.ForgotPasswordResponse;
import com.hanbay.hotel.authentication.dto.response.GuestRegisterResponse;
import com.hanbay.hotel.authentication.dto.response.LoginResponse;
import com.hanbay.hotel.authentication.dto.response.ResetPasswordResponse;
import com.hanbay.hotel.authentication.dto.response.ResetTokenVerificationResponse;

public interface AuthService {

    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    ResetTokenVerificationResponse verifyResetToken(String rawToken);

    ResetPasswordResponse resetPassword(ResetPasswordRequest request);

    LoginResponse login(LoginRequest request, String clientIp);

    GuestRegisterResponse registerGuest(GuestRegisterRequest request);
}
