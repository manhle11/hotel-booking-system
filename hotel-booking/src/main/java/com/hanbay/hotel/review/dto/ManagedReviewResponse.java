package com.hanbay.hotel.review.dto;

import java.time.Instant;
import java.util.List;

public record ManagedReviewResponse(
        ReviewResponse review,
        String bookingCode,
        String orderCode,
        String guestName,
        List<ReviewStayResponse> stays,
        Instant deletedAt,
        String deletedByName,
        String deleteReason,
        String repliedByName) {}
