package com.hanbay.hotel.pricing.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PriceRuleConflictException extends DomainException {
    public PriceRuleConflictException() {
        super("PRICE_RULE_CONFLICT", "Xung dot du lieu khi cap nhat quy tac gia", HttpStatus.CONFLICT);
    }
}
