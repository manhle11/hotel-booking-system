package com.hanbay.hotel.booking.policy;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.exception.BookingValidationException;
import java.time.*;

/** The direct standard rate contract. Existing bookings never acquire it implicitly. */
public final class UnifiedBookingPolicy {
    public static final String VERSION = "HANBAY-STANDARD-2026-11-V2";
    public static final String TERMS = "Hủy/đổi: trước ít nhất 48 giờ 0đ; từ 24 đến dưới 48 giờ 50% giá đêm đầu gốc P; "
            + "dưới 24 giờ và no-show 100% P. Tổng phí toàn chuỗi tối đa P mỗi phòng, không hoàn phí đã phát sinh khi đổi ngày. "
            + "Đổi cùng hạng, cùng số đêm tối đa một lần, ngày nhận mới trong 90 ngày từ ngày nhận gốc, tùy tồn phòng, giá và khách chấp thuận. "
            + "Đến muộn phụ thu 0đ, trước 06:00 hôm sau; sau hạn cần quản lý duyệt và không quá giờ trả gốc. "
            + "Hủy/đổi toàn đơn nhiều phòng; hoàn tiền chờ đối soát trong 3–7 ngày làm việc khi đủ thông tin.";

    private UnifiedBookingPolicy() {}

    public static boolean applies(Booking booking) {
        return VERSION.equals(booking.getTermsVersion()) && booking.getOriginalFirstNightAmount() != null;
    }

    public static long firstNight(Booking booking) {
        return booking.getNights().stream()
                .filter(n -> n.getStayDate().equals(booking.getCheckInDate()))
                .mapToLong(n -> n.getPricePerNight())
                .findFirst()
                .orElseThrow(() -> new BookingValidationException("Thiếu giá đêm đầu đã chốt; cần đối soát booking."));
    }

    public static int rate(Instant checkIn, Instant receivedAt) {
        if (!receivedAt.isAfter(checkIn.minus(Duration.ofHours(48)))) return 0;
        if (!receivedAt.isAfter(checkIn.minus(Duration.ofHours(24)))) return 50;
        return 100;
    }

    public static long target(Booking booking, Instant receivedAt, ZoneId zone, LocalTime checkInTime) {
        long p = booking.getOriginalFirstNightAmount();
        int rate =
                rate(booking.getCheckInDate().atTime(checkInTime).atZone(zone).toInstant(), receivedAt);
        long target = rate == 50 ? p / 2 : rate == 100 ? p : 0;
        return Math.min(p, Math.max(booking.getPolicyFeeTotal(), target));
    }
}
