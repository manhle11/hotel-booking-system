package com.hanbay.hotel.ai.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AiUiAction(Type type, String label, Params params) {

    public enum Type {
        CONTINUE_BOOKING,
        REVIEW_CANCELLATION,
        SELECT_ADDONS,
        VIEW_PAYMENT
    }

    public record Params(
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer adults,
            Integer children,
            String bookingCode,
            UUID paymentId,
            List<Integer> childAges) {
        public Params(
                UUID roomTypeId,
                LocalDate checkIn,
                LocalDate checkOut,
                Integer adults,
                Integer children,
                String bookingCode,
                UUID paymentId) {
            this(roomTypeId, checkIn, checkOut, adults, children, bookingCode, paymentId, null);
        }
    }
}
