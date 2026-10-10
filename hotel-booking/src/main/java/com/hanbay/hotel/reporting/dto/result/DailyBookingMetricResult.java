package com.hanbay.hotel.reporting.dto.result;

import java.time.LocalDate;

public record DailyBookingMetricResult(LocalDate date, long totalBookings) {}
