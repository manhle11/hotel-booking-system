package com.hanbay.hotel.pricing.service.model;

public record HolidayCalendarSearchCriteria(
        int page, int size, String search, Boolean active, String sortBy, String direction) {}
