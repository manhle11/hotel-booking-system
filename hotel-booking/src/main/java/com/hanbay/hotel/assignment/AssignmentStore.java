package com.hanbay.hotel.assignment;

import static com.hanbay.hotel.assignment.AssignmentModels.*;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
public class AssignmentStore {
    private final JdbcTemplate jdbc;
    private final HotelStayPolicy policy;
    private final ObjectMapper json;

    public Snapshot snapshot() {
        var now = policy.now().toInstant();
        LocalDate today = policy.today();
        List<PhysicalRoom> rooms = jdbc.query(
                """
                SELECT r.id, r.room_type_id, t.name, r.room_number, r.floor, r.status::text
                FROM rooms r JOIN room_types t ON t.id = r.room_type_id ORDER BY r.id
                """,
                (rs, n) -> new PhysicalRoom(
                        uuid(rs, "id"),
                        uuid(rs, "room_type_id"),
                        rs.getString("name"),
                        rs.getString("room_number"),
                        rs.getInt("floor"),
                        rs.getString("status")));
        List<Stay> stays = jdbc.query(
                """
                SELECT b.id, b.booking_code, b.room_type_id, b.check_in_date, b.check_out_date,
                       b.status::text, b.stay_revision, b.order_id, b.order_room_number, b.expired_at,
                       COALESCE(b.assigned_room_id, a.room_id) AS room_id,
                       CASE WHEN b.status = 'CHECKED_IN' THEN 'OCCUPIED' ELSE a.status END AS allocation_status,
                       COALESCE(g.full_name, '') AS guest_name
                FROM bookings b
                LEFT JOIN room_allocations a ON a.booking_id = b.id AND a.active
                LEFT JOIN LATERAL (SELECT full_name FROM booking_guests WHERE booking_id = b.id
                    AND is_primary = TRUE ORDER BY created_at, id LIMIT 1) g ON TRUE
                WHERE (b.status IN ('CONFIRMED', 'CHECKED_IN') OR (b.status = 'PENDING' AND b.expired_at > ?))
                  AND (b.check_out_date >= ? OR b.status = 'CHECKED_IN') ORDER BY b.id
                """,
                (rs, n) -> new Stay(
                        uuid(rs, "id"),
                        rs.getString("booking_code"),
                        uuid(rs, "room_type_id"),
                        rs.getObject("check_in_date", LocalDate.class),
                        rs.getObject("check_out_date", LocalDate.class),
                        rs.getString("status"),
                        rs.getInt("stay_revision"),
                        uuid(rs, "order_id"),
                        rs.getObject("order_room_number", Integer.class),
                        rs.getString("guest_name"),
                        rs.getTimestamp("expired_at") == null
                                ? null
                                : rs.getTimestamp("expired_at").toInstant(),
                        uuid(rs, "room_id"),
                        rs.getString("allocation_status")),
                java.sql.Timestamp.from(now),
                today);
        LocalDate horizon = stays.stream()
                .map(Stay::end)
                .reduce(today.plusDays(90), (a, b) -> a.isAfter(b) ? a : b)
                .plusDays(1);
        LocalDate overdue = policy.overdueThrough();
        stays = stays.stream()
                .map(b -> b.occupied() && !b.end().isAfter(overdue)
                        ? new Stay(
                                b.id(),
                                b.bookingCode(),
                                b.roomTypeId(),
                                b.start(),
                                horizon,
                                b.status(),
                                b.stayRevision(),
                                b.orderId(),
                                b.orderRoomNumber(),
                                b.guestName(),
                                b.expiresAt(),
                                b.roomId(),
                                b.allocationStatus(),
                                true,
                                b.end())
                        : b)
                .toList();
        String fingerprint = hash(today + "|" + overdue + "|" + rooms + "|" + stays);
        return new Snapshot(rooms, stays, today, overdue, now, fingerprint);
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void savePlan(Plan plan, UUID actorId) {
        jdbc.update(
                """
                INSERT INTO room_assignment_plans(id, room_type_id, payload, created_by, created_at, valid_until)
                VALUES (?, ?, ?::jsonb, ?, ?, ?)
                """,
                plan.id(),
                plan.roomTypeId(),
                json.writeValueAsString(plan),
                actorId,
                java.sql.Timestamp.from(plan.createdAt()),
                java.sql.Timestamp.from(plan.validUntil()));
    }

    public Plan plan(UUID id, boolean forUpdate) {
        List<Plan> result = jdbc.query(
                "SELECT payload::text, applied_at FROM room_assignment_plans WHERE id = ?"
                        + (forUpdate ? " FOR UPDATE" : ""),
                (rs, n) -> {
                    Plan plan = json.readValue(rs.getString(1), Plan.class);
                    return rs.getTimestamp(2) == null ? plan : plan.asApplied();
                },
                id);
        if (result.isEmpty())
            throw new AssignmentException("ASSIGNMENT_PLAN_NOT_FOUND", "Không tìm thấy phương án gán phòng.");
        return result.get(0);
    }

    public void markApplied(UUID id, UUID actor) {
        jdbc.update(
                "UPDATE room_assignment_plans SET applied_at = CURRENT_TIMESTAMP, applied_by = ? WHERE id = ?",
                actor,
                id);
    }

    public Plan latest(UUID typeId) {
        List<Plan> result = jdbc.query(
                """
                SELECT payload::text FROM room_assignment_plans WHERE room_type_id = ? AND applied_at IS NULL
                ORDER BY created_at DESC, id DESC LIMIT 1
                """,
                (rs, n) -> json.readValue(rs.getString(1), Plan.class),
                typeId);
        return result.isEmpty() ? null : result.get(0);
    }

    public void allocate(Stay stay, UUID roomId, String state) {
        jdbc.update(
                "UPDATE room_allocations SET active = FALSE, status = 'RELEASED', updated_at = CURRENT_TIMESTAMP WHERE booking_id = ? AND active",
                stay.id());
        if (roomId != null)
            jdbc.update(
                    """
                INSERT INTO room_allocations(booking_id, room_id, check_in, check_out, status) VALUES (?, ?, ?, ?, ?)
                """,
                    stay.id(),
                    roomId,
                    stay.start(),
                    stay.end(),
                    state);
    }

    public void history(UUID bookingId, UUID typeId, String action, UUID actorId, String reason, Object details) {
        jdbc.update(
                """
                INSERT INTO room_assignment_history(booking_id, room_type_id, action, actor_id, reason, details)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                bookingId,
                typeId,
                action,
                actorId,
                reason,
                json.writeValueAsString(details));
    }

    public List<History> history(UUID roomTypeId) {
        return jdbc.query(
                """
                SELECT h.*, u.full_name AS actor_name FROM room_assignment_history h LEFT JOIN users u ON u.id = h.actor_id
                WHERE h.room_type_id = ? ORDER BY h.created_at DESC, h.id DESC LIMIT 100
                """,
                (rs, n) -> new History(
                        uuid(rs, "id"),
                        uuid(rs, "booking_id"),
                        rs.getString("action"),
                        uuid(rs, "actor_id"),
                        rs.getString("actor_name"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("reason"),
                        rs.getString("details")),
                roomTypeId);
    }
}
