package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import java.time.Instant;
import java.util.*;

public record ArrivalProgress(
        Group group,
        List<Reason> reasons,
        Instant expectedAt,
        Instant receptionDeadline,
        boolean canReceive,
        boolean holdPending,
        Instant lastContactAt,
        ArrivalContact.Channel lastContactChannel,
        ArrivalContact.Outcome lastContactOutcome,
        String lastContactNote,
        UUID changeRequestId) {
    public enum Group {
        WAITING,
        ACTION_REQUIRED,
        RESOLVED
    }

    public enum Reason {
        RECEPTION_EXPIRED,
        HOLD_REQUEST,
        CHANGE_REQUEST,
        ETA_AFTER_DEADLINE,
        PAST_ETA
    }

    public static ArrivalProgress of(Booking b, UUID changeRequestId, HotelStayPolicy policy, Instant now) {
        Instant deadline = policy.receptionDeadline(b);
        boolean pending = b.getArrivalHoldRequestedUntil() != null
                && !Objects.equals(b.getArrivalHoldRequestedUntil(), b.getArrivalHoldUntil());
        List<Reason> reasons = new ArrayList<>();
        if (b.getStatus() == BookingStatus.CONFIRMED && b.getCheckedInAt() == null) {
            if (!now.isBefore(deadline)) reasons.add(Reason.RECEPTION_EXPIRED);
            if (pending) reasons.add(Reason.HOLD_REQUEST);
            if (changeRequestId != null) reasons.add(Reason.CHANGE_REQUEST);
            if (b.getExpectedArrivalAt() != null) {
                if (!b.getExpectedArrivalAt().isBefore(deadline)) reasons.add(Reason.ETA_AFTER_DEADLINE);
                if (now.isAfter(b.getExpectedArrivalAt())) reasons.add(Reason.PAST_ETA);
            }
        }
        boolean confirmed = b.getStatus() == BookingStatus.CONFIRMED && b.getCheckedInAt() == null;
        return new ArrivalProgress(
                !confirmed ? Group.RESOLVED : reasons.isEmpty() ? Group.WAITING : Group.ACTION_REQUIRED,
                List.copyOf(reasons),
                b.getExpectedArrivalAt(),
                deadline,
                confirmed && policy.canArrive(b),
                pending,
                b.getArrivalContactAt(),
                b.getArrivalContactChannel(),
                b.getArrivalContactOutcome(),
                b.getArrivalContactNote(),
                changeRequestId);
    }
}
