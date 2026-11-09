package com.hanbay.hotel.addon.service.model;

import com.hanbay.hotel.addon.domain.AddOnQuantityUnit;

public record CreateAddOnCommand(
        String code, String name, String description, Long price, AddOnQuantityUnit quantityUnit) {
    public CreateAddOnCommand(String code, String name, String description, Long price) {
        this(code, name, description, price, null);
    }
}
