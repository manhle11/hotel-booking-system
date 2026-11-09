package com.hanbay.hotel.room.dto.request;

import com.hanbay.hotel.room.domain.BedType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoomTypeBedRequest(@NotNull BedType bedType, @Min(1) int quantity) {}
