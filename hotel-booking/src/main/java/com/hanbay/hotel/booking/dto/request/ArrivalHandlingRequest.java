package com.hanbay.hotel.booking.dto.request;

import com.hanbay.hotel.booking.domain.ArrivalContact;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ArrivalHandlingRequest {
    private ArrivalHandlingRequest() {}

    public record Contact(
            @NotBlank @Size(max = 500) String note,
            @Size(min = 1, max = 10) List<@NotBlank @Size(max = 50) String> bookingCodes) {
        public Contact(String note) {
            this(note, null);
        }
    }

    public record StructuredContact(
            @NotNull ArrivalContact.Channel channel,
            @NotNull ArrivalContact.Outcome outcome,
            @Size(max = 500) String note,
            @Size(min = 1, max = 10) List<@NotBlank @Size(max = 50) String> bookingCodes) {
        public StructuredContact(ArrivalContact.Channel channel, ArrivalContact.Outcome outcome, String note) {
            this(channel, outcome, note, null);
        }
    }

    public record Estimate(
            @NotNull Instant expectedAt,
            @Size(max = 500) String note,
            @Size(min = 1, max = 10) List<@NotBlank @Size(max = 50) String> bookingCodes) {
        public Estimate(Instant expectedAt, String note) {
            this(expectedAt, note, null);
        }
    }

    public record HoldDecision(
            @NotNull Instant requestedUntil,
            @NotNull Boolean approve,
            @NotBlank @Size(max = 500) String note,
            @Size(min = 1, max = 10) List<@NotBlank @Size(max = 50) String> bookingCodes) {
        public HoldDecision(Instant requestedUntil, Boolean approve, String note) {
            this(requestedUntil, approve, note, null);
        }
    }

    public record Hold(
            @NotNull Instant until,
            @NotBlank @Size(max = 500) String note,
            Instant expectedAt,
            @Size(min = 1, max = 10) List<@NotBlank @Size(max = 50) String> bookingCodes) {
        public Hold(Instant until, String note, Instant expectedAt) {
            this(until, note, expectedAt, null);
        }

        public Hold(Instant until, String note) {
            this(until, note, null, null);
        }
    }

    public record NoShow(
            @NotBlank @Size(max = 128) String previewToken,
            @tools.jackson.databind.annotation.JsonDeserialize(using = CheckOutAmountDeserializer.class)
                    @PositiveOrZero @Max(9_999_999_999L) Long feeAmount,
            @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 500) String contactNote,
            ArrivalContact.Channel channel,
            ArrivalContact.Outcome outcome) {
        public NoShow(String previewToken, Long feeAmount, String reason, String contactNote) {
            this(previewToken, feeAmount, reason, contactNote, null, null);
        }
    }

    public record Acceptance(@Size(max = 50) String arrivalPolicyVersion) {}
}
