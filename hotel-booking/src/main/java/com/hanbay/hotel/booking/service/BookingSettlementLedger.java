package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.payment.dto.request.RefundInformationRequest;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingSettlementLedger {
    private final PaymentRepository payments;

    public List<Payment> lockPayments(List<Booking> bookings) {
        var ids = new TreeSet<UUID>();
        bookings.forEach(b -> payments.findAllForBookingSettlement(b.getId()).forEach(p -> ids.add(p.getId())));
        return ids.stream()
                .map(id -> payments.findByIdForUpdate(id).orElseThrow())
                .toList();
    }

    public List<PaymentRefund> refunds(Booking booking, Payment payment) {
        return payment.getRefunds().stream()
                .filter(r -> booking.getId().equals(r.getSourceBookingId())
                        || (payment.getOrder() == null
                                && payment.getBooking().getId().equals(booking.getId())
                                && r.getSourceBookingId() == null))
                .toList();
    }

    public long received(Payment payment) {
        return payment.getTransactions().stream()
                .filter(t -> t.getType() == PaymentTransactionType.PAYMENT
                        && t.getStatus() == PaymentTransactionStatus.SUCCESS)
                .mapToLong(PaymentTransaction::getAmount)
                .sum();
    }

    public boolean reserved(PaymentRefund refund) {
        return refund.getStatus() != PaymentRefundStatus.CANCELLED && refund.getStatus() != PaymentRefundStatus.FAILED;
    }

    public long applied(Booking booking, Payment payment) {
        return payment.getOrder() == null
                ? payment.getBooking().getId().equals(booking.getId()) ? received(payment) : 0
                : payment.getAllocations().stream()
                        .filter(a -> a.getBooking().getId().equals(booking.getId()))
                        .mapToLong(PaymentAllocation::getAppliedAmount)
                        .sum();
    }

    public long available(Booking booking, Payment payment) {
        long pool = received(payment)
                - payment.getRefunds().stream()
                        .filter(this::reserved)
                        .mapToLong(r -> r.getAmount().longValueExact())
                        .sum();
        long room = applied(booking, payment)
                - refunds(booking, payment).stream()
                        .filter(this::reserved)
                        .mapToLong(r -> r.getAmount().longValueExact())
                        .sum();
        return Math.max(0, Math.min(pool, room));
    }

    public long available(Booking booking, List<Payment> ledger) {
        return ledger.stream().mapToLong(p -> available(booking, p)).sum();
    }

    public long refundExcess(
            Booking booking,
            List<Payment> ledger,
            long retain,
            String key,
            String reason,
            Instant now,
            RefundInformationRequest destination) {
        long remaining = retain;
        long refunded = 0;
        for (Payment payment : ledger) {
            long available = available(booking, payment);
            long kept = Math.min(available, remaining);
            remaining -= kept;
            long amount = available - kept;
            if ((payment.getOrder() == null
                            ? payment.getBooking().getId().equals(booking.getId())
                            : payment.getAllocations().stream()
                                    .anyMatch(a -> a.getBooking().getId().equals(booking.getId())))
                    && payment.getStatus() == PaymentStatus.PENDING) {
                if (received(payment) > 0) payment.markSuccessful(now);
                else payment.cancelPending(now);
            }
            if (amount == 0) continue;
            var builder = PaymentRefund.builder()
                    .sourceBookingId(booking.getId())
                    .amount(BigDecimal.valueOf(amount))
                    .currency("VND")
                    .status(PaymentRefundStatus.PENDING)
                    .refundMethod(null)
                    .requestedAt(now)
                    .idempotencyKey(key + ":" + booking.getId() + ":" + payment.getId())
                    .reason(reason);
            if (destination != null)
                builder.refundMethod(destination.method())
                        .bankName(destination.normalizedBankName())
                        .accountHolderName(destination.normalizedAccountHolderName())
                        .accountNumber(destination.normalizedAccountNumber());
            payment.requestRefund(builder.build());
            refunded += amount;
        }
        return refunded;
    }
}
