package com.hanbay.hotel.booking.service.impl;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.request.CheckInGuestRequest;
import com.hanbay.hotel.booking.dto.response.AssignedRoomResponse;
import com.hanbay.hotel.booking.dto.response.AvailablePhysicalRoomResponse;
import com.hanbay.hotel.booking.dto.response.CheckInGuestResponse;
import com.hanbay.hotel.booking.exception.CheckInBookingNotFoundException;
import com.hanbay.hotel.booking.exception.CheckInConflictException;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.CheckInService;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomStatus;
import com.hanbay.hotel.room.exception.RoomNotFoundException;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.exception.UserNotFoundException;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Override
    @Transactional
    public CheckInGuestResponse checkIn(String bookingCode, CheckInGuestRequest request) {
        Booking booking = bookingRepository
                .findByBookingCodeWithRoomTypeForUpdate(bookingCode)
                .orElseThrow(() -> new CheckInBookingNotFoundException("Không tìm thấy booking"));

        validateBooking(booking);

        Room room = roomRepository
                .findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng"));

        validateRoom(booking, room);

        User receptionist = currentUserProvider
                .currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản lễ tân"));
        Instant checkedInAt = clock.instant();

        room.occupy();
        booking.checkIn(room, receptionist);

        return new CheckInGuestResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                new AssignedRoomResponse(room.getId(), room.getRoomNumber(), room.getFloor(), room.getStatus()),
                checkedInAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailablePhysicalRoomResponse> getAvailableRooms(String bookingCode) {
        Booking booking = bookingRepository
                .findByBookingCode(bookingCode)
                .orElseThrow(() -> new CheckInBookingNotFoundException("Không tìm thấy booking"));
        validateBooking(booking);

        return roomRepository
                .findAllByRoomTypeIdAndStatusOrderByFloorAscRoomNumberAsc(
                        booking.getRoomType().getId(), RoomStatus.AVAILABLE)
                .stream()
                .map(room -> new AvailablePhysicalRoomResponse(room.getId(), room.getRoomNumber(), room.getFloor()))
                .toList();
    }

    private void validateBooking(Booking booking) {
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            throw CheckInConflictException.bookingAlreadyCheckedIn();
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw CheckInConflictException.bookingNotConfirmed();
        }
        if (!booking.getCheckInDate().equals(LocalDate.now(clock))) {
            throw CheckInConflictException.checkInDateNotAllowed();
        }
    }

    private void validateRoom(Booking booking, Room room) {
        if (!booking.getRoomType().getId().equals(room.getRoomType().getId())) {
            throw CheckInConflictException.roomTypeMismatch();
        }
        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw CheckInConflictException.roomNotAvailable();
        }
    }
}
