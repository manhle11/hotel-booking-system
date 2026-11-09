package com.hanbay.hotel.addon.dto.response;

import com.hanbay.hotel.addon.domain.AddOnQuantityUnit;
import java.time.Instant;
import java.util.UUID;

public record AddOnManagementResponse(
        UUID id,
        String code,
        String name,
        String description,
        long price,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        AddOnQuantityUnit quantityUnit) {
    public AddOnManagementResponse(
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
