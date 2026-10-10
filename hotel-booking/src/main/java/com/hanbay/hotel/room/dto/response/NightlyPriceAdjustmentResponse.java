package com.hanbay.hotel.room.dto.response;

import com.hanbay.hotel.pricing.domain.AdjustmentDirection;
import com.hanbay.hotel.pricing.domain.AdjustmentType;
import com.hanbay.hotel.pricing.domain.RuleType;

public record NightlyPriceAdjustmentResponse(
        String name,
        RuleType ruleType,
        AdjustmentType adjustmentType,
        AdjustmentDirection adjustmentDirection,
        long adjustmentValue,
        long amount) {}
