package com.hanbay.hotel.payment.service.impl;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.policy.ClosedBookingFeePolicy;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.booking.service.BookingAccessTokenService;
import com.hanbay.hotel.booking.service.BookingSettlementLedger;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.payment.config.PaymentProperties;
import com.hanbay.hotel.payment.domain.Payment;
import com.hanbay.hotel.payment.domain.PaymentRefund;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import com.hanbay.hotel.payment.dto.request.CreatePaymentRequest;
import com.hanbay.hotel.payment.dto.response.CreatePaymentRequestResponse;
import com.hanbay.hotel.payment.dto.response.PaymentStatusResponse;
import com.hanbay.hotel.payment.dto.result.SePayQrInfo;
import com.hanbay.hotel.payment.exception.PaymentAlreadyCompletedException;
import com.hanbay.hotel.payment.exception.PaymentBookingNotFoundException;
import com.hanbay.hotel.payment.exception.PaymentBookingNotPayableException;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.payment.repository.PaymentTransactionRepository;
import com.hanbay.hotel.payment.service.PaymentService;
import com.hanbay.hotel.payment.service.SePayService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter PAYMENT_CODE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentProperties paymentProperties;
    private final SePayService sePayService;
    private final Clock clock;
    private final BookingAccessTokenService bookingAccessTokenService;
    private final CurrentUserProvider currentUserProvider;
    private final BookingSettlementLedger settlementLedger;
    private final com.hanbay.hotel.booking.service.BookingOrderPaymentService orderPayments;

    @Override
    @Transactional
    public CreatePaymentRequestResponse createPaymentRequest(CreatePaymentRequest request, String bookingAccessToken) {
        Instant now = clock.instant();
        bookingRepository.findOrderIdByBookingId(request.bookingId()).ifPresent(orderPayments::lockOrder);
        Booking booking = bookingRepository
                .findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> new PaymentBookingNotFoundException("Không tìm thấy đặt phòng cần thanh toán."));

        ensureCheckoutAccess(booking, bookingAccessToken);

        if (booking.getOrder() != null && booking.getStatus() == BookingStatus.PENDING) {
            throw new PaymentBookingNotPayableException("Vui lòng thanh toán tổng tiền trên đơn đặt nhiều phòng.");
        }

        validatePayableBooking(booking, now);
        if (booking.getOrder() != null && booking.getStatus() != BookingStatus.PENDING) {
            orderPayments.closePendingPayments(
                    booking.getOrder(),
                    now,
                    "Đối soát mã toàn đơn khi chuyển sang thanh toán riêng",
                    "replace-with-room-payment:");
        }
        com.hanbay.hotel.booking.policy.ArrivalPolicy.accept(booking, request.arrivalPolicyVersion());
        if (com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.VERSION.equals(request.arrivalPolicyVersion()))
            booking.acceptStandardTerms(clock.instant());
        long payableAmount = payableAmount(booking);

        Payment activePendingPayment = findActivePendingPayment(booking, now, payableAmount);
        if (activePendingPayment != null) {
            return toResponse(activePendingPayment);
        }

        closeNonReusablePendingPayments(booking, now, payableAmount);

        Payment payment = Payment.builder()
                .booking(booking)
                .paymentCode(generatePaymentCode(now))
                .amount(payableAmount)
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .idempotencyKey(additionalBalanceKey(booking))
                .expiresAt(calculatePaymentExpiration(booking, now))
                .build();

        return toResponse(paymentRepository.save(payment));
    }

    private String additionalBalanceKey(Booking booking) {
        if (booking.getStatus() == BookingStatus.PENDING) {
            return null;
        }
        // The booking lock and active-payment lookup make retries idempotent. Each renewed QR needs its own key.
        return (ClosedBookingFeePolicy.applies(booking) ? ClosedBookingFeePolicy.PAYMENT_PREFIX : "additional-balance:")
                + booking.getId() + ":" + UUID.randomUUID();
    }

    private void validatePayableBooking(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.CHECKED_IN
                && !ClosedBookingFeePolicy.applies(booking)) {
            throw new PaymentBookingNotPayableException("Trạng thái booking không cho phép thanh toán.");
        }
        if (booking.getStatus() == BookingStatus.PENDING
                && (booking.getExpiredAt() == null || !booking.getExpiredAt().isAfter(now))) {
            throw new PaymentBookingNotPayableException("Đặt phòng đã hết thời gian giữ phòng.");
        }
        if (payableAmount(booking) <= 0) {
            throw new PaymentAlreadyCompletedException("Đặt phòng không còn số dư cần thanh toán.");
        }
    }

    private long payableAmount(Booking booking) {
        if (ClosedBookingFeePolicy.applies(booking))
            return Math.max(
                    booking.getTotalAmount()
                            - settlementLedger.available(
                                    booking, paymentRepository.findAllForBookingSettlement(booking.getId())),
                    0);
        return Math.max(booking.getTotalAmount() - booking.getPaidAmount(), 0L);
    }

    private Payment findActivePendingPayment(Booking booking, Instant now, long payableAmount) {
        return paymentRepository
                .findAllByBookingIdAndStatusOrderByCreatedAtDesc(booking.getId(), PaymentStatus.PENDING)
                .stream()
                .filter(payment -> payment.getOrder() == null)
                .filter(payment -> payment.isPendingAndActive(now)
                        && payment.getAmount() == payableAmount
                        && (!ClosedBookingFeePolicy.applies(booking)
                                || (payment.getIdempotencyKey() != null
                                        && payment.getIdempotencyKey()
                                                .startsWith(ClosedBookingFeePolicy.PAYMENT_PREFIX))))
                .findFirst()
                .orElse(null);
    }

    private void closeNonReusablePendingPayments(Booking booking, Instant now, long payableAmount) {
        paymentRepository
                .findAllByBookingIdAndStatusOrderByCreatedAtDesc(booking.getId(), PaymentStatus.PENDING)
                .stream()
                .filter(payment -> payment.getOrder() == null)
                .forEach(payment -> {
                    if (payment.isPendingAndExpired(now)) {
                        payment.markExpired(now);
                    } else if (payment.getAmount() != payableAmount
                            || (ClosedBookingFeePolicy.applies(booking)
                                    && (payment.getIdempotencyKey() == null
                                            || !payment.getIdempotencyKey()
                                                    .startsWith(ClosedBookingFeePolicy.PAYMENT_PREFIX)))) {
                        payment.cancelPending(now);
                    }
                });
    }

    private Instant calculatePaymentExpiration(Booking booking, Instant now) {
        Instant configuredExpiration = now.plusSeconds(paymentProperties.expirationMinutes() * 60L);
        return booking.getStatus() == BookingStatus.PENDING
                        && booking.getExpiredAt() != null
                        && booking.getExpiredAt().isBefore(configuredExpiration)
                ? booking.getExpiredAt()
                : configuredExpiration;
    }

    private String generatePaymentCode(Instant now) {
        return "PAY-" + PAYMENT_CODE_TIMESTAMP.format(now) + "-"
                + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private CreatePaymentRequestResponse toResponse(Payment payment) {
        return CreatePaymentRequestResponse.from(
                payment, sePayService.generateQr(payment.getAmount(), payment.getPaymentCode()));
    }

    @Override
    @Transactional
    public PaymentStatusResponse getPaymentStatus(UUID paymentId, String bookingAccessToken) {
        Payment payment = findPayment(paymentId);
        ensureCheckoutAccess(payment.getBooking(), bookingAccessToken);
        if (payment.getOrder() == null) payment = expirePendingPaymentIfNeeded(payment, clock.instant());
        Booking booking = payment.getBooking();
        var order = payment.getOrder();
        PaymentRefund refund = findRelevantRefund(payment);
        long receivedAmount = paymentTransactionRepository.sumSuccessfulPaymentAmount(payment.getId());
        long remainingAmount = Math.max(0, payment.getAmount() - receivedAmount);
        SePayQrInfo qrInfo = sePayService.generateQr(remainingAmount, payment.getPaymentCode());
        return new PaymentStatusResponse(
                payment.getId(),
                order == null ? booking.getId() : order.getId(),
                payment.referenceCode(),
                payment.getPaymentCode(),
                payment.isPendingAndExpired(clock.instant())
                        ? com.hanbay.hotel.payment.domain.PaymentStatus.EXPIRED
                        : payment.getStatus(),
                order == null
                        ? booking.getStatus()
                        : BookingStatus.valueOf(order.getStatus().name()),
                order == null
                        ? booking.getRoomType().getName()
                        : order.getBookings().size() + " phòng",
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                order == null
                        ? booking.getAdultCount()
                        : order.getBookings().stream()
                                .mapToInt(Booking::getAdultCount)
                                .sum(),
                order == null
                        ? booking.getChildCount()
                        : order.getBookings().stream()
                                .mapToInt(Booking::getChildCount)
                                .sum(),
                java.time.temporal.ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate()),
                payment.getAmount(),
                receivedAmount,
                payment.getCurrency(),
                refund == null ? null : refund.getStatus(),
                refund == null ? null : refund.getAmount(),
                refund == null ? null : refund.getReason(),
                qrInfo.qrUrl(),
                qrInfo.bankCode(),
                qrInfo.accountNumber(),
                qrInfo.accountName(),
                qrInfo.transferContent(),
                payment.getExpiresAt(),
                payment.getPaidAt(),
                remainingAmount,
                payment.getIdempotencyKey() != null
                        && payment.getIdempotencyKey().startsWith(ClosedBookingFeePolicy.PAYMENT_PREFIX));
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new PaymentBookingNotFoundException("Không tìm thấy khoản thanh toán."));
    }

    private Payment expirePendingPaymentIfNeeded(Payment payment, Instant now) {
        if (payment.isPendingAndExpired(now)) {
            paymentRepository.markPendingExpired(payment.getId(), now);
            return findPayment(payment.getId());
        }
        return payment;
    }

    private PaymentRefund findRelevantRefund(Payment payment) {
        return payment.getRefunds().stream()
                .max(Comparator.comparing((PaymentRefund refund) -> isActiveRefund(refund.getStatus()))
                        .thenComparing(PaymentRefund::getRequestedAt))
                .orElse(null);
    }

    private boolean isActiveRefund(PaymentRefundStatus status) {
        return status == PaymentRefundStatus.PENDING || status == PaymentRefundStatus.PROCESSING;
    }

    private void ensureCheckoutAccess(Booking booking, String bookingAccessToken) {
        UUID currentUserId = currentUserProvider.currentUserId().orElse(null);
        boolean owner =
                booking.getBookedBy() != null && booking.getBookedBy().getId().equals(currentUserId);
        String tokenHash = booking.getOrder() == null
                ? booking.getAccessTokenHash()
                : booking.getOrder().getAccessTokenHash();
        if (!owner && !bookingAccessTokenService.matches(bookingAccessToken, tokenHash)) {
            throw new PaymentBookingNotFoundException("Không tìm thấy đặt phòng cần thanh toán.");
        }
    }
}
