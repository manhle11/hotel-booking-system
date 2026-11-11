package com.hanbay.hotel.review.repository;

import com.hanbay.hotel.review.domain.RoomTypeReview;
import com.hanbay.hotel.review.dto.ManagedReviewSummaryResponse;
import org.springframework.data.jpa.domain.Specification;

public interface RoomTypeReviewQueries {
    ManagedReviewSummaryResponse summarizeManaged(Specification<RoomTypeReview> specification);
}
