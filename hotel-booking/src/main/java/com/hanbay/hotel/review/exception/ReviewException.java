package com.hanbay.hotel.review.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ReviewException extends DomainException {
    public ReviewException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
