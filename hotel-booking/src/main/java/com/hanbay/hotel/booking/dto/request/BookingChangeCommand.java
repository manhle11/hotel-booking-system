package com.hanbay.hotel.booking.dto.request;

import com.hanbay.hotel.booking.domain.ArrivalContact;
import com.hanbay.hotel.booking.domain.BookingChangeRequest;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class BookingChangeCommand {
    private BookingChangeCommand() {}

    public record Create(
            @NotNull BookingChangeRequest.Type type,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            @NotBlank @Size(max = 500) String reason,
            ArrivalContact.Channel contactChannel) {
        public Create(BookingChangeRequest.Type type, LocalDate checkInDate, LocalDate checkOutDate, String reason) {
            this(type, checkInDate, checkOutDate, reason, null);
        }
    }

    public record Approve(
            @NotBlank @Size(max = 128) String quoteToken,
            @JsonDeserialize(using = CheckOutAmountDeserializer.class) @PositiveOrZero @Max(9_999_999_999L) Long feeAmount,
            @NotBlank @Size(max = 500) String note,
            Boolean customerAccepted,
            Boolean servicesConfirmed,
            java.util.Map<String, java.util.List<@NotNull @Min(0) Integer>> childAges,
            java.time.Instant balanceDueAt) {
        public Approve(String quoteToken, Long feeAmount, String note) {
            this(quoteToken, feeAmount, note, false, false, null, null);
        }
    }

    public record Reject(@NotBlank @Size(max = 500) String note) {}
}
