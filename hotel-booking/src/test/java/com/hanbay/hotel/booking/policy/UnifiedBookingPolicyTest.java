package com.hanbay.hotel.booking.policy;

import static org.assertj.core.api.Assertions.*;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.user.domain.User;
import java.time.*;
import org.junit.jupiter.api.Test;

class UnifiedBookingPolicyTest {
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
    private final LocalDate date = LocalDate.of(2026, 11, 15);
    private final LocalTime time = LocalTime.of(14, 0);

    private Instant arrival() {
        return date.atTime(time).atZone(zone).toInstant();
    }

    private Booking booking(long p, long f) {
        return Booking.builder()
                .bookingCode("BK-TEST")
                .status(BookingStatus.CONFIRMED)
                .checkInDate(date)
                .checkOutDate(date.plusDays(3))
                .totalAmount(3_000_000 + f)
                .termsVersion(UnifiedBookingPolicy.VERSION)
                .originalFirstNightAmount(p)
                .originalCheckInDate(date)
                .policyFeeTotal(f)
                .build();
    }

    @Test
    void exactBoundariesUseInstantsWithoutTruncatingMinutes() {
        assertThat(UnifiedBookingPolicy.rate(arrival(), arrival().minus(Duration.ofHours(48))))
                .isZero();
        assertThat(UnifiedBookingPolicy.rate(
                        arrival(), arrival().minus(Duration.ofHours(48)).plusNanos(1)))
                .isEqualTo(50);
        assertThat(UnifiedBookingPolicy.rate(arrival(), arrival().minus(Duration.ofHours(24))))
                .isEqualTo(50);
        assertThat(UnifiedBookingPolicy.rate(
                        arrival(), arrival().minus(Duration.ofHours(24)).plusNanos(1)))
                .isEqualTo(100);
        assertThat(UnifiedBookingPolicy.rate(arrival(), arrival().plus(Duration.ofDays(2))))
                .isEqualTo(100);
    }

    @Test
    void feeUsesOriginalDiscountedFirstNightAndRoundsDownIncludingZeroPrice() {
        assertThat(UnifiedBookingPolicy.target(
                        booking(1_000_001, 0), arrival().minus(Duration.ofHours(30)), zone, time))
                .isEqualTo(500_000);
        assertThat(UnifiedBookingPolicy.target(booking(0, 0), arrival(), zone, time))
                .isZero();
    }

    @Test
    void acceptedFirstNightUsesItsSavedDateAndPriceInsteadOfStayTotalOrNightlyAverage() {
        Booking b = Booking.builder()
                .status(BookingStatus.PENDING)
                .checkInDate(date)
                .checkOutDate(date.plusDays(3))
                .totalAmount(11_100_000)
                .build();
        b.addNight(BookingNight.builder()
                .stayDate(date.plusDays(2))
                .pricePerNight(4_150_000)
                .build());
        b.addNight(
                BookingNight.builder().stayDate(date).pricePerNight(2_450_000).build());
        b.addNight(BookingNight.builder()
                .stayDate(date.plusDays(1))
                .pricePerNight(3_600_000)
                .build());
        b.acceptStandardTerms(arrival().minus(Duration.ofDays(5)));
        assertThat(b.getOriginalFirstNightAmount()).isEqualTo(2_450_000);
        assertThat(UnifiedBookingPolicy.target(b, arrival().minus(Duration.ofHours(30)), zone, time))
                .isEqualTo(1_225_000);
    }

    @Test
    void changingDatesNeverResetsAccruedFeeOrChargesTheSameFeeTwice() {
        Booking b = booking(1_000_000, 500_000);
        assertThat(UnifiedBookingPolicy.target(b, arrival().minus(Duration.ofDays(5)), zone, time))
                .isEqualTo(500_000);
        long total = UnifiedBookingPolicy.target(b, arrival().minus(Duration.ofHours(10)), zone, time);
        b.recordPolicyFee(total, "CANCELLATION", null, arrival(), "Khách xác nhận hủy");
        assertThat(b.getPolicyFeeTotal()).isEqualTo(1_000_000);
        assertThat(b.getPolicyFees().get(0).getAmount()).isEqualTo(500_000);
        assertThat(UnifiedBookingPolicy.target(b, arrival(), zone, time)).isEqualTo(1_000_000);
        assertThatThrownBy(() -> b.recordPolicyFee(1_000_001, "NO_SHOW", null, arrival(), "Không hợp lệ"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void settlementReviewUsesTheAcceptedCapInsteadOfTheCheaperStayTotal() {
        User actor = User.builder().fullName("Quản lý").build();
        Booking unified = Booking.builder()
                .status(BookingStatus.CONFIRMED)
                .totalAmount(300_000)
                .termsVersion(UnifiedBookingPolicy.VERSION)
                .originalFirstNightAmount(800_000L)
                .build();
        unified.cancelForSettlementReview(actor, arrival(), "Cần đối soát tiền đã nhận");
        unified.settleCancelledBooking(actor, 800_000, 300_000, "Đã đối soát theo P gốc");
        assertThat(unified.isCancellationReviewRequired()).isFalse();
        assertThat(unified.getCancellationOriginalTotal()).isEqualTo(300_000);
        assertThat(unified.getCancellationFee()).isEqualTo(800_000);
        assertThat(unified.getTotalAmount()).isEqualTo(800_000);

        Booking legacy = Booking.builder()
                .status(BookingStatus.CONFIRMED)
                .totalAmount(300_000)
                .build();
        legacy.cancelForSettlementReview(actor, arrival(), "Đối soát điều khoản cũ");
        assertThatThrownBy(() -> legacy.settleCancelledBooking(actor, 800_000, 300_000, "Không có căn cứ tăng phí"))
                .isInstanceOf(com.hanbay.hotel.booking.exception.BookingValidationException.class);
    }

    @Test
    void acceptanceSnapshotsContentTimeAndPriceOnlyForPendingBooking() {
        Booking b = Booking.builder()
                .checkInDate(date)
                .checkOutDate(date.plusDays(3))
                .build();
        b.addNight(BookingNight.builder().stayDate(date).pricePerNight(750_000).build());
        b.acceptStandardTerms(arrival().minus(Duration.ofDays(5)));
        b.getNights().get(0).setPricePerNight(900_000);
        b.acceptStandardTerms(arrival());
        assertThat(b.getOriginalFirstNightAmount()).isEqualTo(750_000);
        assertThat(b.getAcceptedTerms()).isEqualTo(UnifiedBookingPolicy.TERMS);
        assertThat(b.getTermsAcceptedAt()).isEqualTo(arrival().minus(Duration.ofDays(5)));
        Booking historical = Booking.builder().status(BookingStatus.CONFIRMED).build();
        historical.acceptStandardTerms(arrival());
        assertThat(UnifiedBookingPolicy.applies(historical)).isFalse();
    }
}
