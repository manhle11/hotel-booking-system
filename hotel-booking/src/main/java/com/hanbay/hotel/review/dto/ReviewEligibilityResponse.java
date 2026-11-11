package com.hanbay.hotel.review.dto;

import java.time.Instant;

public record ReviewEligibilityResponse(
        boolean canReview, String reasonCode, String message, Instant deadline, ReviewResponse review) {}
