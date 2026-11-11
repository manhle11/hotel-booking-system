package com.hanbay.hotel.review.dto;

public record ManagedReviewSummaryResponse(
        long totalReviews, long unrepliedReviews, long repliedReviews, long lowRatingReviews) {}
