package com.hanbay.hotel.room.dto.response;

import com.hanbay.hotel.room.domain.BedConfigurationStatus;
import com.hanbay.hotel.room.domain.RoomViewType;
import com.hanbay.hotel.room.dto.RoomCategoryResponse;
import com.hanbay.hotel.room.dto.RoomTypeBedResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AvailableRoomTypeResponse(
        UUID id,
        String code,
        String name,
        String description,
        long basePrice,
        int maxGuests,
        int adultCapacity,
        int childCapacity,
        String bedType,
        BigDecimal areaM2,
        String imageUrl,
        long availableRooms,
        long readyRooms,
        int totalNights,
        long roomSubtotal,
        long roomDiscount,
        long roomTotal,
        List<NightlyPriceResponse> nightlyPrices,
        RoomCategoryResponse category,
        List<RoomTypeBedResponse> beds,
        BedConfigurationStatus bedConfigurationStatus,
        RoomViewType viewType,
        Boolean hasBalcony,
        Boolean highFloor) {}
