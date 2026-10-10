package com.hanbay.hotel.media;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ImageStorageException extends DomainException {
    public ImageStorageException(String message, Exception cause) {
        super("IMAGE_STORAGE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}
