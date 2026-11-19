package com.hanbay.hotel.booking.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanbay.hotel.booking.config.BookingCancellationProperties;
import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.response.RefundStatus;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class StandardBookingCancellationPolicyTest {

    private static final ZoneId HOTEL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 8, 20);
    private static final LocalTime CHECK_IN_TIME = LocalTime.of(14, 0);
    private final StandardBookingCancellationPolicy policy = new StandardBookingCancellationPolicy(
            new BookingCancellationProperties(CHECK_IN_TIME, HOTEL_ZONE.getId(), 48, 24, 50));

    @Test
    void returnsFullRefundAtExactlyFortyEightHours() {
        CancellationDecision decision = evaluateConfirmed(48, PaymentStatus.SUCCESS);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.refundPercent()).isEqualTo(100);
        assertThat(decision.refundAmount()).isEqualTo(2_000_000L);
        assertThat(decision.refundStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void returnsHalfRefundOneMinuteBeforeFortyEightHours() {
        Instant now = checkInInstant().minus(48, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES);
        CancellationDecision decision = policy.evaluate(confirmedBooking(), PaymentStatus.SUCCESS, now);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.refundPercent()).isEqualTo(50);
        assertThat(decision.refundAmount()).isEqualTo(1_000_000L);
    }

    @Test
    void returnsHalfRefundAtExactlyTwentyFourHours() {
        CancellationDecision decision = evaluateConfirmed(24, PaymentStatus.SUCCESS);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.refundPercent()).isEqualTo(50);
    }

    @Test
    void rejectsCancellationOneMinuteInsideTwentyFourHourWindow() {
        Instant now = checkInInstant().minus(24, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES);
        CancellationDecision decision = policy.evaluate(confirmedBooking(), PaymentStatus.SUCCESS, now);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialCode()).isEqualTo("BOOKING_CANCELLATION_WINDOW_EXPIRED");
    }

    @Test
    void pendingBookingCanBeCancelledWithoutRefund() {
        Booking pending = Booking.builder()
                .status(BookingStatus.PENDING)
                .checkInDate(CHECK_IN_DATE)
                .totalAmount(2_000_000L)
                .build();

        CancellationDecision decision = policy.evaluate(pending, PaymentStatus.PENDING, Instant.now());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.refundAmount()).isZero();
        assertThat(decision.refundStatus()).isEqualTo(RefundStatus.NOT_REQUIRED);
    }

    @Test
    void confirmedBookingWithoutSuccessfulPaymentDoesNotCreateRefund() {
        CancellationDecision decision = evaluateConfirmed(48, PaymentStatus.PENDING);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.refundAmount()).isZero();
        assertThat(decision.refundStatus()).isEqualTo(RefundStatus.NOT_REQUIRED);
    }

    @Test
    void unifiedCancellationFeeCanExceedTheCheaperRescheduledStay() {
        Booking booking = Booking.builder()
                .status(BookingStatus.CONFIRMED)
                .checkInDate(CHECK_IN_DATE)
                .totalAmount(300_000)
                .termsVersion(UnifiedBookingPolicy.VERSION)
                .originalFirstNightAmount(800_000L)
                .build();

        CancellationDecision decision = policy.evaluate(booking, PaymentStatus.SUCCESS, checkInInstant());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.refundAmount()).isZero();
        assertThat(decision.feeAmount()).isEqualTo(800_000);
    }

    @Test
    void unifiedCancellationUsesHalfOfFirstNightWhileRefundingOtherNightsAndServices() {
        Booking booking = Booking.builder()
                .status(BookingStatus.CONFIRMED)
                .checkInDate(CHECK_IN_DATE)
                .checkOutDate(CHECK_IN_DATE.plusDays(3))
                .totalAmount(11_100_000)
                .termsVersion(UnifiedBookingPolicy.VERSION)
                .originalFirstNightAmount(2_450_000L)
                .build();
        CancellationDecision decision =
                policy.evaluate(booking, PaymentStatus.SUCCESS, checkInInstant().minus(30, ChronoUnit.HOURS));
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.feeAmount()).isEqualTo(1_225_000);
        assertThat(decision.refundAmount()).isEqualTo(9_875_000);
    }

    private CancellationDecision evaluateConfirmed(long hoursBeforeCheckIn, PaymentStatus paymentStatus) {
        return policy.evaluate(
                confirmedBooking(), paymentStatus, checkInInstant().minus(hoursBeforeCheckIn, ChronoUnit.HOURS));
    }

    private Booking confirmedBooking() {
        return Booking.builder()
                .status(BookingStatus.CONFIRMED)
                .checkInDate(CHECK_IN_DATE)
                .totalAmount(2_000_000L)
                .build();
    }

    private Instant checkInInstant() {
        return CHECK_IN_DATE.atTime(CHECK_IN_TIME).atZone(HOTEL_ZONE).toInstant();
    }
}
