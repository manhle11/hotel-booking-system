package com.hanbay.hotel.booking.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanbay.hotel.booking.config.HotelStayProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HotelStayPolicyTest {
    private static final LocalDate ARRIVAL = LocalDate.of(2026, 11, 5);

    private HotelStayPolicy at(String instant) {
        return new HotelStayPolicy(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), new HotelStayProperties());
    }

    @Test
    void usesHotelDateWhenUtcIsStillYesterday() {
        assertThat(at("2026-11-04T17:30:00Z").today()).isEqualTo(ARRIVAL);
    }

    @Test
    void requiresEarlyArrivalApprovalOnlyBeforeStandardCheckInTime() {
        assertThat(at("2026-11-05T06:59:59Z").isEarlyArrival(ARRIVAL)).isTrue();
        assertThat(at("2026-11-05T07:00:00Z").isEarlyArrival(ARRIVAL)).isFalse();
    }

    @Test
    void allowsOriginalBookingAfterMidnightUntilLateArrivalCutoff() {
        assertThat(at("2026-11-05T19:00:00Z").canArrive(ARRIVAL, ARRIVAL.plusDays(1)))
                .isTrue();
        assertThat(at("2026-11-05T23:00:00Z").canArrive(ARRIVAL, ARRIVAL.plusDays(1)))
                .isFalse();
        assertThat(at("2026-11-05T19:00:00Z").isEarlyArrival(ARRIVAL)).isFalse();
    }

    @Test
    void rejectsArrivalBeforeBookedDateOrAfterLateWindow() {
        assertThat(at("2026-11-04T07:00:00Z").canArrive(ARRIVAL, ARRIVAL.plusDays(2)))
                .isFalse();
        assertThat(at("2026-11-07T07:00:00Z").canArrive(ARRIVAL, ARRIVAL.plusDays(3)))
                .isFalse();
    }

    @Test
    void distinguishesCheckInTimeFromSameDayBookingCutoff() {
        assertThat(at("2026-11-05T15:00:00Z").bookingClosed(ARRIVAL)).isFalse();
        assertThat(at("2026-11-05T16:59:59Z").bookingClosed(ARRIVAL)).isTrue();
        assertThat(at("2026-11-05T16:59:59Z").bookingClosed(ARRIVAL.plusDays(1)))
                .isFalse();
    }

    @Test
    void treatsCheckoutAsOverdueOnlyAfterItsConfiguredTime() {
        assertThat(at("2026-11-05T05:00:00Z").isOverdue(ARRIVAL)).isFalse();
        assertThat(at("2026-11-05T05:00:01Z").isOverdue(ARRIVAL)).isTrue();
        assertThat(at("2026-11-05T05:00:00Z").overdueThrough()).isEqualTo(ARRIVAL.minusDays(1));
        assertThat(at("2026-11-05T05:00:01Z").overdueThrough()).isEqualTo(ARRIVAL);
    }

    @Test
    void honoursConfiguredSchedule() {
        var properties = new HotelStayProperties();
        properties.setCheckInTime(LocalTime.of(15, 0));
        properties.setSameDayBookingCutoff(LocalTime.of(22, 0));
        var policy =
                new HotelStayPolicy(Clock.fixed(Instant.parse("2026-11-05T07:30:00Z"), ZoneOffset.UTC), properties);
        assertThat(policy.isEarlyArrival(ARRIVAL)).isTrue();
        assertThat(properties.isValidSchedule()).isTrue();
        properties.setCheckOutTime(LocalTime.of(16, 0));
        assertThat(properties.isValidSchedule()).isFalse();
    }
}
