package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.request.CancelBookingRequest;
import com.hanbay.hotel.booking.dto.request.CreatePendingBookingRequest;
import com.hanbay.hotel.booking.dto.response.BookingHistoryItemResponse;
import com.hanbay.hotel.booking.dto.response.CancelBookingResponse;
import com.hanbay.hotel.booking.dto.response.CancellationEligibilityResponse;
import com.hanbay.hotel.booking.dto.response.CreatePendingBookingResponse;
import com.hanbay.hotel.common.response.PageResponse;

public interface BookingService {
    CreatePendingBookingResponse createPendingBooking(CreatePendingBookingRequest request);

    CreatePendingBookingResponse updatePendingBooking(
            java.util.UUID bookingId, CreatePendingBookingRequest request, String bookingAccessToken);

    PageResponse<BookingHistoryItemResponse> getCurrentUserBookingHistory(int page, int size, BookingStatus status);

    BookingHistoryItemResponse getCurrentUserBooking(String bookingCode);

    CancellationEligibilityResponse getCancellationEligibility(String bookingCode);

    CancelBookingResponse cancelBooking(java.util.UUID bookingId, CancelBookingRequest request);

    /** Compatibility for internal callers; HTTP command endpoints use bookingId. */
    CancelBookingResponse cancelBooking(String bookingCode, CancelBookingRequest request);
}
