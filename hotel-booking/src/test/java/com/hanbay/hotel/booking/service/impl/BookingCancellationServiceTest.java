package com.hanbay.hotel.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.request.CancelBookingRequest;
import com.hanbay.hotel.booking.dto.response.RefundStatus;
import com.hanbay.hotel.booking.policy.BookingCancellationPolicy;
import com.hanbay.hotel.booking.policy.CancellationDecision;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.BookingSettlementLedger;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.payment.domain.Payment;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import com.hanbay.hotel.payment.domain.RefundMethod;
import com.hanbay.hotel.payment.dto.request.RefundInformationRequest;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingCancellationServiceTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BOOKING_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String BOOKING_CODE = "BK20260812000001";
    private static final Instant NOW = Instant.parse("2026-08-12T07:00:00Z");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private BookingCancellationPolicy cancellationPolicy;

    @Mock
    private Clock clock;

    @Mock
    private com.hanbay.hotel.booking.service.BookingChangeLifecycle changeLifecycle;

    @Spy
    private BookingSettlementLedger settlementLedger = new BookingSettlementLedger(null);

    @InjectMocks
    private BookingServiceImpl service;

    @Test
    void cancelsPendingBookingAndPendingPaymentWithoutRefund() {
        Booking booking = booking(BookingStatus.PENDING);
        Payment payment = payment(booking, PaymentStatus.PENDING);
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(USER_ID));
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(clock.instant()).thenReturn(NOW);
        org.mockito.Mockito.doReturn(List.of(payment)).when(settlementLedger).lockPayments(List.of(booking));

        var response = service.cancelBooking(BOOKING_CODE, new CancelBookingRequest("Đổi kế hoạch"));

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.cancelledAt()).isEqualTo(NOW);
        assertThat(response.refundAmount()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(booking.getStatusHistories()).hasSize(1);
    }

    @Test
    void createsPendingRefundForSuccessfulPayment() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        Payment payment = payment(booking, PaymentStatus.SUCCESS);
        arrange(booking, payment, CancellationDecision.allowed(50, 1_000_000, 1_000_000, RefundStatus.PENDING));

        var response = service.cancelBooking(
                BOOKING_CODE,
                new CancelBookingRequest(
                        "Đổi lịch",
                        new RefundInformationRequest(
                                RefundMethod.BANK_TRANSFER, "Vietcombank", "TRAN PHUOC ANH", "12345678")));

        assertThat(response.refundPercent()).isEqualTo(50);
        assertThat(response.refundAmount()).isEqualTo(1_000_000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getRefunds()).singleElement().satisfies(refund -> {
            assertThat(refund.getAmount()).isEqualByComparingTo("1000000");
            assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.PENDING);
            assertThat(refund.getIdempotencyKey()).isEqualTo("booking-cancellation:" + BOOKING_ID);
            assertThat(refund.getAccountNumber()).isEqualTo("12345678");
        });
        assertThat(payment.getTransactions()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getType()).isEqualTo(com.hanbay.hotel.payment.domain.PaymentTransactionType.REFUND);
            assertThat(transaction.getStatus())
                    .isEqualTo(com.hanbay.hotel.payment.domain.PaymentTransactionStatus.PENDING);
            assertThat(transaction.getAmount()).isEqualTo(1_000_000);
        });
    }

    private void arrange(Booking booking, Payment payment, CancellationDecision decision) {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(USER_ID));
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            when(paymentRepository.findFirstByBookingIdAndStatusOrderByPaidAtDesc(BOOKING_ID, PaymentStatus.SUCCESS))
                    .thenReturn(Optional.of(payment));
        } else {
            when(paymentRepository.findFirstByBookingIdAndStatusOrderByPaidAtDesc(BOOKING_ID, PaymentStatus.SUCCESS))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(BOOKING_ID, PaymentStatus.PENDING))
                    .thenReturn(Optional.of(payment));
        }
        when(clock.instant()).thenReturn(NOW);
        when(cancellationPolicy.evaluate(booking, payment.getStatus(), NOW)).thenReturn(decision);
    }

    private Booking booking(BookingStatus status) {
        User user = User.builder().id(USER_ID).build();
        return Booking.builder()
                .id(BOOKING_ID)
                .bookingCode(BOOKING_CODE)
                .bookedBy(user)
                .status(status)
                .totalAmount(2_000_000)
                .build();
    }

    private Payment payment(Booking booking, PaymentStatus status) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .paymentCode("PAY-" + UUID.randomUUID())
                .amount(2_000_000)
                .status(status)
                .expiresAt(NOW.plusSeconds(900))
                .build();
    }
}
