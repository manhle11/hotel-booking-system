package com.hanbay.hotel.frontdesk.service.impl;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingGuest;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.repository.BookingGuestRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.frontdesk.dto.response.ArrivalRoomTypeResponse;
import com.hanbay.hotel.frontdesk.dto.response.PrimaryGuestSummaryResponse;
import com.hanbay.hotel.frontdesk.dto.response.TodayArrivalResponse;
import com.hanbay.hotel.frontdesk.service.FrontDeskService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrontDeskServiceImpl implements FrontDeskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Sort ARRIVAL_SORT = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("bookingCode"));

    private final BookingRepository bookingRepository;
    private final BookingGuestRepository bookingGuestRepository;
    private final Clock clock;
    private final HotelStayPolicy stayPolicy;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TodayArrivalResponse> getOverdueArrivals(int page, int size) {
        PaginationValidator.validate(page, size, MAX_PAGE_SIZE);
        var now = stayPolicy.now();
        LocalDate today = now.toLocalDate();
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("checkInDate"), Sort.Order.asc("createdAt"), Sort.Order.asc("bookingCode")));
        Page<Booking> bookings = bookingRepository.findOverdueArrivals(
                BookingStatus.CONFIRMED,
                today,
                today.minusDays(1),
                now.toLocalTime().isBefore(stayPolicy.getProperties().getLateArrivalCutoff()),
                pageable);
        var primaryGuests = findPrimaryGuests(bookings.getContent());
        return PageResponse.from(bookings.map(booking -> toResponse(booking, primaryGuests.get(booking.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TodayArrivalResponse> getTodayArrivals(int page, int size) {
        PaginationValidator.validate(page, size, MAX_PAGE_SIZE);

        LocalDate today = stayPolicy.today();
        PageRequest pageable = PageRequest.of(page, size, ARRIVAL_SORT);
        Page<Booking> bookings = bookingRepository.findArrivalsIncludingLate(
                BookingStatus.CONFIRMED,
                today,
                today.minusDays(1),
                stayPolicy
                        .now()
                        .toLocalTime()
                        .isBefore(stayPolicy.getProperties().getLateArrivalCutoff()),
                pageable);
        Map<UUID, BookingGuest> primaryGuests = findPrimaryGuests(bookings.getContent());

        return PageResponse.from(bookings.map(booking -> toResponse(booking, primaryGuests.get(booking.getId()))));
    }

    private Map<UUID, BookingGuest> findPrimaryGuests(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }

        List<UUID> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<UUID, BookingGuest> primaryGuests =
                bookingGuestRepository.findPrimaryGuestsByBookingIds(bookingIds).stream()
                        .collect(Collectors.toMap(
                                guest -> guest.getBooking().getId(),
                                Function.identity(),
                                (first, duplicate) -> {
                                    log.warn(
                                            "Booking {} has more than one primary guest; using the first result",
                                            first.getBooking().getId());
                                    return first;
                                },
                                LinkedHashMap::new));

        bookings.stream()
                .filter(booking -> !primaryGuests.containsKey(booking.getId()))
                .forEach(booking ->
                        log.warn("Booking {} ({}) has no primary guest", booking.getId(), booking.getBookingCode()));
        return primaryGuests;
    }

    private TodayArrivalResponse toResponse(Booking booking, BookingGuest primaryGuest) {
        PrimaryGuestSummaryResponse guestResponse = primaryGuest == null
                ? null
                : new PrimaryGuestSummaryResponse(
                        primaryGuest.getFullName(), primaryGuest.getPhone(), primaryGuest.getEmail());

        return new TodayArrivalResponse(
                booking.getId(),
                booking.getBookingCode(),
                guestResponse,
                new ArrivalRoomTypeResponse(
                        booking.getRoomType().getId(),
                        booking.getRoomType().getCode(),
                        booking.getRoomType().getName()),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getAdultCount(),
                booking.getChildCount(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getArrivalHoldUntil(),
                booking.getArrivalHoldRequestedUntil());
    }
}
