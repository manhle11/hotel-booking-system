package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.UUID;

public record CreatePendingBookingResponse(
        UUID bookingId,
        String bookingCode,
        BookingStatus status,
        long totalAmount,
        Instant expiredAt,
        String bookingAccessToken) {}
