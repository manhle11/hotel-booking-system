package com.hanbay.hotel.booking.mapper;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.dto.response.CreatePendingBookingResponse;
import com.hanbay.hotel.common.mapper.MapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfig.class)
public interface BookingMapper {

    @Mapping(target = "bookingId", source = "id")
    @Mapping(target = "bookingAccessToken", ignore = true)
    CreatePendingBookingResponse toPendingResponse(Booking booking);
}
