package com.hanbay.hotel.user.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AccountManagementException extends DomainException {
    public AccountManagementException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}
