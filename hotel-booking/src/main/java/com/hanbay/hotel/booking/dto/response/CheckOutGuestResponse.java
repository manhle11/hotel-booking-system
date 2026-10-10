package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.UUID;

public record CheckOutGuestResponse(
        UUID bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        AssignedRoomResponse room,
        Instant checkedOutAt,
        long totalAmount,
        long paidAmount,
        long outstandingAmount,
        long additionalChargeAmount,
        String additionalChargeReason,
        long collectedAmount) {}
