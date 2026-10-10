package com.hanbay.hotel.pricing.service.model;

import com.hanbay.hotel.pricing.domain.RuleType;
import java.util.UUID;

public record PriceRuleSearchCriteria(
        int page,
        int size,
        String search,
        UUID roomTypeId,
        RuleType ruleType,
        Boolean active,
        String sortBy,
        String direction) {}
