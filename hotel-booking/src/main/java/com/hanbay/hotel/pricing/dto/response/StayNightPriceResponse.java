package com.hanbay.hotel.pricing.dto.response;

import java.time.LocalDate;

public record StayNightPriceResponse(LocalDate date, long finalPrice) {}
