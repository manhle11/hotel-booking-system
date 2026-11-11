package com.hanbay.hotel.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID roomTypeId,
        String roomTypeName,
        String authorName,
        int rating,
        String comment,
        Instant createdAt,
        String hotelReply,
        Instant repliedAt) {}
