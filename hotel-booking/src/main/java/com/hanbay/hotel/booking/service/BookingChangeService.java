package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.request.BookingChangeCommand;
import com.hanbay.hotel.booking.dto.response.BookingChangeResponse;
import com.hanbay.hotel.booking.dto.response.BookingChangeResponse.*;
import com.hanbay.hotel.booking.exception.*;
import com.hanbay.hotel.booking.policy.*;
import com.hanbay.hotel.booking.repository.*;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.RoomAvailabilityCalculator;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingChangeService {
    private final BookingChangeRequestRepository requests;
    private final BookingRepository bookings;
    private final RoomTypeRepository types;
    private final BookingOrderPaymentService orders;
    private final BookingSettlementLedger ledger;
    private final BookingAccessTokenService tokens;
    private final BookingCancellationPolicy cancellation;
    private final HotelStayPolicy stay;
    private final BookingRescheduleCalculator reschedulePricing;
    private final RoomAvailabilityCalculator availability;
    private final CurrentUserProvider currentUser;
    private final UserRepository users;
    private final Clock clock;

    public Quote cancellationQuote(String code) {
        List<Booking> scope = lockScope(code);
        ensureConfirmed(scope);
        var anchor = scope.stream()
                .filter(b -> b.getBookingCode().equals(code))
                .findFirst()
                .orElseThrow();
        var preview = BookingChangeRequest.builder()
                .id(UUID.randomUUID())
                .booking(anchor)
                .scopeKey(BookingChangeRequest.scopeOf(anchor))
                .type(BookingChangeRequest.Type.CANCELLATION)
                .reason("Tra cứu điều kiện hủy, chưa xác nhận hủy")
                .requestedBy(actor())
                .requestedAt(clock.instant())
                .build();
        return quote(preview, scope, ledger.lockPayments(scope));
    }

    public BookingChangeResponse create(String code, BookingChangeCommand.Create command) {
        return createInternal(code, command, actor());
    }

    public BookingChangeResponse customerCreate(String code, String token, BookingChangeCommand.Create command) {
        ensureOwner(lockScope(code).get(0), token);
        if (command.type() != BookingChangeRequest.Type.RESCHEDULE) throw invalid("Dùng chức năng xác nhận hủy riêng.");
        BookingChangeResponse result = createInternal(
                code,
                command,
                currentUser.currentUserId().flatMap(users::findById).orElse(null));
        var request = requests.findById(result.id()).orElseThrow();
        request.recordSnapshot(Map.of("customerInitiated", true));
        return response(request);
    }

    public List<BookingChangeResponse> customerHistory(String code, String token) {
        ensureOwner(lockScope(code).get(0), token);
        return history(code);
    }

    public Quote customerQuote(String code, String token, UUID id) {
        var scope = lockScope(code);
        ensureOwner(scope.get(0), token);
        var request = requests.findByIdForUpdate(id).orElseThrow(() -> invalid("Không tìm thấy yêu cầu."));
        if (!request.getScopeKey().equals(BookingChangeRequest.scopeOf(scope.get(0))))
            throw new BookingNotFoundException("Không tìm thấy yêu cầu.");
        return quote(request, scope, ledger.lockPayments(scope));
    }

    public BookingChangeResponse customerConsent(String code, String token, UUID id, String quoteToken) {
        Quote quote = customerQuote(code, token, id);
        if (!quote.canApprove() || !quote.quoteToken().equals(quoteToken))
            throw conflict("CHANGE_QUOTE_CHANGED", "Báo giá đã thay đổi; vui lòng xem lại.");
        var request = requests.findByIdForUpdate(id).orElseThrow();
        request.recordConsent(quoteToken, clock.instant());
        return response(request);
    }

    private void ensureOwner(Booking b, String token) {
        UUID userId = currentUser.currentUserId().orElse(null);
        boolean owner = userId != null
                && b.getBookedBy() != null
                && userId.equals(b.getBookedBy().getId());
        String hash =
                b.getOrder() == null ? b.getAccessTokenHash() : b.getOrder().getAccessTokenHash();
        if (!owner && !tokens.matches(token, hash)) throw new BookingNotFoundException("Không tìm thấy booking.");
    }

    private BookingChangeResponse createInternal(String code, BookingChangeCommand.Create command, User actor) {
        List<Booking> scope = lockScope(code);
        if (command.type() == BookingChangeRequest.Type.CANCELLATION
                && scope.stream().allMatch(b -> b.getStatus() == BookingStatus.CANCELLED)) {
            var prior =
                    requests
                            .findAllByScopeKeyOrderByRequestedAtDesc(BookingChangeRequest.scopeOf(scope.get(0)))
                            .stream()
                            .filter(q -> q.getType() == command.type()
                                    && q.getReason().equals(command.reason().trim()))
                            .findFirst()
                            .orElseThrow(() -> invalid("Booking đã hủy."));
            return response(prior);
        }
        ensureConfirmed(scope);
        if (command.type() == BookingChangeRequest.Type.RESCHEDULE) {
            validateDates(command.checkInDate(), command.checkOutDate());
            if (scope.stream()
                    .allMatch(b -> b.getCheckInDate().equals(command.checkInDate())
                            && b.getCheckOutDate().equals(command.checkOutDate()))) {
                throw invalid("Kỳ ở mới phải khác kỳ ở hiện tại.");
            }
        } else if (command.checkInDate() != null || command.checkOutDate() != null)
            throw invalid("Yêu cầu hủy không có ngày lưu trú mới.");
        Booking anchor = scope.stream()
                .filter(b -> b.getBookingCode().equals(code))
                .findFirst()
                .orElseThrow();
        String scopeKey = BookingChangeRequest.scopeOf(anchor);
        var existing = requests.findByScopeKeyAndStatus(scopeKey, BookingChangeRequest.Status.PENDING)
                .orElse(null);
        if (existing != null
                && command.type() == BookingChangeRequest.Type.CANCELLATION
                && existing.getType() == BookingChangeRequest.Type.RESCHEDULE) {
            existing.endAfterCancellation(actor, clock.instant());
            requests.flush();
            existing = null;
        }
        if (existing != null) {
            if (existing.getType() == command.type()
                    && Objects.equals(existing.getRequestedCheckIn(), command.checkInDate())
                    && Objects.equals(existing.getRequestedCheckOut(), command.checkOutDate())
                    && existing.getReason().equals(command.reason().trim())) return response(existing);
            throw conflict("CHANGE_REQUEST_PENDING", "Đã có yêu cầu chờ quản lý xử lý cho booking/đơn này.");
        }
        var request = requests.saveAndFlush(BookingChangeRequest.builder()
                .booking(anchor)
                .scopeKey(scopeKey)
                .type(command.type())
                .requestedCheckIn(command.checkInDate())
                .requestedCheckOut(command.checkOutDate())
                .reason(command.reason().trim())
                .requestedBy(actor)
                .requestedAt(clock.instant())
                .build());
        scope.forEach(b -> b.recordStaffNote(
                actor,
                "Khách yêu cầu "
                        + (command.type() == BookingChangeRequest.Type.CANCELLATION ? "hủy booking" : "đổi lịch")
                        + (command.type() == BookingChangeRequest.Type.CANCELLATION
                                ? "; khách xác nhận hủy; mã "
                                : "; chờ quản lý duyệt; mã ")
                        + request.getId() + ": "
                        + command.reason().trim()));
        if (command.contactChannel() != null && actor != null)
            anchor.recordArrivalContact(
                    actor,
                    command.contactChannel(),
                    command.type() == BookingChangeRequest.Type.CANCELLATION
                            ? ArrivalContact.Outcome.CANCELLATION
                            : ArrivalContact.Outcome.RESCHEDULE,
                    command.reason().trim(),
                    request.getRequestedAt());
        if (command.type() == BookingChangeRequest.Type.CANCELLATION
                && scope.stream().allMatch(UnifiedBookingPolicy::applies)) {
            // Confirmation is the cancellation event. Settlement does not hold inventory.
            applyCancellation(request, scope, ledger.lockPayments(scope), actor);
        } else if (command.type() == BookingChangeRequest.Type.CANCELLATION) {
            Map<String, Object> caps = new LinkedHashMap<>();
            for (Booking b : scope) {
                var contract = cancellation.evaluate(b, PaymentStatus.SUCCESS, request.getRequestedAt());
                caps.put(b.getBookingCode(), contract.allowed() ? contract.feeAmount() : b.getTotalAmount());
                caps.put(b.getBookingCode() + ":known", contract.allowed());
                b.cancelForSettlementReview(actor, request.getRequestedAt(), request.getReason());
            }
            request.recordSnapshot(caps);
            if (anchor.getOrder() != null) anchor.getOrder().completeFrontDeskCancellation(clock.instant());
        }
        return response(request);
    }

    private void applyCancellation(
            BookingChangeRequest request, List<Booking> scope, List<Payment> payments, User actor) {
        long refund = 0, feeTotal = 0, outstanding = 0;
        Instant now = clock.instant();
        if (scope.stream()
                .anyMatch(b ->
                        payments.stream().mapToLong(p -> ledger.applied(b, p)).sum() < b.getPaidAmount())) {
            Map<String, Object> caps = new LinkedHashMap<>();
            for (Booking b : scope) {
                caps.put(
                        b.getBookingCode(),
                        UnifiedBookingPolicy.target(
                                b,
                                request.getRequestedAt(),
                                stay.getProperties().zoneId(),
                                stay.getProperties().getCheckInTime()));
                caps.put(b.getBookingCode() + ":known", true);
                b.cancelForSettlementReview(actor, request.getRequestedAt(), request.getReason());
            }
            request.recordSnapshot(caps);
            if (scope.get(0).getOrder() != null) scope.get(0).getOrder().completeFrontDeskCancellation(now);
            return;
        }
        for (Booking b : scope) {
            long fee = UnifiedBookingPolicy.target(
                    b,
                    request.getRequestedAt(),
                    stay.getProperties().zoneId(),
                    stay.getProperties().getCheckInTime());
            long available = ledger.available(b, payments);
            long applied =
                    payments.stream().mapToLong(p -> ledger.applied(b, p)).sum();
            if (applied < b.getPaidAmount())
                throw conflict("SETTLEMENT_MISMATCH", "Cần đối soát tiền đã trả trước khi quyết toán.");
            refund += ledger.refundExcess(b, payments, fee, "chg:" + request.getId(), request.getReason(), now, null);
            outstanding += Math.max(fee - available, 0);
            feeTotal += fee;
            b.recordPolicyFee(fee, "CANCELLATION", actor, now, request.getReason());
            b.cancelAtFrontDesk(actor, now, fee, Math.min(fee, available), request.getReason());
        }
        if (scope.get(0).getOrder() != null) scope.get(0).getOrder().completeFrontDeskCancellation(now);
        request.decide(
                true,
                actor,
                now,
                "Khách xác nhận hủy theo chính sách đã chấp thuận",
                Map.of(
                        "feeAmount",
                        feeTotal,
                        "refundAmount",
                        refund,
                        "additionalAmount",
                        outstanding,
                        "policyVersion",
                        UnifiedBookingPolicy.VERSION));
        requests.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingChangeResponse> list(BookingChangeRequest.Status status, int page, int size) {
        PaginationValidator.validate(page, size, 100);
        Pageable paging = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "requestedAt"));
        return PageResponse.from((status == null ? requests.findAll(paging) : requests.findAllByStatus(status, paging))
                .map(this::response));
    }

    @Transactional(readOnly = true)
    public List<BookingChangeResponse> history(String code) {
        Booking booking = bookings.findByBookingCode(code)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy booking."));
        return requests.findAllByScopeKeyOrderByRequestedAtDesc(BookingChangeRequest.scopeOf(booking)).stream()
                .map(this::response)
                .toList();
    }

    public Quote quote(UUID id) {
        List<Booking> scope = lockScope(codeFor(id));
        var request = requests.findByIdForUpdate(id).orElseThrow(() -> invalid("Không tìm thấy yêu cầu."));
        return quote(request, scope, ledger.lockPayments(scope));
    }

    public BookingChangeResponse approve(UUID id, BookingChangeCommand.Approve command) {
        ensureManager();
        List<Booking> scope = lockScope(codeFor(id));
        var request = requests.findByIdForUpdate(id).orElseThrow(() -> invalid("Không tìm thấy yêu cầu."));
        if (request.getStatus() == BookingChangeRequest.Status.APPROVED) return response(request);
        if (request.getStatus() != BookingChangeRequest.Status.PENDING)
            throw conflict("CHANGE_REQUEST_CLOSED", "Yêu cầu đã được xử lý.");
        List<Payment> payments = ledger.lockPayments(scope);
        Quote quote = quote(request, scope, payments);
        if (!quote.canApprove()) throw conflict("CHANGE_NOT_ALLOWED", quote.blockReason());
        if (!quote.quoteToken().equals(command.quoteToken()))
            throw conflict(
                    "CHANGE_QUOTE_CHANGED", "Giá, tồn phòng hoặc thanh toán đã thay đổi. Xem lại trước khi duyệt.");
        User actor = actor();
        Instant now = clock.instant();
        long fee = 0;
        if (request.getType() == BookingChangeRequest.Type.CANCELLATION) {
            if (quote.requiresFeeDecision() && command.feeAmount() == null)
                throw invalid("Hủy sát giờ cần quản lý chọn rõ phí ngoại lệ và ghi lý do.");
            fee = command.feeAmount() == null ? quote.feeAmount() : command.feeAmount();
            long maxFee = quote.rooms().stream().mapToLong(Room::standardFee).sum();
            if (fee < 0 || fee > maxFee) throw invalid("Phí hủy phải từ 0 đến mức tối đa đang hiển thị.");
        } else {
            fee = command.feeAmount() == null ? quote.feeAmount() : command.feeAmount();
            if (fee < 0
                    || fee > quote.rooms().stream().mapToLong(Room::standardFee).sum())
                throw invalid("Chỉ được giảm/miễn phí đổi trong giới hạn đã công bố.");
            if (scope.stream().anyMatch(UnifiedBookingPolicy::applies)
                    || (request.getSnapshot() != null
                            && Boolean.TRUE.equals(request.getSnapshot().get("customerInitiated")))) {
                if (!Boolean.TRUE.equals(command.customerAccepted())
                        && !quote.quoteToken().equals(request.getConsentToken()))
                    throw invalid("Cần xác nhận khách đã chấp thuận đúng báo giá này.");
                if (!request.isDropServices()
                        && scope.stream().anyMatch(b -> !b.getAddOns().isEmpty())
                        && !Boolean.TRUE.equals(command.servicesConfirmed()))
                    throw invalid("Cần xác nhận lại dịch vụ chuyển được sang kỳ mới.");
                for (Booking b : scope) {
                    if (b.getChildCount() > 0) {
                        var ages = command.childAges() == null
                                ? null
                                : command.childAges().get(b.getBookingCode());
                        if (ages == null) throw invalid("Nhập lại tuổi trẻ em theo ngày nhận mới cho từng phòng.");
                        stay.validateChildAges(b.getChildCount(), ages);
                    }
                }
            }
        }
        Map<String, Long> fees = distributeFee(fee, quote.rooms());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("oldTotalAmount", quote.oldTotalAmount());
        snapshot.put("newTotalAmount", quote.newTotalAmount());
        snapshot.put("feeAmount", fee);
        snapshot.put("availableAmount", quote.availableAmount());
        snapshot.put("quoteToken", quote.quoteToken());
        snapshot.put("managerException", request.isManagerException());
        if (request.getType() == BookingChangeRequest.Type.RESCHEDULE) {
            snapshot.put("changeTermsVersion", BookingRescheduleCalculator.TERMS_VERSION);
            snapshot.put("changeTerms", quote.policyDescription());
        }
        snapshot.put(
                "customerAccepted",
                Boolean.TRUE.equals(command.customerAccepted())
                        || quote.quoteToken().equals(request.getConsentToken()));
        snapshot.put(
                "consentSource",
                quote.quoteToken().equals(request.getConsentToken()) ? "CUSTOMER" : "STAFF_ATTESTATION");
        snapshot.put("servicesConfirmed", Boolean.TRUE.equals(command.servicesConfirmed()));
        snapshot.put(
                "acceptedAt",
                quote.quoteToken().equals(request.getConsentToken()) && request.getConsentAt() != null
                        ? request.getConsentAt().toString()
                        : now.toString());
        List<Map<String, Object>> roomAudit = new ArrayList<>();
        long refund = 0, additional = 0;
        // Quotes and application both run under inventory, booking and payment locks.
        Map<String, BookingRescheduleCalculator.ProposedStay> newPrices = new HashMap<>();
        if (request.getType() == BookingChangeRequest.Type.RESCHEDULE) {
            for (Booking b : scope) {
                var price = reschedulePricing.calculate(
                        b, request.getRequestedCheckIn(), request.getRequestedCheckOut(), request.getRequestedAt());
                Room row = quote.rooms().stream()
                        .filter(r -> r.bookingCode().equals(b.getBookingCode()))
                        .findFirst()
                        .orElseThrow();
                if (price.roomTotal()
                                        + (request.isDropServices()
                                                ? 0
                                                : b.getAddOns().stream()
                                                        .mapToLong(a -> a.getTotalPrice())
                                                        .sum())
                                != row.newTotalAmount()
                        || !price.nights().stream()
                                .map(n -> new Night(n.getStayDate(), n.getPricePerNight()))
                                .toList()
                                .equals(row.nights()))
                    throw conflict("CHANGE_QUOTE_CHANGED", "Giá kỳ mới vừa thay đổi. Xem lại trước khi duyệt.");
                newPrices.put(b.getBookingCode(), price);
            }
        }
        for (Booking booking : scope) {
            Room row = quote.rooms().stream()
                    .filter(r -> r.bookingCode().equals(booking.getBookingCode()))
                    .findFirst()
                    .orElseThrow();
            long available = ledger.available(booking, payments);
            long retain = request.getType() == BookingChangeRequest.Type.CANCELLATION
                    ? fees.get(booking.getBookingCode())
                    : Math.addExact(row.newTotalAmount(), fees.get(booking.getBookingCode()));
            var audit = new LinkedHashMap<String, Object>();
            audit.put("bookingCode", booking.getBookingCode());
            audit.put("oldCheckIn", booking.getCheckInDate().toString());
            audit.put("oldCheckOut", booking.getCheckOutDate().toString());
            audit.put("oldTotal", booking.getTotalAmount());
            audit.put(
                    "oldServices",
                    booking.getAddOns().stream()
                            .map(a -> Map.of(
                                    "name",
                                    a.getAddOnService().getName(),
                                    "quantity",
                                    a.getQuantity(),
                                    "amount",
                                    a.getTotalPrice()))
                            .toList());
            audit.put("servicesRemoved", request.isDropServices());
            audit.put("newTotal", retain);
            audit.put(
                    "oldNights",
                    booking.getNights().stream()
                            .map(n -> Map.of("date", n.getStayDate().toString(), "price", n.getPricePerNight()))
                            .toList());
            long roomRefund = ledger.refundExcess(
                    booking,
                    payments,
                    retain,
                    "chg:" + request.getId(),
                    (request.getType() == BookingChangeRequest.Type.CANCELLATION
                                    ? "Hoàn khách báo hủy "
                                    : "Hoàn chênh lệch đổi lịch ")
                            + booking.getBookingCode(),
                    now,
                    null);
            refund += roomRefund;
            additional += Math.max(retain - available, 0);
            if (UnifiedBookingPolicy.applies(booking))
                booking.recordPolicyFee(
                        fees.get(booking.getBookingCode()),
                        request.getType().name(),
                        actor,
                        now,
                        command.note().trim());
            if (request.getType() == BookingChangeRequest.Type.CANCELLATION) {
                if (booking.isCancellationReviewRequired())
                    booking.settleCancelledBooking(
                            actor,
                            retain,
                            Math.min(available, retain),
                            command.note().trim());
                else
                    booking.cancelAtFrontDesk(
                            actor,
                            now,
                            retain,
                            Math.min(available, retain),
                            "Khách chủ động hủy; yêu cầu " + id + ": "
                                    + command.note().trim());
            } else {
                if (request.isDropServices()) booking.getAddOns().clear();
                booking.getNights().clear();
                bookings.flush(); // Delete old overlapping night keys before inserting repriced snapshots.
                booking.rescheduleConfirmed(
                        request.getRequestedCheckIn(),
                        request.getRequestedCheckOut(),
                        newPrices.get(booking.getBookingCode()).nights(),
                        retain,
                        Math.min(available, retain),
                        actor,
                        "Quản lý duyệt đổi lịch; yêu cầu " + id + ": "
                                + command.note().trim());
                if (UnifiedBookingPolicy.applies(booking) && booking.getChildCount() > 0)
                    booking.recordChildAges(command.childAges().get(booking.getBookingCode()));
                audit.put("newCheckIn", booking.getCheckInDate().toString());
                audit.put("newCheckOut", booking.getCheckOutDate().toString());
                audit.put(
                        "newNights",
                        booking.getNights().stream()
                                .map(n -> Map.of("date", n.getStayDate().toString(), "price", n.getPricePerNight()))
                                .toList());
            }
            audit.put("refundAmount", roomRefund);
            audit.put("creditedAmount", booking.getPaidAmount());
            roomAudit.add(audit);
        }
        BookingOrder order = scope.get(0).getOrder();
        if (order != null) {
            if (request.getType() == BookingChangeRequest.Type.CANCELLATION) order.completeFrontDeskCancellation(now);
            else order.rescheduleDates(request.getRequestedCheckIn(), request.getRequestedCheckOut());
        }
        snapshot.put("refundAmount", refund);
        snapshot.put("additionalAmount", additional);
        snapshot.put("rooms", roomAudit);
        if (request.getType() == BookingChangeRequest.Type.RESCHEDULE
                && scope.stream().anyMatch(UnifiedBookingPolicy::applies)) {
            if (additional > 0
                    && (command.balanceDueAt() == null
                            || !command.balanceDueAt().isAfter(now)))
                throw invalid("Cần ghi hạn thu chênh lệch đã thỏa thuận ở tương lai.");
            for (Booking b : scope) b.setBalanceDueAt(additional > 0 ? command.balanceDueAt() : null);
            if (command.balanceDueAt() != null)
                snapshot.put("balanceDueAt", command.balanceDueAt().toString());
        }
        request.decide(true, actor, now, command.note().trim(), snapshot);
        requests.flush();
        return response(request);
    }

    public Quote allowException(UUID id, String note) {
        ensureManager();
        List<Booking> scope = lockScope(codeFor(id));
        var request = requests.findByIdForUpdate(id).orElseThrow(() -> invalid("Không tìm thấy yêu cầu."));
        if (request.getStatus() != BookingChangeRequest.Status.PENDING
                || request.getType() != BookingChangeRequest.Type.RESCHEDULE)
            throw invalid("Chỉ xem xét ngoại lệ cho đề nghị đổi đang chờ.");
        request.allowManagerException(note);
        scope.forEach(b -> b.recordStaffNote(
                actor(), "Quản lý chấp thuận xét ngoại lệ đổi lịch (số đêm/số lần/90 ngày): " + note));
        return quote(request, scope, ledger.lockPayments(scope));
    }

    public Quote changeServices(UUID id, boolean drop, String note) {
        ensureManager();
        List<Booking> scope = lockScope(codeFor(id));
        var request = requests.findByIdForUpdate(id).orElseThrow(() -> invalid("Không tìm thấy yêu cầu."));
        if (request.getStatus() != BookingChangeRequest.Status.PENDING
                || request.getType() != BookingChangeRequest.Type.RESCHEDULE)
            throw invalid("Chỉ xác nhận lại dịch vụ cho đề nghị đổi đang chờ.");
        request.changeServices(drop, note);
        scope.forEach(b -> b.recordStaffNote(
                actor(), (drop ? "Bỏ toàn bộ dịch vụ chưa dùng khi đổi lịch: " : "Xác nhận chuyển dịch vụ: ") + note));
        return quote(request, scope, ledger.lockPayments(scope));
    }

    public BookingChangeResponse reject(UUID id, BookingChangeCommand.Reject command) {
        ensureManager();
        lockScope(codeFor(id));
        var request = requests.findByIdForUpdate(id).orElseThrow(() -> invalid("Không tìm thấy yêu cầu."));
        if (request.getStatus() == BookingChangeRequest.Status.REJECTED) return response(request);
        if (request.getStatus() != BookingChangeRequest.Status.PENDING)
            throw conflict("CHANGE_REQUEST_CLOSED", "Yêu cầu đã được xử lý.");
        if (request.getType() == BookingChangeRequest.Type.CANCELLATION
                && request.getBooking().getStatus() == BookingStatus.CANCELLED)
            throw invalid(
                    "Khách đã xác nhận hủy; cần quyết định phần quyết toán, không khôi phục booking hoặc từ chối việc hủy.");
        request.decide(false, actor(), clock.instant(), command.note().trim(), Map.of());
        return response(request);
    }

    private Quote quote(BookingChangeRequest request, List<Booking> scope, List<Payment> payments) {
        if (request.getStatus() != BookingChangeRequest.Status.PENDING)
            return new Quote(
                    response(request),
                    false,
                    "Yêu cầu đã được xử lý.",
                    false,
                    "Xem kết quả quyết định đã lưu.",
                    "",
                    0,
                    0,
                    0,
                    null,
                    null,
                    0,
                    List.of(),
                    stay.getProperties().getZone(),
                    clock.instant(),
                    false);
        String blocked = scope.stream()
                        .anyMatch(b -> (b.getStatus() != BookingStatus.CONFIRMED && !b.isCancellationReviewRequired())
                                || b.getCheckedInAt() != null)
                ? "Có phòng đã nhận hoặc đã kết thúc; yêu cầu này không còn đủ điều kiện duyệt."
                : null;
        boolean exception = false;
        String description = request.getType() == BookingChangeRequest.Type.RESCHEDULE
                ? BookingRescheduleCalculator.TERMS
                : "Mức hoàn theo chính sách hủy tại thời điểm khách gửi yêu cầu.";
        if (request.getType() == BookingChangeRequest.Type.RESCHEDULE) {
            try {
                validateDates(request.getRequestedCheckIn(), request.getRequestedCheckOut());
            } catch (BookingValidationException ex) {
                blocked = ex.getMessage();
            }
        }
        boolean pricingAvailable = blocked == null;
        List<Room> rows = new ArrayList<>();
        for (Booking b : scope) {
            long available = ledger.available(b, payments);
            long applied =
                    payments.stream().mapToLong(p -> ledger.applied(b, p)).sum();
            if (applied < b.getPaidAmount()) {
                blocked = "Tiền đã trả chưa khớp sổ giao dịch; cần đối soát trước khi duyệt.";
                pricingAvailable = false;
            }
            long newTotal = b.getTotalAmount(), standardFee = 0;
            if (UnifiedBookingPolicy.applies(b)) {
                standardFee = UnifiedBookingPolicy.target(
                        b,
                        request.getRequestedAt(),
                        stay.getProperties().zoneId(),
                        stay.getProperties().getCheckInTime());
                if (request.getType() == BookingChangeRequest.Type.RESCHEDULE) {
                    if (blocked == null && b.getStayRevision() >= 1 && !request.isManagerException())
                        blocked = "Đã dùng một lần đổi; cần xử lý ngoại lệ riêng với quản lý.";
                    if (blocked == null
                            && !request.isManagerException()
                            && (request.getRequestedCheckIn().isBefore(b.getOriginalCheckInDate())
                                    || request.getRequestedCheckIn()
                                            .isAfter(b.getOriginalCheckInDate().plusDays(90))))
                        blocked = "Ngày nhận mới phải trong 90 ngày từ ngày nhận gốc.";
                    if (blocked == null
                            && !request.isManagerException()
                            && ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate())
                                    != ChronoUnit.DAYS.between(
                                            request.getRequestedCheckIn(), request.getRequestedCheckOut()))
                        blocked =
                                "Thay đổi số đêm cần quản lý xét ngoại lệ và khách chấp thuận báo giá trước khi áp dụng.";
                }
            }
            List<Night> nights = List.of();
            if (request.getType() == BookingChangeRequest.Type.CANCELLATION) {
                var decision = cancellation.evaluate(b, PaymentStatus.SUCCESS, request.getRequestedAt());
                if (b.isCancellationReviewRequired()
                        && request.getSnapshot() != null
                        && Boolean.TRUE.equals(request.getSnapshot().get(b.getBookingCode() + ":known"))) {
                    standardFee = ((Number) request.getSnapshot().get(b.getBookingCode())).longValue();
                } else if (decision.allowed()) standardFee = decision.feeAmount();
                else {
                    exception = true;
                    standardFee =
                            b.isCancellationReviewRequired() ? b.getCancellationOriginalTotal() : b.getTotalAmount();
                    description =
                            decision.message() + ". Quản lý phải quyết định phí ngoại lệ; không tự áp phí no-show.";
                }
                newTotal = 0;
            } else if (pricingAvailable) {
                try {
                    var price = reschedulePricing.calculate(
                            b, request.getRequestedCheckIn(), request.getRequestedCheckOut(), request.getRequestedAt());
                    long addOns = request.isDropServices()
                            ? 0
                            : b.getAddOns().stream()
                                    .mapToLong(a -> a.getTotalPrice())
                                    .sum();
                    newTotal = Math.addExact(price.roomTotal(), addOns);
                    standardFee = price.policyFee();
                    nights = price.nights().stream()
                            .map(n -> new Night(n.getStayDate(), n.getPricePerNight()))
                            .toList();
                } catch (BookingValidationException ex) {
                    pricingAvailable = false;
                    blocked = ex.getMessage();
                }
            }
            rows.add(new Room(
                    b.getBookingCode(),
                    b.getRoomType().getName(),
                    b.getCheckInDate(),
                    b.getCheckOutDate(),
                    request.getRequestedCheckIn(),
                    request.getRequestedCheckOut(),
                    b.getTotalAmount(),
                    newTotal,
                    available,
                    standardFee,
                    nights,
                    b.getOriginalFirstNightAmount(),
                    b.getPolicyFeeTotal(),
                    b.getChildCount(),
                    request.isDropServices()
                            ? List.of()
                            : b.getAddOns().stream()
                                    .map(a -> a.getAddOnService().getName() + " × " + a.getQuantity())
                                    .toList(),
                    feeTiming(b, request.getRequestedAt())));
        }
        if (request.getType() == BookingChangeRequest.Type.RESCHEDULE && pricingAvailable) {
            Map<UUID, Long> quantities = scope.stream()
                    .collect(Collectors.groupingBy(b -> b.getRoomType().getId(), Collectors.counting()));
            Map<UUID, Long> counts = scope.get(0).getOrder() == null
                    ? Map.of(
                            scope.get(0).getRoomType().getId(),
                            availability.countAvailableRoomsExcludingBooking(
                                    scope.get(0).getRoomType().getId(),
                                    request.getRequestedCheckIn(),
                                    request.getRequestedCheckOut(),
                                    scope.get(0).getId()))
                    : availability.countAvailableRoomsExcludingOrder(
                            quantities.keySet(),
                            request.getRequestedCheckIn(),
                            request.getRequestedCheckOut(),
                            scope.get(0).getOrder().getId());
            if (quantities.entrySet().stream().anyMatch(e -> counts.getOrDefault(e.getKey(), 0L) < e.getValue()))
                blocked = "Kỳ ở mới không đủ phòng cho toàn bộ yêu cầu.";
        }
        Long fee = exception || !pricingAvailable
                ? null
                : rows.stream().mapToLong(Room::standardFee).sum();
        Long refund = exception || !pricingAvailable
                ? null
                : rows.stream()
                        .mapToLong(r -> Math.max(
                                r.availableAmount()
                                        - (request.getType() == BookingChangeRequest.Type.CANCELLATION
                                                ? r.standardFee()
                                                : r.newTotalAmount() + r.standardFee()),
                                0))
                        .sum();
        long additional = pricingAvailable && !exception && request.getType() == BookingChangeRequest.Type.RESCHEDULE
                ? rows.stream()
                        .mapToLong(r -> Math.max(r.newTotalAmount() + r.standardFee() - r.availableAmount(), 0))
                        .sum()
                : 0;
        String token = tokens.hash(request.getId() + ":" + request.isManagerException() + ":" + rows + ":" + exception
                + ":" + blocked + ":" + pricingAvailable + ":" + description);
        return new Quote(
                response(request),
                blocked == null,
                blocked,
                exception,
                description,
                token,
                rows.stream().mapToLong(Room::oldTotalAmount).sum(),
                rows.stream().mapToLong(Room::newTotalAmount).sum(),
                rows.stream().mapToLong(Room::availableAmount).sum(),
                fee,
                refund,
                additional,
                rows,
                stay.getProperties().getZone(),
                clock.instant(),
                pricingAvailable);
    }

    private FeeTiming feeTiming(Booking booking, Instant receivedAt) {
        if (!UnifiedBookingPolicy.applies(booking)) return null;
        Instant checkIn = booking.getCheckInDate()
                .atTime(stay.getProperties().getCheckInTime())
                .atZone(stay.getProperties().zoneId())
                .toInstant();
        return new FeeTiming(
                checkIn,
                checkIn.minus(Duration.ofHours(48)),
                checkIn.minus(Duration.ofHours(24)),
                UnifiedBookingPolicy.rate(checkIn, receivedAt));
    }

    private Map<String, Long> distributeFee(long fee, List<Room> rooms) {
        long weight = rooms.stream().mapToLong(Room::standardFee).sum(), allocated = 0;
        Map<String, Long> result = new LinkedHashMap<>();
        for (Room room : rooms) {
            long part = weight == 0
                    ? 0
                    : BigDecimal.valueOf(fee)
                            .multiply(BigDecimal.valueOf(room.standardFee()))
                            .divide(BigDecimal.valueOf(weight), 0, RoundingMode.DOWN)
                            .longValueExact();
            result.put(room.bookingCode(), part);
            allocated += part;
        }
        long remaining = fee - allocated;
        for (Room room : rooms)
            if (remaining > 0 && result.get(room.bookingCode()) < room.standardFee()) {
                result.compute(room.bookingCode(), (k, v) -> v + 1);
                remaining--;
            }
        if (remaining != 0) throw invalid("Không phân bổ được phí hủy.");
        return result;
    }

    private List<Booking> lockScope(String code) {
        var orderId = bookings.findOrderIdForArrival(code);
        if (orderId.isPresent())
            return orders.lockOrder(orderId.get()).getBookings().stream()
                    .sorted(Comparator.comparing(Booking::getId))
                    .toList();
        types.findByIdForUpdate(bookings.findRoomTypeIdByBookingCode(code)
                        .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy booking.")))
                .orElseThrow();
        return List.of(bookings.findByBookingCodeForUpdate(code)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy booking.")));
    }

    private String codeFor(UUID id) {
        return requests.findBookingCodeById(id)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy yêu cầu."));
    }

    private void validateDates(LocalDate arrival, LocalDate departure) {
        if (arrival == null
                || departure == null
                || !departure.isAfter(arrival)
                || arrival.isBefore(stay.today())
                || stay.bookingClosed(arrival)
                || ChronoUnit.DAYS.between(arrival, departure) > 365)
            throw invalid("Kỳ ở mới phải từ hôm nay, trước hạn đặt và từ 1 đến 365 đêm.");
    }

    private void ensureConfirmed(List<Booking> scope) {
        if (scope.stream().anyMatch(b -> b.getStatus() != BookingStatus.CONFIRMED || b.getCheckedInAt() != null))
            throw conflict(
                    "CHANGE_NOT_CONFIRMED",
                    "Chỉ xử lý khi tất cả phòng trong booking/đơn đã xác nhận và chưa nhận phòng.");
    }

    private User actor() {
        return currentUser
                .currentUserId()
                .flatMap(users::findById)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy nhân viên."));
    }

    private void ensureManager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("ROLE_MANAGER")))
            throw new AccessDeniedException("Chỉ quản lý được duyệt hoặc từ chối yêu cầu.");
    }

    private BookingValidationException invalid(String message) {
        return new BookingValidationException(message);
    }

    private StayModificationException conflict(String code, String message) {
        return StayModificationException.conflict(code, message);
    }

    private BookingChangeResponse response(BookingChangeRequest r) {
        return new BookingChangeResponse(
                r.getId(),
                r.getBooking().getBookingCode(),
                r.getBooking().getOrder() == null
                        ? null
                        : r.getBooking().getOrder().getOrderCode(),
                r.getType(),
                r.getStatus(),
                r.getReason(),
                r.getRequestedBy() == null
                        ? "Khách qua mã truy cập"
                        : r.getRequestedBy().getFullName(),
                r.getRequestedAt(),
                r.getRequestedCheckIn(),
                r.getRequestedCheckOut(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getFullName(),
                r.getDecidedAt(),
                r.getDecisionNote(),
                r.getSnapshot(),
                r.getConsentAt());
    }
}
