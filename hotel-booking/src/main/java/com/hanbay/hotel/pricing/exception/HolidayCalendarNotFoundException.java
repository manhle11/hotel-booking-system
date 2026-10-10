package com.hanbay.hotel.pricing.exception;

import com.hanbay.hotel.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class HolidayCalendarNotFoundException extends DomainException {

    public HolidayCalendarNotFoundException() {
        super("HOLIDAY_CALENDAR_NOT_FOUND", "Không tìm thấy ngày lễ hoặc sự kiện.", HttpStatus.NOT_FOUND);
    }
}
