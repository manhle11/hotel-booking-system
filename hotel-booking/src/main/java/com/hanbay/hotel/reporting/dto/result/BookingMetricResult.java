package com.hanbay.hotel.reporting.dto.result;

public record BookingMetricResult(
        long totalBookings,
        long pendingBookings,
        long confirmedBookings,
        long checkedInBookings,
        long checkedOutBookings,
        long cancelledBookings,
        long expiredBookings,
        long totalBookingValue,
        long noShowBookings,
        long noShowFeeAmount) {
    public BookingMetricResult(
            long totalBookings,
            long pendingBookings,
            long confirmedBookings,
            long checkedInBookings,
            long checkedOutBookings,
            long cancelledBookings,
            long expiredBookings,
            long totalBookingValue) {
        this(
                totalBookings,
                pendingBookings,
                confirmedBookings,
                checkedInBookings,
                checkedOutBookings,
                cancelledBookings,
                expiredBookings,
                totalBookingValue,
                0,
                0);
    }
}
