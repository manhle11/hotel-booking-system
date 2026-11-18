package com.hanbay.hotel.frontdesk.service;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.response.ArrivalProgress;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.repository.*;
import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.frontdesk.dto.response.*;
import com.hanbay.hotel.frontdesk.repository.ArrivalWorkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArrivalWorkService {
    private final ArrivalWorkRepository work;
    private final BookingRepository bookings;
    private final BookingGuestRepository guests;
    private final BookingChangeRequestRepository changes;
    private final HotelStayPolicy policy;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<ArrivalWorkItem> list(
            ArrivalProgress.Group group, ArrivalProgress.Reason reason, int page, int size) {
        return list(group, reason, null, null, page, size);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<ArrivalWorkItem> list(
            ArrivalProgress.Group group,
            ArrivalProgress.Reason reason,
            LocalDate resolvedFrom,
            LocalDate resolvedTo,
            int page,
            int size) {
        PaginationValidator.validate(page, size, 100);
        if ((resolvedFrom == null) != (resolvedTo == null))
            throw new RequestValidationException("resolvedFrom", "Chọn đủ ngày bắt đầu và ngày kết thúc.");
        if (resolvedFrom != null) {
            if (group != ArrivalProgress.Group.RESOLVED)
                throw new RequestValidationException("group", "Khoảng ngày xử lý chỉ áp dụng cho nhóm đã xử lý.");
            if (resolvedFrom.isAfter(resolvedTo))
                throw new RequestValidationException("resolvedTo", "Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
        var now = clock.instant();
        var today = now.atZone(policy.getProperties().zoneId()).toLocalDate();
        var result = work.find(
                group,
                reason,
                page,
                size,
                now,
                resolvedFrom == null ? today : resolvedFrom,
                resolvedTo == null ? today : resolvedTo);
        var byId = bookings.findAllById(result.getContent()).stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));
        var primary = result.isEmpty()
                ? Map.<UUID, BookingGuest>of()
                : guests.findPrimaryGuestsByBookingIds(result.getContent()).stream()
                        .collect(Collectors.toMap(
                                g -> g.getBooking().getId(), Function.identity(), (first, duplicate) -> first));
        var keys = byId.values().stream()
                .map(BookingChangeRequest::scopeOf)
                .distinct()
                .toList();
        var pending = keys.isEmpty()
                ? Map.<String, UUID>of()
                : changes.findAllByScopeKeyInAndStatus(keys, BookingChangeRequest.Status.PENDING).stream()
                        .collect(Collectors.toMap(BookingChangeRequest::getScopeKey, BookingChangeRequest::getId));
        return PageResponse.from(result.map(id -> {
            Booking b = byId.get(id);
            var g = primary.get(id);
            var t = b.getRoomType();
            return new ArrivalWorkItem(
                    b.getId(),
                    b.getBookingCode(),
                    g == null ? null : new PrimaryGuestSummaryResponse(g.getFullName(), g.getPhone(), g.getEmail()),
                    new ArrivalRoomTypeResponse(t.getId(), t.getCode(), t.getName()),
                    b.getCheckInDate(),
                    b.getCheckOutDate(),
                    b.getAdultCount(),
                    b.getChildCount(),
                    b.getStatus(),
                    b.getCreatedAt(),
                    ArrivalProgress.of(b, pending.get(BookingChangeRequest.scopeOf(b)), policy, now),
                    policy.getProperties().getZone(),
                    now,
                    b.getOrder() == null ? null : b.getOrder().getOrderCode(),
                    b.getOrder() == null ? 1 : b.getOrder().getBookings().size(),
                    b.getOrderRoomNumber(),
                    resolvedAt(b));
        }));
    }

    private Instant resolvedAt(Booking booking) {
        return switch (booking.getStatus()) {
            case CHECKED_IN, CHECKED_OUT -> booking.getCheckedInAt();
            case NO_SHOW -> booking.getNoShowAt();
            case CANCELLED -> booking.getCancelledAt();
            default -> null;
        };
    }
}
