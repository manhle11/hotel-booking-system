package com.hanbay.hotel.pricing.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HolidayCalendarManagementResponse(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
