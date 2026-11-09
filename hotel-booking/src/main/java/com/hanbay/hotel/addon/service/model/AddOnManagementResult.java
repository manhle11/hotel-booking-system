package com.hanbay.hotel.addon.service.model;

import com.hanbay.hotel.addon.domain.AddOnQuantityUnit;
import java.time.Instant;
import java.util.UUID;

public record AddOnManagementResult(
        UUID id,
        String code,
        String name,
        String description,
        long price,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        AddOnQuantityUnit quantityUnit) {
    public AddOnManagementResult(
            UUID id,
            String code,
            String name,
            String description,
            long price,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this(id, code, name, description, price, active, createdAt, updatedAt, AddOnQuantityUnit.UNIT);
    }
}
