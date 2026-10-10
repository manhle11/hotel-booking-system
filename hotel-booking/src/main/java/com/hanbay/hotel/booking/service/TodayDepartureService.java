package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingGuest;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.response.TodayDepartureResponse;
import com.hanbay.hotel.booking.mapper.TodayDepartureMapper;
import com.hanbay.hotel.booking.repository.BookingGuestRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.validation.PaginationValidator;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TodayDepartureService {
    private static final int MAX_PAGE_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final BookingGuestRepository bookingGuestRepository;
    private final TodayDepartureMapper todayDepartureMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<TodayDepartureResponse> getTodayDepartures(int page, int size) {
        PaginationValidator.validate(page, size, MAX_PAGE_SIZE);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "bookingCode"));
        Page<Booking> bookings = bookingRepository.findAllByStatus(BookingStatus.CHECKED_IN, pageable);
        Map<UUID, BookingGuest> primaryGuests = findPrimaryGuests(bookings.getContent());

        return PageResponse.from(
                bookings.map(booking -> todayDepartureMapper.toResponse(booking, primaryGuests.get(booking.getId()))));
    }

    private Map<UUID, BookingGuest> findPrimaryGuests(List<Booking> bookings) {
        List<UUID> bookingIds = bookings.stream().map(Booking::getId).toList();
        if (bookingIds.isEmpty()) {
            return Map.of();
        }

        return bookingGuestRepository.findPrimaryGuestsByBookingIds(bookingIds).stream()
                .collect(Collectors.toMap(
                        guest -> guest.getBooking().getId(),
                        Function.identity(),
                        (firstGuest, ignoredGuest) -> firstGuest));
    }
}
