package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingChangeRequest;
import com.hanbay.hotel.booking.repository.BookingChangeRequestRepository;
import com.hanbay.hotel.user.domain.User;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BookingChangeLifecycle {
    private final BookingChangeRequestRepository requests;

    /** Booking/order locks must already be held, as in change-request approval. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void afterCancellation(Booking booking, User actor, Instant now) {
        requests.findByScopeKeyAndStatus(BookingChangeRequest.scopeOf(booking), BookingChangeRequest.Status.PENDING)
                .filter(request -> request.getType() == BookingChangeRequest.Type.RESCHEDULE)
                .flatMap(request -> requests.findByIdForUpdate(request.getId()))
                .ifPresent(request -> request.endAfterCancellation(actor, now));
    }
}
