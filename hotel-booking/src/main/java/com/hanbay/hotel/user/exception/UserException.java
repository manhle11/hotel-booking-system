package com.hanbay.hotel.user.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public abstract class UserException extends DomainException {

    protected UserException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public String getCode() {
        return getErrorCode();
    }
}
