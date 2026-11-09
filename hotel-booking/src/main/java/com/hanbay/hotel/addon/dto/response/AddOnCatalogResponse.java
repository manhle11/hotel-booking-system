package com.hanbay.hotel.addon.dto.response;

import com.hanbay.hotel.addon.domain.AddOnQuantityUnit;
import java.util.UUID;

public record AddOnCatalogResponse(
        UUID id, String code, String name, String description, long price, AddOnQuantityUnit quantityUnit) {
    public AddOnCatalogResponse(UUID id, String code, String name, String description, long price) {
        this(id, code, name, description, price, AddOnQuantityUnit.UNIT);
    }
}
