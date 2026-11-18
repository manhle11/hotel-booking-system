package com.hanbay.hotel.booking.policy;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;

/** Collecting a settled fee never restores a reservation or changes inventory. */
public final class ClosedBookingFeePolicy {
    public static final String PAYMENT_PREFIX = "policy-fee:";

    private ClosedBookingFeePolicy() {}

    public static boolean applies(Booking booking) {
        return booking.getStatus() == BookingStatus.NO_SHOW
                || (booking.getStatus() == BookingStatus.CANCELLED
                        && booking.getCancellationOriginalTotal() != null
                        && booking.getCancellationFee() != null
                        && !booking.isCancellationReviewRequired());
    }
}
