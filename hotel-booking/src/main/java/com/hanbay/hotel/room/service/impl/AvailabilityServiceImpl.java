package com.hanbay.hotel.room.service.impl;

import com.hanbay.hotel.booking.exception.RoomNotAvailableException;
import com.hanbay.hotel.room.service.AvailabilityService;
import com.hanbay.hotel.room.service.RoomAvailabilityCalculator;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService {

    private final RoomAvailabilityCalculator roomAvailabilityCalculator;

    @Override
    public void ensureAvailable(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        if (roomAvailabilityCalculator.countAvailableRooms(roomTypeId, checkIn, checkOut) <= 0) {
            throw new RoomNotAvailableException("Loại phòng đã hết phòng trống trong khoảng thời gian này.");
        }
    }
}
