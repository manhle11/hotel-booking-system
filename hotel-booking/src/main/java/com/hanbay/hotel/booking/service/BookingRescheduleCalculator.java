package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingNight;
import com.hanbay.hotel.booking.domain.pricing.BookingNightPriceRule;
import com.hanbay.hotel.booking.exception.BookingValidationException;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.policy.UnifiedBookingPolicy;
import com.hanbay.hotel.pricing.service.PricingService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingRescheduleCalculator {
    public static final String TERMS_VERSION = "HANBAY-STAY-CHANGE-2026-11-V1";
    public static final String TERMS =
            "Dời ngày nhận: báo giá lại toàn kỳ mới; phí 0/50%/100% giá phòng đêm đầu gốc đã chốt của từng phòng theo mốc 48/24 giờ của kỳ hiện tại. "
                    + "Căn cứ đêm đầu đã trừ giảm giá, không gồm các đêm tiếp theo hay dịch vụ. Đơn nhiều phòng cộng phí từng phòng. "
                    + "Giữ ngày nhận và thêm đêm: giữ giá đêm cũ, đêm thêm theo giá hiện hành, không thêm phí đổi. "
                    + "Giữ ngày nhận và bớt đêm: giữ giá các đêm còn lại; hủy phần bị bỏ theo cùng mốc giờ trên giá đêm đầu bị bỏ, không vượt giá trị phần bỏ và trần đêm đầu gốc. "
                    + "Phí trước đây được giữ và chỉ bổ sung phần thiếu; không cộng hai khoản phạt cho cùng một lần đổi. "
                    + "Tăng/giảm số đêm của gói cùng số đêm cần quản lý xét ngoại lệ và khách chấp thuận báo giá trước khi áp dụng.";

    private final PricingService pricing;
    private final HotelStayPolicy stay;

    public record ProposedStay(List<BookingNight> nights, long roomTotal, long policyFee) {}

    public ProposedStay calculate(Booking booking, LocalDate arrival, LocalDate departure, Instant receivedAt) {
        boolean standard = UnifiedBookingPolicy.applies(booking);
        long fee = standard
                ? UnifiedBookingPolicy.target(
                        booking,
                        receivedAt,
                        stay.getProperties().zoneId(),
                        stay.getProperties().getCheckInTime())
                : 0;
        if (!arrival.equals(booking.getCheckInDate())) {
            var price = pricing.calculateRoomPrice(booking.getRoomType(), arrival, departure);
            return new ProposedStay(price.nights(), price.finalRoomAmount(), fee);
        }

        // Clone the saved snapshots before the old period is removed in the applying transaction.
        List<BookingNight> nights = new ArrayList<>();
        LocalDate retainedEnd = departure.isBefore(booking.getCheckOutDate()) ? departure : booking.getCheckOutDate();
        for (LocalDate date = arrival; date.isBefore(retainedEnd); date = date.plusDays(1)) {
            BookingNight original = savedNight(booking, date);
            BookingNight copy = BookingNight.builder()
                    .stayDate(date)
                    .pricePerNight(original.getPricePerNight())
                    .build();
            original.getAppliedRules()
                    .forEach(rule -> copy.addAppliedRule(BookingNightPriceRule.builder()
                            .priceRule(rule.getPriceRule())
                            .adjustmentAmount(rule.getAdjustmentAmount())
                            .build()));
            nights.add(copy);
        }
        if (departure.isAfter(booking.getCheckOutDate())) {
            var extra = pricing.calculateRoomPrice(booking.getRoomType(), booking.getCheckOutDate(), departure);
            nights.addAll(extra.nights());
            fee = standard ? booking.getPolicyFeeTotal() : 0;
        } else if (departure.isBefore(booking.getCheckOutDate()) && standard) {
            long removed = booking.getNights().stream()
                    .filter(night -> !night.getStayDate().isBefore(departure))
                    .mapToLong(BookingNight::getPricePerNight)
                    .reduce(0, Math::addExact);
            long firstRemoved = savedNight(booking, departure).getPricePerNight();
            int rate = UnifiedBookingPolicy.rate(
                    booking.getCheckInDate()
                            .atTime(stay.getProperties().getCheckInTime())
                            .atZone(stay.getProperties().zoneId())
                            .toInstant(),
                    receivedAt);
            long target = rate == 100 ? firstRemoved : rate == 50 ? firstRemoved / 2 : 0;
            fee = Math.min(
                    booking.getOriginalFirstNightAmount(),
                    Math.max(booking.getPolicyFeeTotal(), Math.min(removed, target)));
        }
        long total = nights.stream().mapToLong(BookingNight::getPricePerNight).reduce(0, Math::addExact);
        return new ProposedStay(nights, total, fee);
    }

    private BookingNight savedNight(Booking booking, LocalDate date) {
        return booking.getNights().stream()
                .filter(night -> night.getStayDate().equals(date))
                .findFirst()
                .orElseThrow(() -> new BookingValidationException(
                        "Thiếu giá đêm đã chốt ngày " + date + "; cần đối soát trước khi báo giá."));
    }
}
