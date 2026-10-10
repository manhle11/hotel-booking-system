package com.hanbay.hotel.pricing.dto.response;

import com.hanbay.hotel.pricing.domain.AdjustmentDirection;
import com.hanbay.hotel.pricing.domain.AdjustmentType;
import com.hanbay.hotel.pricing.domain.RuleType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceRuleManagementResponse(
        UUID id,
        RoomTypeSummaryResponse roomType,
        String name,
        RuleType ruleType,
        AdjustmentType adjustmentType,
        long adjustmentValue,
        AdjustmentDirection adjustmentDirection,
        LocalDate startDate,
        LocalDate endDate,
        int priority,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
