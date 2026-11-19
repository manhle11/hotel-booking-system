package com.hanbay.hotel.booking.policy;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingNight;
import com.hanbay.hotel.booking.exception.BookingValidationException;

public final class ArrivalPolicy {
    public static final String VERSION = "HANBAY-ARRIVAL-2026-11-V1";

    private ArrivalPolicy() {}

    public static long standardFee(Booking booking) {
        if (UnifiedBookingPolicy.applies(booking)) return booking.getOriginalFirstNightAmount();
        return booking.getNights().stream()
                .filter(n -> n.getStayDate().equals(booking.getCheckInDate()))
                .mapToLong(BookingNight::getPricePerNight)
                .findFirst()
                .orElseThrow(() -> new BookingValidationException("Thiếu giá đêm đầu đã chốt; cần đối soát booking."));
    }

    public static void accept(Booking booking, String version) {
        if (version == null) return; // Legacy integrations require manager review when resolving a no-show.
        if (!VERSION.equals(version) && !UnifiedBookingPolicy.VERSION.equals(version))
            throw new BookingValidationException("Chính sách đến muộn đã thay đổi; vui lòng đọc lại.");
        booking.acceptArrivalPolicy(version);
    }
}
