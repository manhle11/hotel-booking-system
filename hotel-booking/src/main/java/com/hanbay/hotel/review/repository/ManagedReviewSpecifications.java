package com.hanbay.hotel.review.repository;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.review.domain.RoomTypeReview;
import com.hanbay.hotel.review.dto.ManagedReviewFilter;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ManagedReviewSpecifications {
    private ManagedReviewSpecifications() {}

    public static Specification<RoomTypeReview> matching(ManagedReviewFilter filter, ZoneId zone) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(filter.deleted() ? cb.isNotNull(root.get("deletedAt")) : cb.isNull(root.get("deletedAt")));
            if (filter.roomTypeId() != null) {
                predicates.add(cb.equal(root.get("roomType").get("id"), filter.roomTypeId()));
            }
            if (filter.rating() != null) predicates.add(cb.equal(root.get("rating"), filter.rating()));
            if (filter.replied() != null) {
                predicates.add(
                        filter.replied() ? cb.isNotNull(root.get("hotelReply")) : cb.isNull(root.get("hotelReply")));
            }
            if (filter.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        filter.fromDate().atStartOfDay(zone).toInstant()));
            }
            if (filter.toDate() != null) {
                predicates.add(cb.lessThan(
                        root.get("createdAt"),
                        filter.toDate().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (filter.search() != null) {
                String text = filter.search()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + text + "%";
                var order = root.join("order", JoinType.LEFT);
                var booking = root.join("booking");
                // A review covers every booking of this room type in the same order.
                var related = query.subquery(Integer.class);
                var stay = related.from(Booking.class);
                related.select(cb.literal(1))
                        .where(
                                cb.isNotNull(root.get("order")),
                                cb.equal(stay.get("order"), root.get("order")),
                                cb.equal(stay.get("roomType"), root.get("roomType")),
                                cb.like(cb.lower(stay.get("bookingCode")), pattern, '\\'));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reviewerName")), pattern, '\\'),
                        cb.like(cb.lower(booking.get("bookingCode")), pattern, '\\'),
                        cb.like(cb.lower(order.get("orderCode")), pattern, '\\'),
                        cb.exists(related)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
