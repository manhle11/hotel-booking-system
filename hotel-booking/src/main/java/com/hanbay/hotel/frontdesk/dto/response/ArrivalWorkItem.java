package com.hanbay.hotel.frontdesk.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.response.ArrivalProgress;
import java.time.*;
import java.util.UUID;

public record ArrivalWorkItem(
        UUID bookingId,
        String bookingCode,
        PrimaryGuestSummaryResponse primaryGuest,
        ArrivalRoomTypeResponse roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int adultCount,
        int childCount,
        BookingStatus bookingStatus,
        Instant createdAt,
        ArrivalProgress progress,
        String zone,
        Instant serverNow,
        String orderCode,
        int roomCount,
        Integer orderRoomNumber,
        Instant resolvedAt) {}
