package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.response.BookingChangeSettlementResponse;
import com.hanbay.hotel.booking.dto.response.BookingChangeSettlementResponse.*;
import com.hanbay.hotel.booking.exception.BookingNotFoundException;
import com.hanbay.hotel.booking.repository.*;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingChangeSettlementService {
    private final BookingChangeRequestRepository requests;
    private final BookingRepository bookings;
    private final RoomTypeRepository types;
    private final BookingOrderPaymentService orders;
    private final BookingSettlementLedger ledger;
    private final Clock clock;
    private final com.hanbay.hotel.booking.config.HotelStayProperties properties;

    public PageResponse<BookingChangeSettlementResponse> list(String keyword, int page, int size) {
        PaginationValidator.validate(page, size, 100);
        return PageResponse.from(requests.findLatestAppliedRescheduleIds(keyword.trim(), PageRequest.of(page, size))
                .map(this::get));
    }

    public BookingChangeSettlementResponse get(UUID id) {
        String code = requests.findBookingCodeById(id)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy yêu cầu đổi lịch."));
        var orderId = bookings.findOrderIdForArrival(code);
        List<Booking> scope;
        if (orderId.isPresent()) scope = orders.lockOrder(orderId.get()).getBookings();
        else {
            types.findByIdForUpdate(bookings.findRoomTypeIdByBookingCode(code).orElseThrow())
                    .orElseThrow();
            scope = List.of(bookings.findByBookingCodeForUpdate(code).orElseThrow());
        }
        var request = requests.findByIdForUpdate(id).orElseThrow();
        if (request.getType() != BookingChangeRequest.Type.RESCHEDULE
                || request.getStatus() != BookingChangeRequest.Status.APPROVED)
            throw new BookingNotFoundException("Chỉ giám sát quyết toán của yêu cầu đổi lịch đã áp dụng.");
        List<Payment> payments = ledger.lockPayments(scope);
        Instant now = clock.instant();
        List<Room> rooms = scope.stream()
                .map(booking -> {
                    long available = ledger.available(booking, payments);
                    long applied = payments.stream()
                            .mapToLong(payment -> ledger.applied(booking, payment))
                            .sum();
                    boolean review = booking.isCancellationReviewRequired() || applied < booking.getPaidAmount();
                    return new Room(
                            booking.getBookingCode(),
                            booking.getRoomType().getName(),
                            booking.getStatus(),
                            booking.getTotalAmount(),
                            available,
                            Math.max(booking.getTotalAmount() - available, 0),
                            BookingOrderPaymentService.canCollectBalance(booking) ? booking.getBalanceDueAt() : null,
                            review);
                })
                .toList();
        Map<UUID, Booking> bookingById = new HashMap<>();
        scope.forEach(booking -> bookingById.put(booking.getId(), booking));
        List<Refund> refunds = new ArrayList<>();
        for (Payment payment : payments)
            for (PaymentRefund refund : payment.getRefunds()) {
                if (refund.getSourceBookingId() != null && !bookingById.containsKey(refund.getSourceBookingId()))
                    continue;
                if (refund.getSourceBookingId() == null
                        && payment.getOrder() == null
                        && !bookingById.containsKey(payment.getBooking().getId())) continue;
                String refundCode = refund.getSourceBookingId() != null
                        ? bookingById.get(refund.getSourceBookingId()).getBookingCode()
                        : payment.getOrder() == null
                                ? payment.getBooking().getBookingCode()
                                : payment.getOrder().getOrderCode();
                refunds.add(new Refund(
                        refund.getId(),
                        refundCode,
                        payment.getPaymentCode(),
                        refund.getAmount().longValueExact(),
                        refund.getStatus(),
                        refund.getRequestedAt(),
                        refund.getRefundedAt(),
                        refund.getProviderRefundId(),
                        refund.getRefundMethod() != null));
            }
        refunds.sort(Comparator.comparing(Refund::requestedAt).reversed());
        long refunded = refunds.stream()
                .filter(refund -> refund.status() == PaymentRefundStatus.SUCCESS)
                .mapToLong(Refund::amount)
                .sum();
        long pendingRefund = refunds.stream()
                .filter(refund -> refund.status() == PaymentRefundStatus.PENDING
                        || refund.status() == PaymentRefundStatus.PROCESSING)
                .mapToLong(Refund::amount)
                .sum();
        long unrequested = rooms.stream()
                .filter(room -> !room.needsReview())
                .mapToLong(room -> Math.max(room.availableAmount() - room.obligationAmount(), 0))
                .sum();
        long outstanding = rooms.stream().mapToLong(Room::outstandingAmount).sum();
        long available = rooms.stream().mapToLong(Room::availableAmount).sum();
        boolean review = rooms.stream().anyMatch(Room::needsReview);
        boolean overdue = rooms.stream()
                .anyMatch(room -> room.outstandingAmount() > 0
                        && room.balanceDueAt() != null
                        && !room.balanceDueAt().isAfter(now));
        Instant dueAt = rooms.stream()
                .filter(room -> room.outstandingAmount() > 0)
                .map(Room::balanceDueAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        String collection = review
                ? "REVIEW_REQUIRED"
                : outstanding == 0 ? "PAID" : overdue ? "OVERDUE" : available > 0 ? "PARTIALLY_PAID" : "UNPAID";
        boolean refundIssue =
                unrequested > 0 || refunds.stream().anyMatch(refund -> refund.status() == PaymentRefundStatus.FAILED);
        String refundStatus = refundIssue
                ? "REVIEW_REQUIRED"
                : pendingRefund > 0
                        ? refunded > 0 ? "PARTIALLY_REFUNDED" : "PENDING"
                        : refunded > 0 ? "REFUNDED" : "NOT_REQUIRED";
        String stayStatus = scope.stream().allMatch(booking -> booking.getStatus() == BookingStatus.CANCELLED)
                ? "CANCELLED"
                : scope.stream().allMatch(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                        ? "CONFIRMED"
                        : "MIXED";
        return new BookingChangeSettlementResponse(
                id,
                request.getBooking().getBookingCode(),
                request.getBooking().getOrder() == null
                        ? null
                        : request.getBooking().getOrder().getOrderCode(),
                scope.size(),
                request.getDecidedAt(),
                stayStatus,
                scope.get(0).getCheckInDate(),
                scope.get(0).getCheckOutDate(),
                rooms.stream().mapToLong(Room::obligationAmount).sum(),
                payments.stream().mapToLong(ledger::received).sum(),
                available,
                outstanding,
                collection,
                dueAt,
                overdue,
                refunded,
                pendingRefund,
                unrequested,
                refundStatus,
                review,
                snapshotAmount(request, "additionalAmount"),
                snapshotAmount(request, "refundAmount"),
                rooms,
                refunds,
                properties.getZone(),
                now);
    }

    private Long snapshotAmount(BookingChangeRequest request, String field) {
        return request.getSnapshot() != null && request.getSnapshot().get(field) instanceof Number number
                ? number.longValue()
                : null;
    }
}
