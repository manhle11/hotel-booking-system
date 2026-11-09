package com.hanbay.hotel.room.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoomCategoryWriteRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String code, @NotBlank @Size(max = 150) String name) {}
