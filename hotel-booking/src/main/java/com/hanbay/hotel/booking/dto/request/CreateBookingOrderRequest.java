package com.hanbay.hotel.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBookingOrderRequest(
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Valid GuestInfo guestInfo,
        @NotNull @Size(min = 1, max = 10, message = "Mỗi đơn được đặt từ 1 đến 10 phòng") List<@NotNull @Valid RoomSelection> rooms) {
    public record RoomSelection(
            @NotNull UUID roomTypeId,
            @Min(1) @Max(20) int adults,
            @Min(0) @Max(20) int children,
            @Size(max = 20) List<@NotNull @Valid AddOnSelectionRequest> addOns,
            @Size(max = 20) List<@NotNull @Min(0) Integer> childAges) {
        public RoomSelection(UUID roomTypeId, int adults, int children, List<AddOnSelectionRequest> addOns) {
            this(roomTypeId, adults, children, addOns, null);
        }
    }
}
