package com.hanbay.hotel.booking.domain;

import com.hanbay.hotel.booking.domain.addon.BookingAddOn;
import com.hanbay.hotel.booking.exception.BookingValidationException;
import com.hanbay.hotel.booking.exception.InvalidBookingStateException;
import com.hanbay.hotel.common.domain.BaseEntity;
import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NaturalId;
import org.hibernate.type.SqlTypes;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
        name = "bookings",
        indexes = {
            @Index(name = "idx_booked_by", columnList = "booked_by"),
            @Index(name = "idx_room_booking", columnList = "room_type_id, check_in_date, check_out_date"),
            @Index(name = "idx_status", columnList = "status")
        })
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @EqualsAndHashCode.Include
    @Column(name = "booking_code", unique = true, nullable = false, length = 50)
    @NaturalId
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by")
    private User bookedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private BookingOrder order;

    private Integer orderRoomNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_room_id")
    private Room assignedRoom;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Builder.Default
    @Column(name = "adult_count", nullable = false)
    private int adultCount = 1;

    @Builder.Default
    @Column(name = "child_count", nullable = false)
    private int childCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "child_ages", columnDefinition = "jsonb")
    private List<Integer> childAges;

    public void recordChildAges(List<Integer> ages) {
        this.childAges = ages == null ? null : List.copyOf(ages);
    }

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Builder.Default
    @Column(name = "total_amount", nullable = false)
    private long totalAmount = 0L;

    @Builder.Default
    @Column(name = "paid_amount", nullable = false)
    private long paidAmount = 0L;

    @Column(name = "access_token_hash", length = 64)
    private String accessTokenHash;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(length = 50)
    private String arrivalPolicyVersion;

    private Instant arrivalHoldUntil;
    private Instant arrivalHoldRequestedUntil;
    private Instant expectedArrivalAt;
    private Instant arrivalContactAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ArrivalContact.Channel arrivalContactChannel;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ArrivalContact.Outcome arrivalContactOutcome;

    @Column(length = 500)
    private String arrivalContactNote;

    private Instant noShowAt;
    private Long noShowFee;
    private Long noShowOriginalTotal;

    @Column(length = 50)
    private String noShowPolicyVersion;

    private UUID replacementForBookingId;

    @Builder.Default
    private int stayRevision = 0;

    private Long cancellationOriginalTotal;
    private Long cancellationFee;
    private Instant balanceDueAt;

    public void setBalanceDueAt(Instant dueAt) {
        balanceDueAt = dueAt;
    }

    public void recordSettledFeeCollection(long credited, User actor, String reason) {
        if (!com.hanbay.hotel.booking.policy.ClosedBookingFeePolicy.applies(this)
                || credited < 0
                || credited > totalAmount)
            throw new BookingValidationException("Booking không có phí đã quyết toán để ghi nhận thu tiền.");
        paidAmount = credited;
        recordStaffNote(actor, reason);
    }

    public void applyFeeReduction(long fee, long available, User actor, Instant now, String reason) {
        long delta = fee - policyFeeTotal;
        if (delta > 0) throw new BookingValidationException("Điều chỉnh chỉ được giảm/miễn phí đã phát sinh.");
        recordPolicyFee(fee, "MANAGER_REDUCTION", actor, now, reason);
        totalAmount = Math.addExact(totalAmount, delta);
        paidAmount = Math.min(totalAmount, available);
        if (status == BookingStatus.CANCELLED) cancellationFee = fee;
        if (status == BookingStatus.NO_SHOW) noShowFee = fee;
        recordStaffNote(actor, "Quản lý giảm phí: " + reason);
    }

    @Builder.Default
    private boolean cancellationReviewRequired = false;

    public void cancelForSettlementReview(User actor, Instant now, String reason) {
        cancellationOriginalTotal = totalAmount;
        cancellationReviewRequired = true;
        cancellationFee = null;
        totalAmount = 0; // No unaccepted fee is booked as debt.
        assignedRoom = null;
        arrivalHoldUntil = null;
        arrivalHoldRequestedUntil = null;
        cancel(actor, reason, now);
    }

    public void settleCancelledBooking(User actor, long fee, long credited, String reason) {
        if (status != BookingStatus.CANCELLED
                || !cancellationReviewRequired
                || fee < 0
                || (com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.applies(this)
                        ? fee > originalFirstNightAmount
                        : fee > cancellationOriginalTotal))
            throw new BookingValidationException("Booking không có quyết toán hủy chờ xem xét.");
        cancellationReviewRequired = false;
        cancellationFee = fee;
        totalAmount = fee;
        paidAmount = credited;
        recordStaffNote(actor, reason);
    }

    @Column(length = 50)
    private String termsVersion;

    @Column(columnDefinition = "text")
    private String acceptedTerms;

    private Instant termsAcceptedAt;
    private LocalDate originalCheckInDate;
    private Long originalFirstNightAmount;

    @Builder.Default
    private long policyFeeTotal = 0;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingPolicyFee> policyFees = new ArrayList<>();

    public void acceptStandardTerms(Instant now) {
        if (status != BookingStatus.PENDING || termsVersion != null) return;
        termsVersion = com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.VERSION;
        acceptedTerms = com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.TERMS;
        termsAcceptedAt = Objects.requireNonNull(now);
        originalCheckInDate = checkInDate;
        originalFirstNightAmount = com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.firstNight(this);
    }

    public void recordPolicyFee(long total, String event, User actor, Instant now, String reason) {
        if (!com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.applies(this)
                || total < 0
                || total > originalFirstNightAmount) {
            throw new BookingValidationException("Tổng phí phải từ 0 đến căn cứ P gốc.");
        }
        policyFees.add(BookingPolicyFee.builder()
                .booking(this)
                .event(event)
                .previousAmount(policyFeeTotal)
                .amount(total - policyFeeTotal)
                .totalAmount(total)
                .actor(actor)
                .occurredAt(now)
                .reason(Objects.requireNonNull(reason))
                .build());
        policyFeeTotal = total;
    }

    public void recordStaffNote(User actor, String note) {
        changeStatus(status, actor, note);
    }

    public void cancelAtFrontDesk(User actor, Instant now, long fee, long creditedAmount, String reason) {
        if (status != BookingStatus.CONFIRMED
                || checkedInAt != null
                || fee < 0
                || (com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.applies(this)
                        ? fee > originalFirstNightAmount
                        : fee > totalAmount)) {
            throw new InvalidBookingStateException("Booking không đủ điều kiện hủy tại quầy.");
        }
        cancellationOriginalTotal = totalAmount;
        cancellationFee = fee;
        totalAmount = fee;
        paidAmount = creditedAmount;
        assignedRoom = null;
        arrivalHoldUntil = null;
        arrivalHoldRequestedUntil = null;
        cancel(actor, reason, now);
    }

    public void rescheduleConfirmed(
            LocalDate arrival,
            LocalDate departure,
            List<BookingNight> repricedNights,
            long newTotal,
            long creditedAmount,
            User actor,
            String note) {
        if (status != BookingStatus.CONFIRMED || checkedInAt != null) {
            throw new InvalidBookingStateException("Chỉ booking đã xác nhận chưa nhận phòng được đổi lịch.");
        }
        if (!arrival.equals(checkInDate)) childAges = null;
        checkInDate = arrival;
        checkOutDate = departure;
        totalAmount = newTotal;
        paidAmount = creditedAmount;
        assignedRoom = null;
        arrivalHoldUntil = null;
        arrivalHoldRequestedUntil = null;
        stayRevision++;
        expectedArrivalAt = null;
        repricedNights.forEach(this::addNight);
        recordStaffNote(actor, note);
    }

    public void acceptArrivalPolicy(String version) {
        if (status == BookingStatus.PENDING) arrivalPolicyVersion = Objects.requireNonNull(version);
    }

    public void recordArrivalContact(User actor, String note) {
        changeStatus(status, Objects.requireNonNull(actor), "Liên hệ khách chưa đến: " + note);
    }

    public void recordArrivalContact(
            User actor, ArrivalContact.Channel channel, ArrivalContact.Outcome outcome, String note, Instant now) {
        arrivalContactAt = now;
        arrivalContactChannel = channel;
        arrivalContactOutcome = outcome;
        arrivalContactNote = note;
        recordArrivalContact(actor, channel.label() + " / " + outcome.label() + (note.isBlank() ? "" : ": " + note));
    }

    public void updateExpectedArrival(Instant expectedAt, User actor, String note) {
        expectedArrivalAt = expectedAt;
        changeStatus(
                status, actor, "Cập nhật giờ khách dự kiến đến: " + expectedAt + (note.isBlank() ? "" : "; " + note));
    }

    public void rejectArrivalHold(User actor, String note) {
        arrivalHoldRequestedUntil = null;
        changeStatus(status, actor, "Từ chối đề nghị nhận sau hạn: " + note);
    }

    public void holdArrival(Instant until, User actor, boolean approved, String note) {
        if (status != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException("Chỉ booking đã xác nhận mới được giữ để đến muộn.");
        }
        arrivalHoldRequestedUntil = until;
        if (approved) arrivalHoldUntil = until;
        changeStatus(
                status, actor, (approved ? "Duyệt giữ phòng đến " : "Đề nghị giữ phòng đến ") + until + ": " + note);
    }

    public void markNoShow(User actor, Instant now, long fee, String policyVersion, String reason) {
        if (status != BookingStatus.CONFIRMED || checkedInAt != null) {
            throw new InvalidBookingStateException("Chỉ booking chưa nhận phòng đã xác nhận mới được đóng no-show.");
        }
        if (fee < 0
                || (com.hanbay.hotel.booking.policy.UnifiedBookingPolicy.applies(this)
                        ? fee > originalFirstNightAmount
                        : fee > totalAmount)) throw new BookingValidationException("Phí no-show không hợp lệ.");
        noShowOriginalTotal = totalAmount;
        noShowFee = fee;
        noShowPolicyVersion = Objects.requireNonNull(policyVersion);
        totalAmount = fee;
        noShowAt = Objects.requireNonNull(now);
        assignedRoom = null;
        expiredAt = null;
        arrivalHoldUntil = null;
        arrivalHoldRequestedUntil = null;
        changeStatus(BookingStatus.NO_SHOW, Objects.requireNonNull(actor), reason);
    }

    public void releaseArrivalHold(User actor, String note) {
        arrivalHoldUntil = null;
        arrivalHoldRequestedUntil = null;
        changeStatus(status, actor, "Quản lý kết thúc thỏa thuận giữ phòng: " + note);
    }

    public void linkReplacement(Booking original, User creator) {
        replacementForBookingId = Objects.requireNonNull(original.getId());
        changeStatus(
                status,
                Objects.requireNonNull(creator),
                "Quản lý tạo kỳ ở mới thay booking " + original.getBookingCode() + "; quyết toán tiền độc lập");
    }

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingGuest> guests = new ArrayList<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingNight> nights = new ArrayList<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingStatusHistory> statusHistories = new ArrayList<>();

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingAddOn> addOns = new ArrayList<>();

    public void recordCreated(User createdBy) {

        BookingStatusHistory history = BookingStatusHistory.builder()
                .oldStatus(null)
                .newStatus(BookingStatus.PENDING)
                .changedBy(createdBy)
                .reason("Người dùng đặt phòng")
                .build();

        addStatusHistory(history);
    }

    public void assignOrder(BookingOrder order, int roomNumber) {
        this.order = Objects.requireNonNull(order);
        this.orderRoomNumber = roomNumber;
        this.expiredAt = order.getExpiresAt();
    }

    public void claimBy(User owner) {
        Objects.requireNonNull(owner);
        if (bookedBy != null && !bookedBy.getId().equals(owner.getId())) {
            throw new InvalidBookingStateException("Đặt phòng đã thuộc tài khoản khác.");
        }
        bookedBy = owner;
    }

    public void addGuest(BookingGuest guest) {
        Objects.requireNonNull(guest, "Guest không được để trống");

        String identity = guest.getIdentityNumber();
        if (identity != null && !identity.trim().isEmpty()) {
            String normalizedIdentity = identity.trim();

            boolean isDuplicate = this.guests.stream()
                    .map(BookingGuest::getIdentityNumber)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .anyMatch(normalizedIdentity::equals);

            if (isDuplicate) {
                throw new BookingValidationException("Khách hàng với CMND/CCCD này đã tồn tại trong Booking.");
            }
        }

        this.guests.add(guest);
        guest.assignBooking(this);
    }

    public void removeGuest(BookingGuest guest) {
        Objects.requireNonNull(guest, "Guest không được để trống");
        this.guests.remove(guest);
        guest.assignBooking(null);
    }

    public void addNight(BookingNight night) {
        Objects.requireNonNull(night, "Night không được để trống");

        boolean isDuplicate = this.nights.stream().anyMatch(n -> n.getStayDate().equals(night.getStayDate()));

        if (isDuplicate) {
            throw new BookingValidationException("Ngày lưu trú " + night.getStayDate() + " đã tồn tại trong Booking.");
        }
        this.nights.add(night);
        night.assignBooking(this);
    }

    public void removeNight(BookingNight night) {
        Objects.requireNonNull(night, "Night không được để trống");
        this.nights.remove(night);
        night.assignBooking(null);
    }

    private void addStatusHistory(BookingStatusHistory history) {
        this.statusHistories.add(history);
        history.assignBooking(this);
    }

    private void changeStatus(BookingStatus newStatus, User changedBy, String reason) {
        BookingStatus oldStatus = this.status;
        this.status = newStatus;

        BookingStatusHistory history = BookingStatusHistory.builder()
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .reason(reason)
                .build();

        this.addStatusHistory(history);
    }

    public void addAddOn(BookingAddOn addOn) {
        Objects.requireNonNull(addOn, "Dịch vụ không được để trống");

        boolean duplicated = this.addOns.stream().anyMatch(a -> a.getAddOnService()
                .getId()
                .equals(addOn.getAddOnService().getId()));

        if (duplicated) {
            throw new BookingValidationException("Dịch vụ đã được thêm vào đặt phòng.");
        }

        this.addOns.add(addOn);
        addOn.assignBooking(this);
    }

    public void removeAddOn(BookingAddOn addOn) {
        Objects.requireNonNull(addOn);

        this.addOns.remove(addOn);
        addOn.assignBooking(null);
    }

    public void confirm(User confirmedBy, Instant currentTime) {
        confirm(currentTime, confirmedBy);
    }

    public void confirmAutomatically(Instant currentTime) {
        confirm(currentTime, null);
    }

    private void confirm(Instant currentTime, User confirmedBy) {
        Objects.requireNonNull(currentTime, "Thời gian xác nhận không được để trống");

        if (!this.status.canConfirm()) {
            throw new InvalidBookingStateException("Chỉ có thể xác nhận đặt phòng khi đang ở trạng thái PENDING.");
        }

        if (this.expiredAt != null && !currentTime.isBefore(this.expiredAt)) {
            throw new InvalidBookingStateException("Không thể xác nhận đặt phòng đã quá hạn thanh toán.");
        }

        this.expiredAt = null;
        changeStatus(BookingStatus.CONFIRMED, confirmedBy, "Xác nhận đặt phòng thành công");
    }

    public void recordPayment(long amount) {
        if (amount <= 0) {
            throw new BookingValidationException("Số tiền thanh toán phải lớn hơn 0.");
        }
        paidAmount = Math.addExact(paidAmount, amount);
    }

    public void assignAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = Objects.requireNonNull(accessTokenHash, "Mã bảo vệ đặt phòng không được để trống");
    }

    public void cancel(User cancelledBy, String reason, Instant cancelledAt) {
        if (order == null) Objects.requireNonNull(cancelledBy, "Người hủy không được để trống");
        Objects.requireNonNull(cancelledAt, "Thời gian hủy không được để trống");
        if (reason == null || reason.trim().isEmpty()) {
            throw new BookingValidationException("Lý do hủy không được để trống");
        }

        if (!this.status.canCancel()) {
            throw new InvalidBookingStateException("Trạng thái hiện tại (" + this.status + ") không cho phép hủy.");
        }

        this.cancelledAt = cancelledAt;
        this.expiredAt = null;
        changeStatus(BookingStatus.CANCELLED, cancelledBy, reason.trim());
    }

    public void checkIn(Room room, User receptionist) {
        checkIn(room, receptionist, Instant.now(), null);
    }

    public void checkIn(Room room, User receptionist, Instant actualTime, String note) {
        Objects.requireNonNull(room, "Phòng không được để trống");
        Objects.requireNonNull(receptionist, "Lễ tân thực hiện check-in không được để trống");
        if (status != BookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException("Chỉ booking CONFIRMED mới có thể check-in.");
        }

        assignedRoom = room;
        checkedInAt = Objects.requireNonNull(actualTime);
        changeStatus(
                BookingStatus.CHECKED_IN,
                receptionist,
                "Lễ tân check-in và gán phòng " + room.getRoomNumber() + (note == null ? "" : ": " + note));
    }

    public void checkOut(User receptionist, String note) {
        checkOut(receptionist, note, Instant.now());
    }

    public void checkOut(User receptionist, String note, Instant actualTime) {
        Objects.requireNonNull(receptionist, "Lễ tân thực hiện check-out không được để trống");
        if (status != BookingStatus.CHECKED_IN) {
            throw new InvalidBookingStateException("Chỉ booking CHECKED_IN mới có thể check-out.");
        }

        String reason = "Lễ tân check-out khách";
        if (note != null && !note.trim().isEmpty()) {
            reason = reason + ": " + note.trim();
        }
        checkedOutAt = Objects.requireNonNull(actualTime);
        changeStatus(BookingStatus.CHECKED_OUT, receptionist, reason);
    }

    public void extendTo(LocalDate newCheckOutDate, List<BookingNight> additionalNights, long additionalAmount) {
        Objects.requireNonNull(newCheckOutDate, "Ngày trả phòng mới không được để trống");
        if (status != BookingStatus.CONFIRMED && status != BookingStatus.CHECKED_IN) {
            throw new InvalidBookingStateException("Trạng thái hiện tại không cho phép gia hạn lưu trú.");
        }
        if (!newCheckOutDate.isAfter(checkOutDate)) {
            throw new BookingValidationException("Ngày trả phòng mới phải sau ngày trả phòng hiện tại.");
        }
        if (additionalAmount < 0) {
            throw new BookingValidationException("Số tiền gia hạn không hợp lệ.");
        }
        additionalNights.forEach(this::addNight);
        totalAmount = Math.addExact(totalAmount, additionalAmount);
        checkOutDate = newCheckOutDate;
    }

    public void expire(User systemUser, Instant currentTime) {
        Objects.requireNonNull(systemUser, "System User không được để trống");
        expire(currentTime, systemUser);
    }

    public void expireAutomatically(Instant currentTime) {
        expire(currentTime, null);
    }

    private void expire(Instant currentTime, User changedBy) {
        Objects.requireNonNull(currentTime, "Thời gian hiện tại không được để trống");

        if (!this.status.canExpire()) {
            throw new InvalidBookingStateException("Chỉ đặt phòng đang chờ thanh toán mới có thể hết hạn.");
        }

        if (this.expiredAt == null || currentTime.isBefore(this.expiredAt)) {
            throw new InvalidBookingStateException("Booking chưa đến thời hạn hết hạn.");
        }

        this.expiredAt = null;
        changeStatus(BookingStatus.EXPIRED, changedBy, "Hệ thống tự động hủy do quá hạn thanh toán");
    }

    public void applyTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void updatePendingDetails(
            RoomType roomType, LocalDate checkIn, LocalDate checkOut, int adults, int children) {
        updatePendingDetails(roomType, checkIn, checkOut, adults, children, null);
    }

    public void updatePendingDetails(
            RoomType roomType, LocalDate checkIn, LocalDate checkOut, int adults, int children, List<Integer> ages) {
        if (status != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Chỉ đặt phòng đang chờ thanh toán mới được chỉnh sửa.");
        }
        // Ages refer to the arrival date. Omitted legacy data may preserve a known
        // snapshot only while the child count and arrival date remain unchanged.
        List<Integer> updatedAges =
                ages != null ? ages : children == childCount && Objects.equals(checkIn, checkInDate) ? childAges : null;
        if (updatedAges != null && updatedAges.size() != children) {
            throw new BookingValidationException("Số tuổi trẻ em phải khớp số trẻ em trong phòng.");
        }
        termsVersion = null;
        acceptedTerms = null;
        termsAcceptedAt = null;
        originalCheckInDate = null;
        originalFirstNightAmount = null;
        arrivalPolicyVersion = null;
        this.roomType = Objects.requireNonNull(roomType);
        this.checkInDate = Objects.requireNonNull(checkIn);
        this.checkOutDate = Objects.requireNonNull(checkOut);
        this.adultCount = adults;
        this.childCount = children;
        recordChildAges(updatedAges);
        this.guests.clear();
        this.addOns.clear();
        this.nights.clear();
    }
}
