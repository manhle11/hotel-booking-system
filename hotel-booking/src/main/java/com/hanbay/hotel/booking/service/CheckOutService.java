package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.dto.request.CheckOutGuestRequest;
import com.hanbay.hotel.booking.dto.response.CheckOutGuestResponse;
import java.util.UUID;

public interface CheckOutService {

    CheckOutGuestResponse checkOut(UUID bookingId, CheckOutGuestRequest request);
}
