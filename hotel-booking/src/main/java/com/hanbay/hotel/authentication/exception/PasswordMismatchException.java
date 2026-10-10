package com.hanbay.hotel.authentication.exception;

import com.hanbay.hotel.common.exception.DomainException;

public class PasswordMismatchException extends DomainException {

    public PasswordMismatchException() {
        super(
                "PASSWORD_MISMATCH",
                "Mật khẩu và xác nhận mật khẩu không khớp",
                org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
