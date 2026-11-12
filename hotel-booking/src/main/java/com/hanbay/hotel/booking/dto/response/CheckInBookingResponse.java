package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CheckInBookingResponse(
        UUID bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        Guest primaryGuest,
        RoomTypeSummary roomType,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int adultCount,
        int childCount,
        long totalAmount,
        long paidAmount,
        long outstandingAmount,
        boolean canCheckIn,
        boolean earlyArrivalRequired,
        AssignedRoomResponse assignedRoom,
        Instant checkedInAt) {
    public record Guest(String fullName, String phone, String email) {}

    public record RoomTypeSummary(UUID id, String code, String name) {}
}
