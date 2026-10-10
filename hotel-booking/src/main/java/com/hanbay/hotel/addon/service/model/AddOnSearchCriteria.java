package com.hanbay.hotel.addon.service.model;

public record AddOnSearchCriteria(int page, int size, String search, Boolean active, String sortBy, String direction) {}
