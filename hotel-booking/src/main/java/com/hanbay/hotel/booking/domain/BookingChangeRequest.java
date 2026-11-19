package com.hanbay.hotel.booking.domain;

import com.hanbay.hotel.common.domain.BaseEntity;
import com.hanbay.hotel.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "booking_change_requests")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BookingChangeRequest extends BaseEntity {
    public enum Type {
        CANCELLATION,
        RESCHEDULE
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false, length = 80)
    private String scopeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    private LocalDate requestedCheckIn;
    private LocalDate requestedCheckOut;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    private Instant decidedAt;

    @Column(length = 500)
    private String decisionNote;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> snapshot;

    @Builder.Default
    private boolean dropServices = false;

    public void changeServices(boolean drop, String note) {
        dropServices = drop;
        consentAt = null;
        consentToken = null;
        decisionNote = note;
    }

    @Builder.Default
    private boolean managerException = false;

    public void allowManagerException(String note) {
        managerException = true;
        consentAt = null;
        consentToken = null;
        decisionNote = note;
    }

    private String consentToken;
    private Instant consentAt;

    public void recordConsent(String token, Instant at) {
        consentToken = token;
        consentAt = at;
    }

    public void recordSnapshot(Map<String, Object> values) {
        snapshot = values;
    }

    public void endAfterCancellation(User actor, Instant now) {
        if (status != Status.PENDING || type != Type.RESCHEDULE) return;
        var audit = new java.util.LinkedHashMap<String, Object>();
        if (snapshot != null) audit.putAll(snapshot);
        audit.put("autoClosed", true);
        audit.put("closureReason", "BOOKING_CANCELLED");
        decide(false, actor, now, "Đã kết thúc yêu cầu đổi lịch vì khách đã hủy booking/đơn.", audit);
    }

    @Version
    private int version;

    public static String scopeOf(Booking booking) {
        return booking.getOrder() == null
                ? "BOOKING:" + booking.getId()
                : "ORDER:" + booking.getOrder().getId();
    }

    public void decide(boolean approved, User actor, Instant now, String note, Map<String, Object> snapshot) {
        if (status != Status.PENDING) throw new IllegalStateException("Yêu cầu đã được xử lý.");
        status = approved ? Status.APPROVED : Status.REJECTED;
        decidedBy = actor;
        decidedAt = now;
        decisionNote = note;
        this.snapshot = snapshot;
    }
}
