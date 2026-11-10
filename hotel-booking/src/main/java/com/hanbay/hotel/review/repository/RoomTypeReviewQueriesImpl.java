package com.hanbay.hotel.review.repository;

import com.hanbay.hotel.review.domain.RoomTypeReview;
import com.hanbay.hotel.review.dto.ManagedReviewSummaryResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@RequiredArgsConstructor
public class RoomTypeReviewQueriesImpl implements RoomTypeReviewQueries {
    private final EntityManager entityManager;

    @Override
    public ManagedReviewSummaryResponse summarizeManaged(Specification<RoomTypeReview> specification) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(RoomTypeReview.class);
        query.multiselect(
                cb.count(root),
                cb.coalesce(
                        cb.sum(cb.<Long>selectCase()
                                .when(cb.isNull(root.get("hotelReply")), 1L)
                                .otherwise(0L)),
                        0L),
                cb.coalesce(
                        cb.sum(cb.<Long>selectCase()
                                .when(cb.isNotNull(root.get("hotelReply")), 1L)
                                .otherwise(0L)),
                        0L),
                cb.coalesce(
                        cb.sum(cb.<Long>selectCase()
                                .when(cb.le(root.get("rating"), 2), 1L)
                                .otherwise(0L)),
                        0L));
        query.where(specification.toPredicate(root, query, cb));
        var result = entityManager.createQuery(query).getSingleResult();
        return new ManagedReviewSummaryResponse(
                result.get(0, Long.class),
                result.get(1, Long.class),
                result.get(2, Long.class),
                result.get(3, Long.class));
    }
}
