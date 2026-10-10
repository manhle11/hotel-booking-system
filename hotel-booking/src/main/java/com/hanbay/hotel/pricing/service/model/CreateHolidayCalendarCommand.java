package com.hanbay.hotel.pricing.service.model;

import java.time.LocalDate;

public record CreateHolidayCalendarCommand(String name, LocalDate startDate, LocalDate endDate) {}
