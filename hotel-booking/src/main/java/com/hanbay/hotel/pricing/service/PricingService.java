package com.hanbay.hotel.pricing.service;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.pricing.dto.result.PricingResult;
import com.hanbay.hotel.pricing.dto.result.RoomPricingResult;
import com.hanbay.hotel.pricing.dto.result.StayPriceResult;
import com.hanbay.hotel.room.domain.RoomType;
import java.time.LocalDate;
import java.util.UUID;

public interface PricingService {
    PricingResult calculatePrice(Booking booking);

    RoomPricingResult calculateRoomPrice(RoomType roomType, LocalDate checkIn, LocalDate checkOut);

    StayPriceResult calculateStayPrice(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);
}
