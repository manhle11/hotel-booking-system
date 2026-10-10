package com.hanbay.hotel.authentication.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RefreshTokenExpiredException extends DomainException {

    public RefreshTokenExpiredException() {
        super("AUTH_REFRESH_TOKEN_EXPIRED", "Refresh token đã hết hạn", HttpStatus.UNAUTHORIZED);
    }
}
