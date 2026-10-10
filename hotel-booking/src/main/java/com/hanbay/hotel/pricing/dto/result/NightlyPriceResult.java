package com.hanbay.hotel.pricing.dto.result;

import java.time.LocalDate;

public record NightlyPriceResult(LocalDate date, long finalPrice) {}
