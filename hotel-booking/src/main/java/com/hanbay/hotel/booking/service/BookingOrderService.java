package com.hanbay.hotel.booking.service;

import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.dto.request.*;
import com.hanbay.hotel.booking.dto.response.*;
import com.hanbay.hotel.booking.exception.*;
import com.hanbay.hotel.booking.policy.BookingCancellationPolicy;
import com.hanbay.hotel.booking.policy.CancellationDecision;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.repository.BookingOrderRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.common.validation.PaginationValidator;
import com.hanbay.hotel.payment.domain.*;
import com.hanbay.hotel.payment.dto.response.CreatePaymentRequestResponse;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.payment.service.SePayService;
import com.hanbay.hotel.room.exception.RoomTypeNotFoundException;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.RoomAvailabilityCalculator;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingOrderService {
    private final BookingOrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityCalculator availability;
    private final BookingService bookingService;
    private final BookingOrderPaymentService orderPayments;
    private final BookingAccessTokenService tokens;
    private final PaymentRepository paymentRepository;
    private final SePayService sePayService;
    private final CurrentUserProvider currentUser;
    private final UserRepository userRepository;
    private final BookingCancellationPolicy cancellationPolicy;
    private final HotelStayPolicy stayPolicy;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public BookingOrderResponse create(CreateBookingOrderRequest request, String idempotencyKey, String accessToken) {
        String fingerprint = tokens.hash(objectMapper.writeValueAsString(request));
        orderRepository.lockCheckout("booking-order:" + idempotencyKey);
        BookingOrderRepository.CheckoutReference previous =
                orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (previous != null) {
            if (!tokens.matches(accessToken, previous.getAccessTokenHash())) throw notFound();
            if (!previous.getRequestHash().equals(fingerprint)) {
                throw new BookingConflictException("Yêu cầu đã được sử dụng cho đơn khác. Vui lòng tạo yêu cầu mới.");
            }
            return get(previous.getId(), accessToken);
        }
        if (request.checkInDate().isBefore(stayPolicy.today())
                || !request.checkOutDate().isAfter(request.checkInDate())
                || stayPolicy.bookingClosed(request.checkInDate())) {
            throw new InvalidBookingDateException("Ngày nhận/trả phòng không hợp lệ hoặc đã qua giờ nhận đặt phòng.");
        }

        Map<UUID, Long> quantities = request.rooms().stream()
                .collect(Collectors.groupingBy(
                        CreateBookingOrderRequest.RoomSelection::roomTypeId, Collectors.counting()));
        // Acquire every type lock in the same order before checking aggregate demand, including duplicate types.
        quantities.keySet().stream().sorted().forEach(id -> roomTypeRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new RoomTypeNotFoundException("Không tìm thấy loại phòng")));
        Map<UUID, Long> available =
                availability.countAvailableRooms(quantities.keySet(), request.checkInDate(), request.checkOutDate());
        quantities.forEach((id, quantity) -> {
            if (available.getOrDefault(id, 0L) < quantity) {
                throw new RoomNotAvailableException(
                        "Không đủ số phòng đã chọn. Vui lòng giảm số lượng hoặc chọn loại khác.");
            }
        });

        UUID userId = currentUser.currentUserId().orElse(null);
        var guest = request.guestInfo();
        // Use the same factory deadline as individual bookings, then share the first room's deadline for all rooms.
        BookingOrder order = null;
        for (var room : request.rooms()) {
            var created = bookingService.createPendingBooking(new CreatePendingBookingRequest(
                    room.roomTypeId(),
                    request.checkInDate(),
                    request.checkOutDate(),
                    room.adults(),
                    room.children(),
                    guest,
                    room.addOns()));
            Booking booking = bookingRepository.findById(created.bookingId()).orElseThrow();
            if (order == null) {
                order = orderRepository.save(BookingOrder.builder()
                        .orderCode("ORD-"
                                + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                        .bookedBy(userId == null ? null : userRepository.getReferenceById(userId))
                        .contactName(guest.fullName())
                        .contactEmail(guest.email())
                        .contactPhone(guest.phone())
                        .checkInDate(request.checkInDate())
                        .checkOutDate(request.checkOutDate())
                        .accessTokenHash(tokens.hash(accessToken))
                        .idempotencyKey(idempotencyKey)
                        .requestHash(fingerprint)
                        .expiresAt(booking.getExpiredAt())
                        .build());
            }
            order.addBooking(booking);
            bookingRepository.flush();
        }
        if (order.getTotalAmount() == 0) order.confirm(clock.instant());
        orderRepository.flush();
        return toResponse(order);
    }

    public BookingOrderResponse get(UUID orderId, String accessToken) {
        BookingOrder order = orderPayments.lockOrder(orderId);
        ensureAccess(order, accessToken);
        orderPayments.expire(order, clock.instant());
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingOrderResponse> history(int page, int size) {
        PaginationValidator.validate(page, size, 100);
        UUID userId = currentUser.currentUserId().orElseThrow(BookingOrderService::notFound);
        return PageResponse.from(orderRepository
                .findAllByBookedById(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse));
    }

    public BookingOrderResponse createPayment(UUID orderId, String accessToken) {
        BookingOrder order = orderPayments.lockOrder(orderId);
        ensureAccess(order, accessToken);
        Instant now = clock.instant();
        if (!order.isActive(now)) throw new BookingValidationException("Đơn không còn trong thời gian thanh toán.");
        List<Payment> payments = paymentRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId);
        if (payments.stream().anyMatch(p -> p.isPendingAndActive(now))) return toResponse(order);
        if (!payments.isEmpty()) throw new BookingValidationException("Yêu cầu thanh toán không còn hiệu lực.");
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneOffset.UTC)
                .format(now);
        Payment payment = Payment.builder()
                .order(order)
                .booking(order.getBookings().get(0))
                .paymentCode("PAY-" + timestamp + "-"
                        + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                .amount(order.getTotalAmount())
                .currency("VND")
                .expiresAt(order.getExpiresAt())
                .idempotencyKey("order-payment:" + orderId)
                .build();
        order.getBookings().forEach(payment::allocateTo);
        paymentRepository.saveAndFlush(payment);
        return toResponse(order);
    }

    public CancellationEligibilityResponse cancellationEligibility(UUID orderId, String accessToken) {
        BookingOrder order = orderPayments.lockOrder(orderId);
        ensureAccess(order, accessToken);
        return eligibility(order);
    }

    public BookingOrderResponse cancel(UUID orderId, String accessToken, CancelBookingRequest request) {
        BookingOrder order = orderPayments.lockOrder(orderId);
        ensureAccess(order, accessToken);
        if (order.getStatus() == BookingOrderStatus.CANCELLED) return toResponse(order);
        CancellationEligibilityResponse decision = eligibility(order);
        if (!decision.canCancel()) {
            throw new BookingCancellationNotAllowedException("ORDER_CANCELLATION_NOT_ALLOWED", decision.message());
        }
        if (decision.refundAmount() > 0 && request.refundInformation() == null) {
            throw new BookingValidationException("Vui lòng cung cấp tài khoản nhận hoàn tiền.");
        }
        Instant now = clock.instant();
        boolean pending = order.getStatus() == BookingOrderStatus.PENDING;
        // A token also authorizes anonymous cancellation; no user profile is invented for the audit history.
        order.cancel(order.getBookedBy(), request.normalizedReason(), now);
        if (pending) {
            orderPayments.closePendingPayments(
                    order, now, request.normalizedReason(), "order-cancellation:", request.refundInformation());
        } else {
            Payment payment = paymentRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .findFirst()
                    .orElse(null);
            if (payment != null)
                orderPayments.refund(
                        payment,
                        decision.refundAmount(),
                        request.normalizedReason(),
                        "order-cancellation:" + orderId,
                        now,
                        request.refundInformation());
        }
        return toResponse(order);
    }

    public void expire(UUID id) {
        orderPayments.expire(orderPayments.lockOrder(id), clock.instant());
    }

    public BookingOrderResponse supplyRefundInformation(
            UUID orderId, String token, com.hanbay.hotel.payment.dto.request.RefundInformationRequest information) {
        BookingOrder order = orderPayments.lockOrder(orderId);
        ensureAccess(order, token);
        for (Payment payment : paymentRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId)) {
            paymentRepository
                    .findByPaymentCodeForUpdate(payment.getPaymentCode())
                    .orElseThrow();
            payment.getRefunds().stream()
                    .filter(r -> r.getStatus() == PaymentRefundStatus.PENDING && r.getRefundMethod() == null)
                    .forEach(r -> r.supplyDestination(
                            information.method(),
                            information.normalizedBankName(),
                            information.normalizedAccountHolderName(),
                            information.normalizedAccountNumber()));
        }
        return toResponse(order);
    }

    private CancellationEligibilityResponse eligibility(BookingOrder order) {
        Instant now = clock.instant();
        if (order.getStatus() == BookingOrderStatus.CANCELLED
                || order.getStatus() == BookingOrderStatus.EXPIRED
                || (order.getStatus() == BookingOrderStatus.PENDING && !order.isActive(now))) {
            return new CancellationEligibilityResponse(
                    false, "ORDER_NOT_ACTIVE", "Đơn đã hủy hoặc hết hạn", 0, 0, "VND", "Hủy toàn bộ phòng trong đơn.");
        }
        Payment payment = latestPayment(order);
        if (order.getStatus() == BookingOrderStatus.PENDING) {
            long received = payment == null
                    ? 0
                    : payment.getAllocations().stream()
                            .mapToLong(PaymentAllocation::getAppliedAmount)
                            .sum();
            return new CancellationEligibilityResponse(
                    true,
                    null,
                    null,
                    received > 0 ? 100 : 0,
                    received,
                    "VND",
                    "Hủy toàn đơn đang giữ phòng; hoàn lại số tiền đã nhận nếu có.");
        }
        long refund = 0;
        int percent = 0;
        for (Booking booking : order.getBookings()) {
            CancellationDecision decision =
                    cancellationPolicy.evaluate(booking, payment == null ? null : payment.getStatus(), now);
            if (!decision.allowed())
                return new CancellationEligibilityResponse(
                        false,
                        decision.denialCode(),
                        decision.message(),
                        0,
                        0,
                        "VND",
                        "Chỉ hủy toàn đơn khi tất cả phòng còn đủ điều kiện.");
            refund = Math.addExact(refund, decision.refundAmount());
            percent = decision.refundPercent();
        }
        return new CancellationEligibilityResponse(
                true,
                null,
                null,
                percent,
                refund,
                "VND",
                "Hủy toàn bộ phòng trong đơn; hoàn " + percent + "% tiền phòng theo chính sách khách sạn.");
    }

    private void ensureAccess(BookingOrder order, String token) {
        UUID userId = currentUser.currentUserId().orElse(null);
        boolean owner = userId != null
                && order.getBookedBy() != null
                && userId.equals(order.getBookedBy().getId());
        if (!owner && !tokens.matches(token, order.getAccessTokenHash())) throw notFound();
    }

    private Payment latestPayment(BookingOrder order) {
        return paymentRepository.findAllByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .findFirst()
                .orElse(null);
    }

    private BookingOrderResponse toResponse(BookingOrder order) {
        Payment payment = latestPayment(order);
        long totalAmount =
                order.getBookings().stream().mapToLong(Booking::getTotalAmount).sum();
        long remainingAmount = payment == null
                ? 0
                : Math.max(
                        0,
                        payment.getAmount()
                                - payment.getAllocations().stream()
                                        .mapToLong(allocation -> allocation.getAppliedAmount())
                                        .sum());
        long paid =
                order.getBookings().stream().mapToLong(Booking::getPaidAmount).sum();
        long refund = payment == null
                ? 0
                : payment.getRefunds().stream()
                        .filter(r -> r.getStatus() != PaymentRefundStatus.CANCELLED
                                && r.getStatus() != PaymentRefundStatus.FAILED)
                        .mapToLong(r -> r.getAmount().longValueExact())
                        .sum();
        var refundStatus = payment == null
                ? null
                : payment.getRefunds().stream()
                        .max(Comparator.comparing((PaymentRefund r) -> r.getStatus() == PaymentRefundStatus.PENDING
                                        || r.getStatus() == PaymentRefundStatus.PROCESSING)
                                .thenComparing(PaymentRefund::getRequestedAt))
                        .map(PaymentRefund::getStatus)
                        .orElse(null);
        BookingOrderStatus status = order.getStatus() == BookingOrderStatus.PENDING && !order.isActive(clock.instant())
                ? BookingOrderStatus.EXPIRED
                : order.getStatus();
        return new BookingOrderResponse(
                order.getId(),
                order.getOrderCode(),
                status,
                order.getCheckInDate(),
                order.getCheckOutDate(),
                order.getContactName(),
                order.getContactEmail(),
                totalAmount,
                paid,
                "VND",
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getBookings().stream()
                        .map(b -> new BookingOrderResponse.Room(
                                b.getId(),
                                b.getBookingCode(),
                                b.getRoomType().getId(),
                                b.getRoomType().getName(),
                                b.getAdultCount(),
                                b.getChildCount(),
                                b.getTotalAmount(),
                                b.getPaidAmount(),
                                b.getStatus(),
                                b.getAssignedRoom() == null
                                        ? null
                                        : b.getAssignedRoom().getRoomNumber()))
                        .toList(),
                payment == null
                        ? null
                        : CreatePaymentRequestResponse.from(
                                payment,
                                sePayService.generateQr(remainingAmount, payment.getPaymentCode()),
                                remainingAmount),
                payment == null ? null : payment.getStatus(),
                refund,
                refundStatus,
                payment != null
                        && payment.getRefunds().stream()
                                .anyMatch(r ->
                                        r.getStatus() == PaymentRefundStatus.PENDING && r.getRefundMethod() == null),
                order.getTotalAmount(),
                totalAmount - order.getTotalAmount());
    }

    private static BookingNotFoundException notFound() {
        return new BookingNotFoundException("Không tìm thấy đơn đặt phòng");
    }
}
