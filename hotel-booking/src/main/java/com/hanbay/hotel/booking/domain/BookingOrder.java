package com.hanbay.hotel.booking.domain;

import com.hanbay.hotel.common.domain.BaseEntity;
import com.hanbay.hotel.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "booking_orders")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BookingOrder extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by")
    private User bookedBy;

    @Column(nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String contactEmail;

    @Column(length = 30)
    private String contactPhone;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingOrderStatus status = BookingOrderStatus.PENDING;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false, length = 64)
    private String accessTokenHash;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant cancelledAt;

    @Builder.Default
    @OneToMany(mappedBy = "order")
    @OrderBy("orderRoomNumber ASC")
    private List<Booking> bookings = new ArrayList<>();

    public void addBooking(Booking booking) {
        booking.assignOrder(this, bookings.size() + 1);
        bookings.add(booking);
        totalAmount = Math.addExact(totalAmount, booking.getTotalAmount());
    }

    public boolean isActive(Instant now) {
        return status == BookingOrderStatus.PENDING && expiresAt.isAfter(now);
    }

    public void confirm(Instant now) {
        if (!isActive(now)) throw new IllegalStateException("Đơn không còn trong thời gian giữ phòng");
        bookings.forEach(booking -> booking.confirmAutomatically(now));
        status = BookingOrderStatus.CONFIRMED;
    }

    public void cancel(User actor, String reason, Instant now) {
        bookings.forEach(booking -> booking.cancel(actor, reason, now));
        status = BookingOrderStatus.CANCELLED;
        cancelledAt = now;
    }

    public void expire(Instant now) {
        if (status != BookingOrderStatus.PENDING || expiresAt.isAfter(now)) return;
        bookings.forEach(booking -> booking.expireAutomatically(now));
        status = BookingOrderStatus.EXPIRED;
    }
}
