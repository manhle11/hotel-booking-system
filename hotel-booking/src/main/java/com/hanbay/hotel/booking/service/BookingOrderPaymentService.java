package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.exception.BookingNotFoundException;
import com.hanbay.hotel.booking.repository.BookingOrderRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.payment.dto.request.RefundInformationRequest;
import com.hanbay.hotel.payment.dto.request.SePayWebhookRequest;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.payment.repository.PaymentTransactionRepository;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lock inventory types by UUID, then the order, its bookings by UUID, and its payments. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BookingOrderPaymentService {
    private final BookingOrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final Clock clock;
    private final RoomTypeRepository roomTypeRepository;

    public BookingOrder lockOrder(UUID orderId) {
        // Serialize confirmation across the hold deadline with new reservations using the same inventory locks.
        bookingRepository.findRoomTypeIdsByOrderId(orderId).stream()
                .sorted()
                .forEach(id -> roomTypeRepository.findByIdForUpdate(id).orElseThrow());
        BookingOrder order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn đặt phòng"));
        bookingRepository.findIdsByOrderId(orderId).forEach(id -> bookingRepository
                .findByIdWithAssignedRoomForUpdate(id)
                .orElseThrow());
        return order;
    }

    public void settle(
            UUID orderId, String paymentCode, SePayWebhookRequest request, PaymentWebhookEvent event, Instant now) {
        BookingOrder order = lockOrder(orderId);
        Payment payment =
                paymentRepository.findByPaymentCodeForUpdate(paymentCode).orElseThrow();
        Instant settlementTime = clock.instant();
        Instant occurredAt =
                request.transactionDate().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .providerTransactionId(Long.toString(request.id()))
                .transactionRef(request.referenceCode())
                .type(PaymentTransactionType.PAYMENT)
                .status(PaymentTransactionStatus.SUCCESS)
                .amount(request.transferAmount())
                .currency("VND")
                .providerMessage(request.description())
                .rawPayload(event.getPayload())
                .occurredAt(occurredAt)
                .receivedAt(now)
                .build();
        payment.addTransaction(transaction);
        transactionRepository.saveAndFlush(transaction);

        if (order.isActive(settlementTime) && payment.isPendingAndActive(settlementTime)) {
            long remaining = request.transferAmount();
            for (PaymentAllocation allocation : payment.getAllocations().stream()
                    .sorted(Comparator.comparing(a -> a.getBooking().getOrderRoomNumber()))
                    .toList()) {
                remaining -= allocation.apply(remaining);
            }
            if (remaining > 0) {
                refund(
                        payment,
                        remaining,
                        "Hoàn tiền thanh toán vượt tổng đơn",
                        "overpayment:" + request.id(),
                        now,
                        null);
            }
            if (payment.getAllocations().stream().allMatch(a -> a.getAppliedAmount() == a.getAmount())) {
                payment.markSuccessful(occurredAt);
                order.confirm(settlementTime);
            }
        } else {
            // Expiry/cancellation may already have released inventory. Never reconfirm it on a late transfer.
            expire(order, settlementTime);
            reconcileReceivedPayment(payment, occurredAt);
            refund(
                    payment,
                    request.transferAmount(),
                    "Hoàn tiền chuyển khoản khi đơn không còn hiệu lực",
                    "late-order-payment:" + request.id(),
                    now,
                    null);
        }
        event.markProcessed(payment, now);
    }

    public void expire(BookingOrder order, Instant now) {
        if (order.getStatus() != BookingOrderStatus.PENDING
                || order.getExpiresAt().isAfter(now)) return;
        order.expire(now);
        closePendingPayments(order, now, "Hoàn tiền đơn hết hạn", "order-expiration:");
    }

    public void closePendingPayments(BookingOrder order, Instant now, String reason, String keyPrefix) {
        closePendingPayments(order, now, reason, keyPrefix, null);
    }

    public void closePendingPayments(
            BookingOrder order, Instant now, String reason, String keyPrefix, RefundInformationRequest information) {
        for (Payment payment : paymentRepository.findAllByOrderIdOrderByCreatedAtDesc(order.getId())) {
            if (payment.getStatus() != PaymentStatus.PENDING) continue;
            long received = payment.getAllocations().stream()
                    .mapToLong(PaymentAllocation::getAppliedAmount)
                    .sum();
            if (received > 0) {
                payment.markSuccessful(now);
                refund(payment, received, reason, keyPrefix + payment.getId(), now, information);
            } else {
                payment.cancelPending(now);
            }
        }
    }

    private void reconcileReceivedPayment(Payment payment, Instant now) {
        if (payment.getStatus() == PaymentStatus.PENDING) payment.markSuccessful(now);
        else if (payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.EXPIRED
                || payment.getStatus() == PaymentStatus.FAILED) payment.reconcileSuccessfulLatePayment(now);
    }

    public void refund(
            Payment payment,
            long amount,
            String reason,
            String key,
            Instant now,
            RefundInformationRequest information) {
        if (amount <= 0 || payment.getRefunds().stream().anyMatch(r -> key.equals(r.getIdempotencyKey()))) return;
        PaymentRefund.PaymentRefundBuilder builder = PaymentRefund.builder()
                .amount(BigDecimal.valueOf(amount))
                .currency("VND")
                .status(PaymentRefundStatus.PENDING)
                .reason(reason)
                .idempotencyKey(key)
                .requestedAt(now)
                .refundMethod(null);
        if (information != null) {
            builder.refundMethod(information.method())
                    .bankName(information.normalizedBankName())
                    .accountHolderName(information.normalizedAccountHolderName())
                    .accountNumber(information.normalizedAccountNumber());
        }
        payment.addRefund(builder.build());
        payment.addTransaction(PaymentTransaction.builder()
                .type(PaymentTransactionType.REFUND)
                .status(PaymentTransactionStatus.PENDING)
                .amount(amount)
                .currency("VND")
                .providerMessage(reason)
                .receivedAt(now)
                .build());
    }
}
