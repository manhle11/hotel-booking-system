package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.request.ArrivalHandlingRequest;
import com.hanbay.hotel.booking.dto.response.ArrivalHandlingResponse;
import com.hanbay.hotel.booking.dto.response.ArrivalProgress;
import com.hanbay.hotel.booking.exception.*;
import com.hanbay.hotel.booking.policy.ArrivalPolicy;
import com.hanbay.hotel.booking.policy.ClosedBookingFeePolicy;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.policy.UnifiedBookingPolicy;
import com.hanbay.hotel.booking.repository.BookingChangeRequestRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.payment.dto.request.RefundInformationRequest;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ArrivalHandlingService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final BookingOrderPaymentService orderPayments;
    private final BookingAccessTokenService tokens;
    private final CurrentUserProvider currentUser;
    private final UserRepository users;
    private final HotelStayPolicy policy;
    private final Clock clock;
    private final BookingChangeRequestRepository changeRequests;
    private final BookingSettlementLedger settlementLedger;

    public ArrivalHandlingResponse preview(String code) {
        Booking booking = lock(code);
        return response(booking, lockPayments(booking));
    }

    public ArrivalHandlingResponse contact(String code, ArrivalHandlingRequest.Contact request) {
        Booking booking = lock(code);
        var selected = selectArrivalRooms(booking, request.bookingCodes());
        User actor = actor();
        Instant now = clock.instant();
        for (Booking b : selected) {
            b.recordArrivalContact(
                    actor,
                    ArrivalContact.Channel.OTHER,
                    ArrivalContact.Outcome.UNDECIDED,
                    request.note().trim(),
                    now);
            recordScope(b, selected, actor);
        }
        return response(booking, lockPayments(booking));
    }

    public ArrivalHandlingResponse structuredContact(String code, ArrivalHandlingRequest.StructuredContact request) {
        Booking booking = lock(code);
        var selected = selectArrivalRooms(booking, request.bookingCodes());
        if (request.outcome() == ArrivalContact.Outcome.CANCELLATION
                || request.outcome() == ArrivalContact.Outcome.RESCHEDULE)
            throw StayModificationException.badRequest(
                    "ARRIVAL_CUSTOMER_REQUEST_REQUIRED",
                    "Khách xác nhận hủy/đề nghị đổi lịch cần dùng luồng riêng; phạm vi hiện tại là toàn đơn.");
        User actor = actor();
        Instant now = clock.instant();
        for (Booking b : selected) {
            b.recordArrivalContact(
                    actor,
                    request.channel(),
                    request.outcome(),
                    request.note() == null ? "" : request.note().trim(),
                    now);
            recordScope(b, selected, actor);
        }
        return response(booking, lockPayments(booking));
    }

    public ArrivalHandlingResponse estimate(String code, ArrivalHandlingRequest.Estimate request) {
        Booking booking = lock(code);
        var selected = selectArrivalRooms(booking, request.bookingCodes());
        selected.forEach(b -> validateEstimate(b, request.expectedAt()));
        User actor = actor();
        for (Booking b : selected) {
            b.updateExpectedArrival(
                    request.expectedAt(),
                    actor,
                    request.note() == null ? "" : request.note().trim());
            recordScope(b, selected, actor);
        }
        return response(booking, lockPayments(booking));
    }

    public ArrivalHandlingResponse decideHold(String code, ArrivalHandlingRequest.HoldDecision request) {
        if (!isManager()) throw new AccessDeniedException("Chỉ quản lý được duyệt hoặc từ chối nhận sau hạn.");
        Booking booking = lock(code);
        var selected = selectArrivalRooms(booking, request.bookingCodes());
        for (Booking b : selected) {
            if (!request.requestedUntil().equals(b.getArrivalHoldRequestedUntil())
                    || request.requestedUntil().equals(b.getArrivalHoldUntil()))
                throw StayModificationException.conflict(
                        "ARRIVAL_HOLD_CHANGED",
                        "Đề nghị của " + b.getBookingCode()
                                + " đã thay đổi hoặc khác hạn đang duyệt. Xem lại phạm vi trước khi quyết định.");
            if (request.approve()) {
                validateHold(b, request.requestedUntil());
                if (b.getExpectedArrivalAt() != null
                        && !request.requestedUntil().isAfter(b.getExpectedArrivalAt()))
                    throw StayModificationException.conflict(
                            "ARRIVAL_HOLD_CHANGED",
                            b.getBookingCode()
                                    + ": giờ hẹn đã vượt hạn đề nghị; cần thống nhất hạn mới trước khi duyệt.");
            }
        }
        User actor = actor();
        for (Booking b : selected) {
            if (request.approve())
                b.holdArrival(
                        request.requestedUntil(), actor, true, request.note().trim());
            else b.rejectArrivalHold(actor, request.note().trim());
            recordScope(b, selected, actor);
        }
        return response(booking, lockPayments(booking));
    }

    private void validateHold(Booking booking, Instant until) {
        if (!until.isAfter(clock.instant()) || until.isAfter(policy.departureDeadline(booking.getCheckOutDate())))
            throw StayModificationException.badRequest(
                    "ARRIVAL_HOLD_INVALID",
                    booking.getBookingCode() + ": hạn tiếp nhận phải ở tương lai và không vượt giờ trả phòng đã đặt.");
    }

    private void validateEstimate(Booking booking, Instant expectedAt) {
        if (expectedAt.isBefore(clock.instant())
                || expectedAt.isBefore(booking.getCheckInDate()
                        .atStartOfDay(policy.getProperties().zoneId())
                        .toInstant())
                || !expectedAt.isBefore(policy.departureDeadline(booking.getCheckOutDate())))
            throw StayModificationException.badRequest(
                    "ARRIVAL_ESTIMATE_INVALID",
                    booking.getBookingCode()
                            + ": giờ dự kiến đến phải từ hiện tại, trong kỳ ở và trước giờ trả phòng.");
    }

    public ArrivalHandlingResponse hold(String code, ArrivalHandlingRequest.Hold request) {
        Booking booking = lock(code);
        var selected = selectArrivalRooms(booking, request.bookingCodes());
        for (Booking b : selected) {
            validateHold(b, request.until());
            if (request.expectedAt() != null) {
                validateEstimate(b, request.expectedAt());
                if (!request.until().isAfter(request.expectedAt())
                        || !request.until().isAfter(policy.receptionDeadline(b)))
                    throw StayModificationException.badRequest(
                            "ARRIVAL_HOLD_INVALID",
                            b.getBookingCode()
                                    + ": hạn đề nghị phải sau giờ khách hẹn và kéo dài hạn tiếp nhận hiện tại.");
            }
        }
        User actor = actor();
        for (Booking b : selected) {
            if (request.expectedAt() != null)
                b.updateExpectedArrival(
                        request.expectedAt(), actor, request.note().trim());
            boolean approved = isManager() || !request.until().isAfter(policy.arrivalCutoff(b.getCheckInDate()));
            b.recordArrivalContact(actor, request.note().trim());
            b.holdArrival(request.until(), actor, approved, request.note().trim());
            recordScope(b, selected, actor);
        }
        return response(booking, lockPayments(booking));
    }

    private List<Booking> selectArrivalRooms(Booking anchor, List<String> codes) {
        if (codes == null) {
            ensureConfirmed(anchor);
            return List.of(anchor);
        }
        Set<String> selection = new HashSet<>(codes);
        if (codes.isEmpty()
                || codes.size() > 10
                || selection.size() != codes.size()
                || !selection.contains(anchor.getBookingCode()))
            throw StayModificationException.badRequest(
                    "ARRIVAL_SCOPE_INVALID", "Chọn từ 1 đến 10 phòng, gồm booking đang mở, không trùng mã.");
        List<Booking> members =
                anchor.getOrder() == null ? List.of(anchor) : anchor.getOrder().getBookings();
        List<Booking> selected = members.stream()
                .filter(b -> selection.contains(b.getBookingCode()))
                .sorted(Comparator.comparing(Booking::getBookingCode))
                .toList();
        if (selected.size() != selection.size())
            throw StayModificationException.badRequest(
                    "ARRIVAL_SCOPE_INVALID",
                    "Chỉ được chọn các phòng thuộc cùng đơn; không dùng tên hoặc số điện thoại để gom phòng.");
        for (Booking b : selected) {
            if (b.getStatus() != BookingStatus.CONFIRMED || b.getCheckedInAt() != null)
                throw StayModificationException.conflict(
                        "ARRIVAL_SCOPE_CHANGED",
                        b.getBookingCode() + " đã nhận hoặc kết thúc; tải lại và chọn lại phạm vi.");
        }
        return selected;
    }

    private void recordScope(Booking booking, List<Booking> selected, User actor) {
        if (selected.size() > 1)
            booking.recordStaffNote(
                    actor,
                    "Áp dụng chung cho " + selected.size() + " phòng cùng đơn: "
                            + String.join(
                                    ", ",
                                    selected.stream()
                                            .map(Booking::getBookingCode)
                                            .toList()));
    }

    public ArrivalHandlingResponse markNoShow(String code, ArrivalHandlingRequest.NoShow request) {
        Booking booking = lock(code);
        List<Payment> ledger = lockPayments(booking);
        if (booking.getStatus() == BookingStatus.NO_SHOW) return response(booking, ledger);
        if (request.outcome() == ArrivalContact.Outcome.CANCELLATION
                || request.outcome() == ArrivalContact.Outcome.RESCHEDULE)
            throw StayModificationException.badRequest(
                    "NO_SHOW_CUSTOMER_REQUEST",
                    "Khách chủ động hủy/đổi lịch phải xử lý bằng yêu cầu riêng, không đánh no-show.");
        ArrivalHandlingResponse preview = response(booking, ledger);
        if (!preview.canMarkNoShow())
            throw StayModificationException.conflict("NO_SHOW_NOT_ALLOWED", preview.blockReason());
        if (!preview.previewToken().equals(request.previewToken())) {
            throw StayModificationException.conflict(
                    "ARRIVAL_PREVIEW_CHANGED",
                    "Booking hoặc thanh toán đã thay đổi; xem lại quyết toán trước khi xác nhận.");
        }
        long fee = request.feeAmount() == null ? preview.standardFee() : request.feeAmount();
        if (fee < 0 || fee > preview.standardFee()) {
            throw StayModificationException.badRequest(
                    "NO_SHOW_FEE_INVALID", "Phí phải từ 0 đến tiền phòng đêm đầu đã chốt.");
        }
        if ((fee != preview.standardFee() || preview.legacyPolicy()) && !isManager()) {
            throw new AccessDeniedException(
                    "Quản lý phải duyệt miễn/giảm phí hoặc booking chưa có điều khoản no-show.");
        }
        long remainingFee = fee;
        Instant now = clock.instant();
        for (Payment payment : ledger) {
            long available = availableFor(booking, payment);
            long retained = Math.min(available, remainingFee);
            remainingFee -= retained;
            long refundAmount = available - retained;
            if (payment.getOrder() == null && payment.getStatus() == PaymentStatus.PENDING) {
                if (received(payment) > 0) payment.markSuccessful(now);
                else payment.cancelPending(now);
            }
            if (refundAmount > 0) {
                payment.requestRefund(PaymentRefund.builder()
                        .sourceBookingId(booking.getId())
                        .amount(BigDecimal.valueOf(refundAmount))
                        .currency("VND")
                        .status(PaymentRefundStatus.PENDING)
                        .refundMethod(null)
                        .reason("Hoàn booking không đến " + booking.getBookingCode())
                        .idempotencyKey("no-show:" + booking.getId() + ":" + payment.getId())
                        .requestedAt(now)
                        .build());
            }
        }
        booking.recordArrivalContact(
                actor(),
                request.channel() == null ? ArrivalContact.Channel.OTHER : request.channel(),
                request.outcome() == null ? ArrivalContact.Outcome.UNDECIDED : request.outcome(),
                request.contactNote().trim(),
                now);
        if (UnifiedBookingPolicy.applies(booking))
            booking.recordPolicyFee(
                    fee, "NO_SHOW", actor(), now, request.reason().trim());
        booking.markNoShow(
                actor(),
                now,
                fee,
                UnifiedBookingPolicy.applies(booking) ? UnifiedBookingPolicy.VERSION : ArrivalPolicy.VERSION,
                "Xác nhận không đến; chính sách "
                        + (UnifiedBookingPolicy.applies(booking) ? UnifiedBookingPolicy.VERSION : ArrivalPolicy.VERSION)
                        + "; phí " + fee + " VND: " + request.reason().trim());
        payments.flush(); // Materialize refund IDs and audit timestamps before returning the settlement.
        return response(booking, ledger);
    }

    public ArrivalHandlingResponse releaseHold(String code, ArrivalHandlingRequest.Contact request) {
        if (!isManager()) throw new AccessDeniedException("Chỉ quản lý được kết thúc thỏa thuận giữ phòng.");
        Booking booking = lock(code);
        var selected = selectArrivalRooms(booking, request.bookingCodes());
        User actor = actor();
        for (Booking b : selected) {
            b.releaseArrivalHold(actor, request.note().trim());
            recordScope(b, selected, actor);
        }
        return response(booking, lockPayments(booking));
    }

    public ArrivalHandlingResponse reduceFee(String code, long fee, String reason) {
        if (!isManager()) throw new AccessDeniedException("Chỉ quản lý được giảm/miễn phí.");
        Booking b = lock(code);
        if (!UnifiedBookingPolicy.applies(b)
                || b.isCancellationReviewRequired()
                || (b.getStatus() != BookingStatus.CONFIRMED
                        && b.getStatus() != BookingStatus.CANCELLED
                        && b.getStatus() != BookingStatus.NO_SHOW))
            throw StayModificationException.badRequest(
                    "FEE_ADJUSTMENT_NOT_ALLOWED", "Booking không đủ điều kiện điều chỉnh phí.");
        if (fee < 0 || fee > b.getPolicyFeeTotal())
            throw StayModificationException.badRequest("FEE_INVALID", "Chỉ được giảm/miễn tổng phí đã phát sinh.");
        List<Payment> ledger = lockPayments(b);
        if (fee == b.getPolicyFeeTotal()) return response(b, ledger);
        long available = ledger.stream().mapToLong(p -> availableFor(b, p)).sum();
        long retain = b.getTotalAmount() - b.getPolicyFeeTotal() + fee;
        // Reuse the settlement ledger so reserved/paid refunds cannot be reused.
        settlementLedger.refundExcess(b, ledger, retain, "fa:" + UUID.randomUUID(), reason, clock.instant(), null);
        b.applyFeeReduction(fee, available, actor(), clock.instant(), reason);
        return response(b, ledger);
    }

    public ArrivalHandlingResponse collectFee(
            String code, UUID requestId, long amount, long expectedOutstanding, String note) {
        Booking booking = lock(code);
        var ledger = lockPayments(booking);
        if (!ClosedBookingFeePolicy.applies(booking))
            throw StayModificationException.conflict(
                    "FEE_COLLECTION_NOT_ALLOWED", "Chỉ thu phí của booking đã hủy/không đến và đã quyết toán.");
        String key = "policy-fee-cash:" + booking.getId() + ":" + requestId;
        var existing = payments.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (existing.getAmount() != amount)
                throw StayModificationException.conflict(
                        "FEE_COLLECTION_CHANGED", "Mã xác nhận đã dùng cho số tiền khác; tra cứu lại kết quả.");
            return response(booking, ledger);
        }
        long available = settlementLedger.available(booking, ledger);
        long due = Math.max(booking.getTotalAmount() - available, 0);
        if (due != expectedOutstanding || amount <= 0 || amount > due)
            throw StayModificationException.conflict(
                    "FEE_COLLECTION_CHANGED",
                    "Công nợ đã thay đổi hoặc số tiền thu vượt số còn phải thu; tải lại trước khi xác nhận.");
        User staff = actor();
        Instant now = clock.instant();
        for (Payment pending : ledger)
            if (pending.getOrder() == null && pending.getStatus() == PaymentStatus.PENDING) pending.cancelPending(now);
        Payment payment = Payment.builder()
                .booking(booking)
                .paymentCode("CASH-" + UUID.randomUUID())
                .amount(amount)
                .currency("VND")
                .idempotencyKey(key)
                .expiresAt(now)
                .build();
        payment.markSuccessful(now);
        payment.addTransaction(PaymentTransaction.builder()
                .type(PaymentTransactionType.PAYMENT)
                .status(PaymentTransactionStatus.SUCCESS)
                .amount(amount)
                .currency("VND")
                .transactionRef(payment.getPaymentCode())
                .providerMessage("Thu tiền mặt phí hủy/không đến")
                .rawPayload(
                        Map.of("method", "CASH", "collectedBy", staff.getId().toString(), "note", note))
                .occurredAt(now)
                .receivedAt(now)
                .build());
        payments.saveAndFlush(payment);
        booking.recordSettledFeeCollection(
                Math.min(booking.getTotalAmount(), available + amount),
                staff,
                "Đã thu tiền mặt phí hủy/không đến: " + amount + " VND; " + note);
        return response(booking, lockPayments(booking));
    }

    public record PolicySummary(
            String bookingCode,
            String version,
            String terms,
            Instant acceptedAt,
            Long firstNightAmount,
            long accruedFee,
            Instant freeCancellationUntil,
            Instant halfFeeUntil,
            String zone,
            boolean legacy,
            boolean settlementReviewRequired,
            java.util.List<FeeEntry> fees) {}

    public record FeeEntry(String event, long amount, long total, Instant at, String reason) {}

    public PolicySummary customerPolicy(String code, String accessToken) {
        Booking b = lock(code);
        ensureOwner(b, accessToken);
        boolean pending = b.getStatus() == BookingStatus.PENDING;
        Instant arrival = b.getCheckInDate()
                .atTime(policy.getProperties().getCheckInTime())
                .atZone(policy.getProperties().zoneId())
                .toInstant();
        return new PolicySummary(
                code,
                pending ? UnifiedBookingPolicy.VERSION : b.getTermsVersion(),
                pending ? UnifiedBookingPolicy.TERMS : b.getAcceptedTerms(),
                b.getTermsAcceptedAt(),
                pending ? Long.valueOf(UnifiedBookingPolicy.firstNight(b)) : b.getOriginalFirstNightAmount(),
                b.getPolicyFeeTotal(),
                arrival.minus(java.time.Duration.ofHours(48)),
                arrival.minus(java.time.Duration.ofHours(24)),
                policy.getProperties().getZone(),
                !pending && !UnifiedBookingPolicy.applies(b),
                b.isCancellationReviewRequired(),
                b.getPolicyFees().stream()
                        .sorted(Comparator.comparing(BookingPolicyFee::getOccurredAt))
                        .map(f -> new FeeEntry(
                                f.getEvent(), f.getAmount(), f.getTotalAmount(), f.getOccurredAt(), f.getReason()))
                        .toList());
    }

    public ArrivalHandlingResponse customerSettlement(String code, String accessToken) {
        Booking booking = lock(code);
        ensureOwner(booking, accessToken);
        return response(booking, lockPayments(booking), false);
    }

    public ArrivalHandlingResponse supplyRefundInformation(
            String code, String accessToken, RefundInformationRequest request) {
        Booking booking = lock(code);
        ensureOwner(booking, accessToken);
        List<Payment> ledger = lockPayments(booking);
        for (Payment payment : ledger) {
            relevantRefunds(booking, payment).stream()
                    .filter(r -> r.getStatus() == PaymentRefundStatus.PENDING && r.getRefundMethod() == null)
                    .forEach(r -> r.supplyDestination(
                            request.method(),
                            request.normalizedBankName(),
                            request.normalizedAccountHolderName(),
                            request.normalizedAccountNumber()));
        }
        return response(booking, ledger, false);
    }

    private Booking lock(String code) {
        bookings.findOrderIdForArrival(code).ifPresent(orderPayments::lockOrder);
        return bookings.findByBookingCodeForUpdate(code)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy booking"));
    }

    private List<Payment> lockPayments(Booking booking) {
        return payments.findAllForBookingSettlement(booking.getId()).stream()
                .map(p -> payments.findByIdForUpdate(p.getId()).orElseThrow())
                .toList();
    }

    private ArrivalHandlingResponse response(Booking booking, List<Payment> ledger) {
        return response(booking, ledger, true);
    }

    private ArrivalHandlingResponse response(Booking booking, List<Payment> ledger, boolean includeHistory) {
        Instant now = clock.instant();
        boolean staffCancellation =
                booking.getStatus() == BookingStatus.CANCELLED && booking.getCancellationOriginalTotal() != null;
        boolean closed = booking.getStatus() == BookingStatus.NO_SHOW || staffCancellation;
        long standardFee = staffCancellation
                ? Objects.requireNonNullElse(booking.getCancellationFee(), 0L)
                : ArrivalPolicy.standardFee(booking);
        long fee = staffCancellation
                ? Objects.requireNonNullElse(booking.getCancellationFee(), 0L)
                : closed ? booking.getNoShowFee() : standardFee;
        long available =
                ledger.stream().mapToLong(p -> availableFor(booking, p)).sum();
        long applied = ledger.stream().mapToLong(p -> appliedTo(booking, p)).sum();
        var pendingChange = changeRequests
                .findByScopeKeyAndStatus(BookingChangeRequest.scopeOf(booking), BookingChangeRequest.Status.PENDING)
                .orElse(null);
        String block = booking.getStatus() != BookingStatus.CONFIRMED || booking.getCheckedInAt() != null
                ? "Booking không còn ở trạng thái đã xác nhận chưa nhận phòng."
                : pendingChange != null
                        ? "Khách đang có yêu cầu hủy/đổi lịch chờ quản lý xử lý; không đánh no-show."
                        : booking.getArrivalHoldRequestedUntil() != null
                                        && !Objects.equals(
                                                booking.getArrivalHoldRequestedUntil(), booking.getArrivalHoldUntil())
                                ? "Đang chờ quản lý quyết định nhận sau hạn; xử lý đề nghị trước khi đánh no-show."
                                : now.isBefore(policy.arrivalCutoff(booking.getCheckInDate()))
                                        ? "Chưa đến cutoff xác nhận khách không đến."
                                        : booking.getArrivalHoldUntil() != null
                                                        && now.isBefore(booking.getArrivalHoldUntil())
                                                ? "Thỏa thuận giữ phòng vẫn còn hiệu lực."
                                                : applied < booking.getPaidAmount()
                                                        ? "Tiền đã trả chưa khớp sổ giao dịch; cần quản lý đối soát."
                                                        : null;
        List<PaymentRefund> refunds = ledger.stream()
                .flatMap(p -> relevantRefunds(booking, p).stream())
                .toList();
        String fingerprint = booking.getId() + ":" + booking.getStatus() + ":" + booking.getTotalAmount() + ":"
                + booking.getPaidAmount() + ":" + available + ":" + standardFee + ":" + booking.getArrivalHoldUntil()
                + ":" + booking.getArrivalPolicyVersion();
        return new ArrivalHandlingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                staffCancellation
                        ? "STAFF-CANCELLATION"
                        : closed
                                ? booking.getNoShowPolicyVersion()
                                : UnifiedBookingPolicy.applies(booking)
                                        ? UnifiedBookingPolicy.VERSION
                                        : ArrivalPolicy.VERSION,
                !ArrivalPolicy.VERSION.equals(booking.getArrivalPolicyVersion())
                        && !UnifiedBookingPolicy.applies(booking),
                block == null,
                block,
                policy.arrivalCutoff(booking.getCheckInDate()),
                policy.departureDeadline(booking.getCheckOutDate()),
                now,
                policy.getProperties().getZone(),
                staffCancellation
                        ? booking.getCancellationOriginalTotal()
                        : closed ? booking.getNoShowOriginalTotal() : booking.getTotalAmount(),
                booking.getPaidAmount(),
                standardFee,
                fee,
                closed
                        ? refunds.stream()
                                .filter(this::reserved)
                                .mapToLong(r -> r.getAmount().longValueExact())
                                .sum()
                        : Math.max(available - fee, 0),
                Math.max(fee - available, 0),
                booking.getArrivalHoldUntil(),
                booking.getArrivalHoldRequestedUntil(),
                booking.getNoShowAt(),
                tokens.hash(fingerprint),
                includeHistory
                        ? booking.getStatusHistories().stream()
                                .sorted(Comparator.comparing(
                                        BookingStatusHistory::getCreatedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(h -> new ArrivalHandlingResponse.Event(
                                        h.getCreatedAt(),
                                        h.getChangedBy() == null
                                                ? "Hệ thống"
                                                : h.getChangedBy().getFullName(),
                                        h.getReason()))
                                .toList()
                        : List.of(),
                refunds.stream()
                        .map(r -> new ArrivalHandlingResponse.Refund(
                                r.getId(),
                                r.getAmount().longValueExact(),
                                r.getStatus(),
                                r.getStatus() == PaymentRefundStatus.PENDING && r.getRefundMethod() == null))
                        .toList(),
                staffCancellation,
                booking.getStayRevision(),
                booking.getOrder() == null ? null : booking.getOrder().getOrderCode(),
                booking.getOrder() == null
                        ? 1
                        : booking.getOrder().getBookings().size(),
                applied,
                available,
                includeHistory
                        ? ArrivalProgress.of(booking, pendingChange == null ? null : pendingChange.getId(), policy, now)
                        : null,
                booking.isCancellationReviewRequired(),
                booking.getPolicyFeeTotal(),
                booking.getBalanceDueAt(),
                includeHistory ? orderRooms(booking) : List.of());
    }

    private List<ArrivalHandlingResponse.OrderRoom> orderRooms(Booking anchor) {
        var members =
                anchor.getOrder() == null ? List.of(anchor) : anchor.getOrder().getBookings();
        return members.stream()
                .sorted(Comparator.comparing(Booking::getBookingCode))
                .map(b -> new ArrivalHandlingResponse.OrderRoom(
                        b.getBookingCode(),
                        b.getOrderRoomNumber(),
                        b.getRoomType().getName(),
                        b.getStatus(),
                        b.getExpectedArrivalAt(),
                        policy.receptionDeadline(b),
                        policy.departureDeadline(b.getCheckOutDate()),
                        b.getArrivalHoldUntil(),
                        b.getArrivalHoldRequestedUntil(),
                        b.getStatus() == BookingStatus.CONFIRMED && b.getCheckedInAt() == null))
                .toList();
    }

    private List<PaymentRefund> relevantRefunds(Booking booking, Payment payment) {
        return payment.getRefunds().stream()
                .filter(r -> booking.getId().equals(r.getSourceBookingId())
                        || (payment.getOrder() == null && r.getSourceBookingId() == null))
                .toList();
    }

    private long received(Payment payment) {
        return payment.getTransactions().stream()
                .filter(t -> t.getType() == PaymentTransactionType.PAYMENT
                        && t.getStatus() == PaymentTransactionStatus.SUCCESS)
                .mapToLong(PaymentTransaction::getAmount)
                .sum();
    }

    private boolean reserved(PaymentRefund refund) {
        return refund.getStatus() != PaymentRefundStatus.CANCELLED && refund.getStatus() != PaymentRefundStatus.FAILED;
    }

    private long appliedTo(Booking booking, Payment payment) {
        return payment.getOrder() == null
                ? received(payment)
                : payment.getAllocations().stream()
                        .filter(a -> a.getBooking().getId().equals(booking.getId()))
                        .mapToLong(PaymentAllocation::getAppliedAmount)
                        .sum();
    }

    private long availableFor(Booking booking, Payment payment) {
        long totalAvailable = received(payment)
                - payment.getRefunds().stream()
                        .filter(this::reserved)
                        .mapToLong(r -> r.getAmount().longValueExact())
                        .sum();
        long roomReserved = relevantRefunds(booking, payment).stream()
                .filter(this::reserved)
                .mapToLong(r -> r.getAmount().longValueExact())
                .sum();
        return Math.max(0, Math.min(totalAvailable, appliedTo(booking, payment) - roomReserved));
    }

    private void ensureConfirmed(Booking booking) {
        if (booking.getStatus() != BookingStatus.CONFIRMED || booking.getCheckedInAt() != null) {
            throw StayModificationException.conflict("ARRIVAL_NOT_CONFIRMED", "Booking không còn chờ nhận phòng.");
        }
    }

    private User actor() {
        return currentUser
                .currentUserId()
                .flatMap(users::findById)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy nhân viên thực hiện."));
    }

    private boolean isManager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
    }

    private void ensureOwner(Booking booking, String token) {
        UUID userId = currentUser.currentUserId().orElse(null);
        boolean owner = userId != null
                && booking.getBookedBy() != null
                && userId.equals(booking.getBookedBy().getId());
        String hash = booking.getOrder() == null
                ? booking.getAccessTokenHash()
                : booking.getOrder().getAccessTokenHash();
        if (!owner && !tokens.matches(token, hash)) throw new BookingNotFoundException("Không tìm thấy booking");
    }
}
