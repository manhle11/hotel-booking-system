package com.hanbay.hotel.addon.service.model;

import com.hanbay.hotel.addon.domain.AddOnQuantityUnit;

public record UpdateAddOnCommand(String name, String description, Long price, AddOnQuantityUnit quantityUnit) {
    public UpdateAddOnCommand(String name, String description, Long price) {
        this(name, description, price, null);
    }
}
