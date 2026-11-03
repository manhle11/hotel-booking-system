package com.hanbay.hotel.booking.domain;

import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingFactory {

    private final Clock clock;

    @Value("${payment.expiration-minutes:15}")
    private int paymentExpireMinutes = 15;

    public Booking createPendingBooking(
            User bookedBy,
            String bookingCode,
            RoomType roomType,
            LocalDate checkIn,
            LocalDate checkOut,
            int adults,
            int children) {

        Objects.requireNonNull(roomType, "Loại phòng không được để trống.");
        Objects.requireNonNull(checkIn, "Ngày nhận phòng không được để trống.");
        Objects.requireNonNull(checkOut, "Ngày trả phòng không được để trống.");

        Instant now = Instant.now(clock);

        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .bookedBy(bookedBy)
                .roomType(roomType)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .adultCount(adults)
                .childCount(children)
                .status(BookingStatus.PENDING)
                .expiredAt(now.plus(paymentExpireMinutes, ChronoUnit.MINUTES))
                .build();

        booking.recordCreated(bookedBy);

        return booking;
    }
}
