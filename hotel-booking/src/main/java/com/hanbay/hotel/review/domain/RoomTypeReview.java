package com.hanbay.hotel.review.domain;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingOrder;
import com.hanbay.hotel.common.domain.BaseEntity;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "room_type_reviews")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RoomTypeReview extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private BookingOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(nullable = false, length = 150)
    private String reviewerName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    @Column(length = 500)
    private String deleteReason;

    @Column(length = 1000)
    private String hotelReply;

    private Instant repliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replied_by")
    private User repliedBy;

    public void delete(User manager, Instant now) {
        delete(manager, now, null);
    }

    public void delete(User manager, Instant now, String reason) {
        if (deletedAt == null) {
            deletedAt = now;
            deletedBy = manager;
            deleteReason = reason;
        }
    }

    public void reply(User manager, Instant now, String text) {
        hotelReply = text;
        repliedAt = now;
        repliedBy = manager;
    }

    public void removeReply() {
        hotelReply = null;
        repliedAt = null;
        repliedBy = null;
    }
}
