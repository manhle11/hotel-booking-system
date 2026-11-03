package com.hanbay.hotel.booking.service.impl;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.request.CheckOutGuestRequest;
import com.hanbay.hotel.booking.dto.response.AssignedRoomResponse;
import com.hanbay.hotel.booking.dto.response.CheckOutGuestResponse;
import com.hanbay.hotel.booking.exception.CheckOutBookingNotFoundException;
import com.hanbay.hotel.booking.exception.CheckOutConflictException;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.CheckOutService;
import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.payment.service.CounterPaymentService;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckOutServiceImpl implements CheckOutService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final CounterPaymentService counterPaymentService;
    private final HotelStayPolicy stayPolicy;

    @Override
    @Transactional
    public CheckOutGuestResponse checkOut(UUID bookingId, CheckOutGuestRequest request) {
        Booking booking = bookingRepository
                .findByIdWithAssignedRoomForUpdate(bookingId)
                .orElseThrow(() -> new CheckOutBookingNotFoundException("Không tìm thấy đặt phòng"));

        validateBookingStatus(booking);
        if (LocalDate.now(clock).isBefore(booking.getCheckOutDate())) {
            throw CheckOutConflictException.earlyCheckoutRequired();
        }
        Room assignedRoom = booking.getAssignedRoom();
        if (assignedRoom == null) {
            throw CheckOutConflictException.roomNotAssigned();
        }

        Room room = roomRepository
                .findByIdForUpdate(assignedRoom.getId())
                .orElseThrow(() -> new RoomNotFoundException("Không tìm thấy phòng"));
        validateRoom(room);

        long additionalChargeAmount = request == null ? 0L : request.normalizedAdditionalChargeAmount();
        long collectedAmount = request == null ? 0L : request.normalizedCollectedAmount();
        String additionalChargeReason = request == null ? null : request.normalizedAdditionalChargeReason();
        String note = request == null ? null : request.normalizedNote();
        if (stayPolicy.isOverdue(booking.getCheckOutDate())) {
            if (note == null) {
                throw new RequestValidationException(
                        "note", "Trả phòng sau giờ chuẩn cần ghi chú xác nhận của lễ tân.");
            }
            note = "Lễ tân xử lý trả phòng muộn/quá hạn: " + note;
        }

        long finalTotalAmount;
        long finalPaidAmount;
        try {
            finalTotalAmount = Math.addExact(booking.getTotalAmount(), additionalChargeAmount);
            finalPaidAmount = Math.addExact(booking.getPaidAmount(), collectedAmount);
        } catch (ArithmeticException exception) {
            throw new RequestValidationException("additionalChargeAmount", "Tổng tiền vượt giới hạn cho phép");
        }
        if (collectedAmount > outstandingAmount(finalTotalAmount, booking.getPaidAmount())) {
            throw CheckOutConflictException.overCollection();
        }
        long outstandingAmount = outstandingAmount(finalTotalAmount, finalPaidAmount);
        if (outstandingAmount > 0) {
            throw CheckOutConflictException.paymentRequired(outstandingAmount);
        }

        User receptionist = currentUserProvider
                .currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản lễ tân"));
        Instant checkedOutAt = clock.instant();

        if (additionalChargeAmount > 0) {
            booking.applyTotalAmount(finalTotalAmount);
        }
        counterPaymentService.settleAtCheckout(booking, collectedAmount, receptionist.getId());
        room.markDirtyAfterCheckout();
        booking.checkOut(
                receptionist,
                buildCheckOutNote(note, additionalChargeAmount, additionalChargeReason, collectedAmount),
                checkedOutAt);

        return new CheckOutGuestResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                new AssignedRoomResponse(room.getId(), room.getRoomNumber(), room.getFloor(), room.getStatus()),
                checkedOutAt,
                booking.getTotalAmount(),
                booking.getPaidAmount(),
                outstandingAmount(booking),
                additionalChargeAmount,
                additionalChargeReason,
                collectedAmount);
    }

    private void validateBookingStatus(Booking booking) {
        if (booking.getStatus() == BookingStatus.CHECKED_OUT) {
            throw CheckOutConflictException.bookingAlreadyCheckedOut();
        }
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw CheckOutConflictException.bookingNotCheckedIn();
        }
    }

    private void validateRoom(Room room) {
        if (room.getStatus() != RoomStatus.OCCUPIED) {
            throw CheckOutConflictException.roomNotOccupied();
        }
    }

    private long outstandingAmount(Booking booking) {
        return outstandingAmount(booking.getTotalAmount(), booking.getPaidAmount());
    }

    private long outstandingAmount(long totalAmount, long paidAmount) {
        return Math.max(totalAmount - paidAmount, 0L);
    }

    private String buildCheckOutNote(
            String note, long additionalChargeAmount, String additionalChargeReason, long collectedAmount) {
        StringBuilder builder = new StringBuilder();
        if (note != null) {
            builder.append(note);
        }
        if (additionalChargeAmount > 0) {
            appendSeparator(builder);
            builder.append("Phí phát sinh: ")
                    .append(additionalChargeAmount)
                    .append(" VND")
                    .append(" - ")
                    .append(additionalChargeReason);
        }
        if (collectedAmount > 0) {
            appendSeparator(builder);
            builder.append("Đã thu thêm tại quầy: ").append(collectedAmount).append(" VND");
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private void appendSeparator(StringBuilder builder) {
        if (!builder.isEmpty()) {
            builder.append("; ");
        }
    }
}
