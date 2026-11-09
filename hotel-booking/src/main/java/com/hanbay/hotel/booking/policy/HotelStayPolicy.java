package com.hanbay.hotel.booking.policy;

import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.exception.BookingValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HotelStayPolicy {
    private final Clock clock;

    @Getter
    private final HotelStayProperties properties;

    public ZonedDateTime now() {
        return ZonedDateTime.ofInstant(clock.instant(), properties.zoneId());
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    public void validateChildAges(int children, java.util.List<Integer> ages) {
        // Legacy callers may only know the declared counts. Never fabricate ages.
        if (ages == null) return;
        if (ages.size() != children
                || ages.stream().anyMatch(age -> age == null || age < 0 || age >= properties.getAdultFromAge())) {
            throw new BookingValidationException("Nhập tuổi tại ngày nhận phòng cho từng trẻ em; từ "
                    + properties.getAdultFromAge() + " tuổi phải tính vào số người lớn.");
        }
    }

    public boolean bookingClosed(LocalDate checkIn) {
        var now = now();
        return checkIn.equals(now.toLocalDate()) && !now.toLocalTime().isBefore(properties.getSameDayBookingCutoff());
    }

    public boolean canArrive(LocalDate checkIn, LocalDate checkOut) {
        var now = now();
        var today = now.toLocalDate();
        return checkIn.equals(today)
                || (checkIn.equals(today.minusDays(1))
                        && !checkOut.isBefore(today)
                        && now.toLocalTime().isBefore(properties.getLateArrivalCutoff()));
    }

    public boolean isEarlyArrival(LocalDate checkIn) {
        var now = now();
        return checkIn.equals(now.toLocalDate()) && now.toLocalTime().isBefore(properties.getCheckInTime());
    }

    public boolean isOverdue(LocalDate checkOut) {
        return now().isAfter(checkOut.atTime(properties.getCheckOutTime()).atZone(properties.zoneId()));
    }

    public LocalDate overdueThrough() {
        var now = now();
        return now.toLocalTime().isAfter(properties.getCheckOutTime())
                ? now.toLocalDate()
                : now.toLocalDate().minusDays(1);
    }
}
