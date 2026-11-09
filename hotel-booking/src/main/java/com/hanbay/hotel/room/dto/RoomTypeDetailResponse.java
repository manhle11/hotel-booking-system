package com.hanbay.hotel.room.dto;

import com.hanbay.hotel.room.domain.BedConfigurationStatus;
import com.hanbay.hotel.room.domain.RoomViewType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RoomTypeDetailResponse(
        UUID id,
        String name,
        String shortDescription,
        String description,
        BigDecimal basePrice,
        int maxGuests,
        int capacityAdults,
        int capacityChildren,
        List<String> images,
        List<AmenityDto> amenities,
        String bedType,
        RoomCategoryResponse category,
        List<RoomTypeBedResponse> beds,
        BedConfigurationStatus bedConfigurationStatus,
        BigDecimal areaM2,
        RoomViewType viewType,
        Boolean hasBalcony,
        Boolean highFloor) {}
