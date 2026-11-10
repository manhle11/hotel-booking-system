package com.hanbay.hotel.review.dto;

import com.hanbay.hotel.review.exception.ReviewException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record ManagedReviewFilter(
        UUID roomTypeId,
        Integer rating,
        boolean deleted,
        Boolean replied,
        String search,
        LocalDate fromDate,
        LocalDate toDate) {
    public ManagedReviewFilter {
        search = search == null || search.isBlank() ? null : search.strip();
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new ReviewException("REVIEW_INVALID_RATING", "Số sao phải từ 1 đến 5", HttpStatus.BAD_REQUEST);
        }
        if (search != null && search.length() > 100) {
            throw new ReviewException("REVIEW_INVALID_SEARCH", "Từ khóa tối đa 100 ký tự", HttpStatus.BAD_REQUEST);
        }
        if ((fromDate != null && toDate != null && fromDate.isAfter(toDate)) || LocalDate.MAX.equals(toDate)) {
            throw new ReviewException(
                    "REVIEW_INVALID_DATE_RANGE", "Khoảng ngày đánh giá không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }
}
