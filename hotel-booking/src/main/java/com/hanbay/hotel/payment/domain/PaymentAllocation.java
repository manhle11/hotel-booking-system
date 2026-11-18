package com.hanbay.hotel.payment.domain;

import com.hanbay.hotel.booking.domain.Booking;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "payment_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "booking_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long appliedAmount;

    /** Applies only the room's share; overpayments never inflate the room balance. */
    public long apply(long availableAmount) {
        return apply(availableAmount, Long.MAX_VALUE);
    }

    public long apply(long availableAmount, long currentBalance) {
        long applied = Math.min(Math.min(availableAmount, amount - appliedAmount), Math.max(currentBalance, 0));
        if (applied > 0) {
            booking.recordPayment(applied);
            appliedAmount = Math.addExact(appliedAmount, applied);
        }
        return applied;
    }
}
