package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.dto.request.CheckInGuestRequest;
import com.hanbay.hotel.booking.dto.response.AvailablePhysicalRoomResponse;
import com.hanbay.hotel.booking.dto.response.CheckInGuestResponse;
import java.util.List;

public interface CheckInService {

    CheckInGuestResponse checkIn(String bookingCode, CheckInGuestRequest request);

    List<AvailablePhysicalRoomResponse> getAvailableRooms(String bookingCode);
}
