package com.hanbay.hotel.frontdesk.repository;

import com.hanbay.hotel.booking.dto.response.ArrivalProgress;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ArrivalWorkRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final HotelStayPolicy policy;

    // Filter and paginate before loading details. Hibernate's Instant mapping stores the legacy
    // cancelled_at timestamp in UTC; interpret it explicitly before mixing it with timestamptz.
    private static final String WORK =
            """
        WITH reception AS (
          SELECT b.*, LEAST(
            GREATEST((b.check_in_date + 1 + CAST(:cutoff AS time)) AT TIME ZONE :zone,
              COALESCE(b.arrival_hold_until, (b.check_in_date + 1 + CAST(:cutoff AS time)) AT TIME ZONE :zone)),
            (b.check_out_date + CAST(:checkout AS time)) AT TIME ZONE :zone) AS deadline,
            CASE WHEN b.status IN ('CHECKED_IN','CHECKED_OUT') THEN b.checked_in_at
              WHEN b.status='NO_SHOW' THEN b.no_show_at
              WHEN b.status='CANCELLED' THEN b.cancelled_at AT TIME ZONE 'UTC' END AS resolved_at,
            EXISTS (SELECT 1 FROM booking_change_requests c WHERE c.status='PENDING'
              AND (c.booking_id=b.id OR c.scope_key='ORDER:' || CAST(b.order_id AS text))) AS change_pending
          FROM bookings b
          WHERE (b.status='CONFIRMED' AND b.checked_in_at IS NULL)
             OR b.status IN ('CHECKED_IN','CHECKED_OUT','NO_SHOW','CANCELLED')
        ), reasons AS (
          SELECT r.*, CASE WHEN status <> 'CONFIRMED' OR checked_in_at IS NOT NULL THEN ARRAY[]::text[] ELSE
            array_remove(ARRAY[
              CASE WHEN :now >= deadline THEN 'RECEPTION_EXPIRED' END,
              CASE WHEN arrival_hold_requested_until IS NOT NULL AND arrival_hold_requested_until IS DISTINCT FROM arrival_hold_until THEN 'HOLD_REQUEST' END,
              CASE WHEN change_pending THEN 'CHANGE_REQUEST' END,
              CASE WHEN expected_arrival_at >= deadline THEN 'ETA_AFTER_DEADLINE' END,
              CASE WHEN :now > expected_arrival_at THEN 'PAST_ETA' END
            ], NULL) END AS reasons
          FROM reception r
          WHERE (status <> 'CONFIRMED' AND resolved_at >= :start AND resolved_at < :end)
            OR (status='CONFIRMED' AND (check_in_date <= :today OR change_pending
              OR (arrival_hold_requested_until IS NOT NULL AND arrival_hold_requested_until IS DISTINCT FROM arrival_hold_until)))
        ), work AS (
          SELECT r.*, CASE WHEN status <> 'CONFIRMED' OR checked_in_at IS NOT NULL THEN 'RESOLVED'
            WHEN cardinality(reasons)>0 THEN 'ACTION_REQUIRED' ELSE 'WAITING' END AS work_group
          FROM reasons r
        )
        """;
    private static final String FILTER = " WHERE work_group=:group AND (:reason='ALL' OR :reason=ANY(reasons)) ";

    public Page<UUID> find(
            ArrivalProgress.Group group,
            ArrivalProgress.Reason reason,
            int page,
            int size,
            Instant now,
            LocalDate resolvedFrom,
            LocalDate resolvedTo) {
        var today = now.atZone(policy.getProperties().zoneId()).toLocalDate();
        var params = new MapSqlParameterSource()
                .addValue(
                        "cutoff", policy.getProperties().getLateArrivalCutoff().toString())
                .addValue("checkout", policy.getProperties().getCheckOutTime().toString())
                .addValue("zone", policy.getProperties().getZone())
                .addValue("today", today)
                .addValue("now", now.atOffset(java.time.ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue(
                        "start",
                        resolvedFrom
                                .atStartOfDay(policy.getProperties().zoneId())
                                .toOffsetDateTime(),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue(
                        "end",
                        resolvedTo
                                .plusDays(1)
                                .atStartOfDay(policy.getProperties().zoneId())
                                .toOffsetDateTime(),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("group", group.name())
                .addValue("reason", reason == null ? "ALL" : reason.name())
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        long total = Objects.requireNonNull(
                jdbc.queryForObject(WORK + "SELECT COUNT(*) FROM work" + FILTER, params, Long.class));
        var ids = jdbc.query(
                WORK + "SELECT id FROM work" + FILTER
                        + """
            ORDER BY CASE WHEN work_group='RESOLVED' THEN resolved_at END DESC NULLS LAST,
              CASE WHEN 'RECEPTION_EXPIRED'=ANY(reasons) THEN 0 WHEN 'HOLD_REQUEST'=ANY(reasons) OR 'CHANGE_REQUEST'=ANY(reasons) THEN 1 ELSE 2 END,
              COALESCE(expected_arrival_at,deadline), created_at, booking_code, id LIMIT :limit OFFSET :offset
            """,
                params,
                (rs, row) -> rs.getObject("id", UUID.class));
        return new PageImpl<>(ids, PageRequest.of(page, size), total);
    }
}
