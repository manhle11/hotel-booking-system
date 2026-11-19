package com.hanbay.hotel.room.service;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.room.domain.RoomStatus;
import com.hanbay.hotel.room.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomAvailabilityCalculator {

    private static final List<RoomStatus> SELLABLE_ROOM_STATUSES =
            List.of(RoomStatus.AVAILABLE, RoomStatus.OCCUPIED, RoomStatus.DIRTY, RoomStatus.CLEANING);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final HotelStayPolicy stayPolicy;

    public Map<UUID, Long> countReadyRooms(Collection<UUID> roomTypeIds) {
        if (roomTypeIds.isEmpty()) {
            return Map.of();
        }

        return roomRepository.countByRoomTypeIdsAndStatusIn(roomTypeIds, List.of(RoomStatus.AVAILABLE)).stream()
                .collect(Collectors.toMap(
                        RoomRepository.RoomTypeRoomCount::getRoomTypeId,
                        RoomRepository.RoomTypeRoomCount::getRoomCount));
    }

    public long countAvailableRooms(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return countAvailableRooms(List.of(roomTypeId), checkIn, checkOut).getOrDefault(roomTypeId, 0L);
    }

    public long countAvailableRoomsExcludingBooking(
            UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, UUID excludedBookingId) {
        return countAvailableRooms(List.of(roomTypeId), checkIn, checkOut, excludedBookingId)
                .getOrDefault(roomTypeId, 0L);
    }

    public Map<UUID, Long> countAvailableRooms(Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut) {
        return countAvailableRooms(roomTypeIds, checkIn, checkOut, null);
    }

    private Map<UUID, Long> countAvailableRooms(
            Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut, UUID excludedBookingId) {
        return countAvailableRooms(roomTypeIds, checkIn, checkOut, excludedBookingId, null);
    }

    public Map<UUID, Long> countAvailableRoomsExcludingOrder(
            Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut, UUID orderId) {
        return countAvailableRooms(roomTypeIds, checkIn, checkOut, null, orderId);
    }

    private Map<UUID, Long> countAvailableRooms(
            Collection<UUID> roomTypeIds,
            LocalDate checkIn,
            LocalDate checkOut,
            UUID excludedBookingId,
            UUID excludedOrderId) {
        if (roomTypeIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> sellableRoomCounts =
                roomRepository.countByRoomTypeIdsAndStatusIn(roomTypeIds, SELLABLE_ROOM_STATUSES).stream()
                        .collect(Collectors.toMap(
                                RoomRepository.RoomTypeRoomCount::getRoomTypeId,
                                RoomRepository.RoomTypeRoomCount::getRoomCount));

        var counts = excludedOrderId == null
                ? bookingRepository.countPeakActiveBookingsByRoomTypeIds(
                        roomTypeIds,
                        checkIn,
                        checkOut,
                        stayPolicy.overdueThrough(),
                        Instant.now(clock),
                        excludedBookingId)
                : bookingRepository.countPeakExcludingOrder(
                        roomTypeIds,
                        checkIn,
                        checkOut,
                        stayPolicy.overdueThrough(),
                        Instant.now(clock),
                        excludedOrderId);
        Map<UUID, Long> overlappingBookingCounts = counts.stream()
                .collect(Collectors.toMap(
                        BookingRepository.RoomTypeBookingCount::getRoomTypeId,
                        BookingRepository.RoomTypeBookingCount::getBookingCount));

        return roomTypeIds.stream()
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        roomTypeId -> Math.max(
                                0L,
                                sellableRoomCounts.getOrDefault(roomTypeId, 0L)
                                        - overlappingBookingCounts.getOrDefault(roomTypeId, 0L))));
    }
}
