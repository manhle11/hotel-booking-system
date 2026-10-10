package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.UUID;

public record CheckInGuestResponse(
        UUID bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        AssignedRoomResponse room,
        Instant checkedInAt) {}
