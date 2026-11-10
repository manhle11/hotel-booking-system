package com.hanbay.hotel.review.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ReviewStayResponse(
        String bookingCode,
        String roomNumber,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Instant checkedInAt,
        Instant checkedOutAt) {}
