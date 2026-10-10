package com.hanbay.hotel.booking.policy;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import java.time.Instant;

public interface BookingCancellationPolicy {
    CancellationDecision evaluate(Booking booking, PaymentStatus paymentStatus, Instant now);
}
