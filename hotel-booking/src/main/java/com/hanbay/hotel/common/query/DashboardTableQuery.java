package com.hanbay.hotel.common.query;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.common.validation.PaginationValidator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Paginated read model for visible dashboard columns, including derived and JSON values. */
@Component
@RequiredArgsConstructor
public class DashboardTableQuery {
    private static final String VIETNAMESE_ACCENTS =
            "áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđ";
    private static final String ASCII_LETTERS =
            "a".repeat(17) + "e".repeat(11) + "i".repeat(5) + "o".repeat(17) + "u".repeat(11) + "y".repeat(5) + "d";
    private static final Set<String> VALUE_COLUMNS = Set.of(
            "basePrice",
            "maxGuests",
            "floor",
            "createdAt",
            "paidAt",
            "receivedAt",
            "requestedAt",
            "transferAmount",
            "amount",
            "attempts",
            "stay",
            "guests",
            "totalAmount",
            "paidAmount",
            "outstandingAmount");
    private final NamedParameterJdbcTemplate jdbc;
    private final HotelStayPolicy stayPolicy;

    public enum Table {
        ROOM_TYPES(
                """
                SELECT rt.id, lower(rt.code) AS "code", lower(rt.name) AS "name",
                       rt.base_price AS "basePrice", rt.max_guests AS "maxGuests",
                       CASE rt.status::text WHEN 'ACTIVE' THEN 'Đang kinh doanh'
                         ELSE 'Ngừng kinh doanh' END AS "status"
                FROM room_types rt
                """,
                Set.of("code", "name", "basePrice", "maxGuests", "status")),
        ROOMS(
                """
                SELECT r.id,
                       CASE WHEN r.room_number ~ '^[0-9]+$' THEN lpad(r.room_number, 30, '0')
                         ELSE lower(r.room_number) END AS "roomNumber",
                       lower(rt.name) AS "roomTypeName", r.floor AS "floor", lower(r.note) AS "note",
                       CASE r.status::text WHEN 'AVAILABLE' THEN 'Sẵn sàng' WHEN 'OCCUPIED' THEN 'Có khách'
                         WHEN 'DIRTY' THEN 'Cần dọn' WHEN 'CLEANING' THEN 'Đang dọn'
                         WHEN 'MAINTENANCE' THEN 'Bảo trì' ELSE r.status::text END AS "status"
                FROM rooms r JOIN room_types rt ON rt.id = r.room_type_id
                """,
                Set.of("roomNumber", "roomTypeName", "floor", "note", "status")),
        HOUSEKEEPING(
                """
                SELECT r.id,
                       CASE WHEN r.room_number ~ '^[0-9]+$' THEN lpad(r.room_number, 30, '0')
                         ELSE lower(r.room_number) END AS "roomNumber",
                       lower(rt.name) AS "roomTypeName", r.floor AS "floor", lower(r.note) AS "note",
                       CASE r.status::text WHEN 'DIRTY' THEN 'Chờ dọn' ELSE 'Đang dọn' END AS "status"
                FROM rooms r JOIN room_types rt ON rt.id = r.room_type_id
                WHERE r.status::text IN ('DIRTY', 'CLEANING')
                """,
                Set.of("roomNumber", "roomTypeName", "floor", "note", "status")),
        MAINTENANCE(
                """
                SELECT m.id,
                       CASE WHEN r.room_number ~ '^[0-9]+$' THEN lpad(r.room_number, 30, '0')
                         ELSE lower(r.room_number) END AS "roomNumber",
                       lower(m.title) AS "title", m.created_at AS "createdAt",
                       CASE m.status::text WHEN 'PENDING' THEN 'Chờ xử lý'
                         WHEN 'IN_PROGRESS' THEN 'Đang xử lý' ELSE 'Hoàn thành' END AS "status"
                FROM maintenance_requests m JOIN rooms r ON r.id = m.room_id
                """,
                Set.of("roomNumber", "title", "status", "createdAt")),
        DEPARTURES(
                """
                SELECT b.id, lower(b.booking_code) AS "bookingCode",
                       lower(coalesce((SELECT min(g.full_name) FROM booking_guests g
                         WHERE g.booking_id = b.id AND g.is_primary = true), 'Chưa cập nhật')) AS "guestName",
                       lower(rt.name) AS "roomTypeName", b.check_in_date AS "stay",
                       b.adult_count + b.child_count AS "guests",
                       CASE WHEN b.check_out_date <= :overdueThrough THEN 1 ELSE 0 END AS "status",
                       b.total_amount AS "totalAmount", b.paid_amount AS "paidAmount",
                       greatest(b.total_amount - b.paid_amount, 0) AS "outstandingAmount"
                FROM bookings b JOIN room_types rt ON rt.id = b.room_type_id
                WHERE b.status::text = 'CHECKED_IN'
                """,
                Set.of(
                        "bookingCode",
                        "guestName",
                        "roomTypeName",
                        "stay",
                        "guests",
                        "status",
                        "totalAmount",
                        "paidAmount",
                        "outstandingAmount")),
        PAYMENTS(
                """
                SELECT p.id, lower(b.booking_code) AS "bookingCode", lower(p.payment_code) AS "paymentCode",
                       p.amount AS "amount", p.created_at AS "createdAt", p.paid_at AS "paidAt",
                       CASE p.status::text WHEN 'PENDING' THEN 'Chờ thanh toán' WHEN 'SUCCESS' THEN 'Thành công'
                         WHEN 'FAILED' THEN 'Thất bại' WHEN 'CANCELLED' THEN 'Đã hủy' WHEN 'EXPIRED' THEN 'Hết hạn'
                         WHEN 'REFUNDED' THEN 'Đã hoàn tiền' ELSE 'Hoàn tiền một phần' END AS "status"
                FROM payments p JOIN bookings b ON b.id = p.booking_id
                WHERE (:status = '' OR p.status::text = :status)
                  AND (:keyword = '' OR lower(p.payment_code) LIKE '%' || lower(:keyword) || '%'
                       OR lower(b.booking_code) LIKE '%' || lower(:keyword) || '%')
                """,
                Set.of("bookingCode", "paymentCode", "amount", "status", "createdAt", "paidAt")),
        WEBHOOKS(
                """
                SELECT e.id,
                       CASE WHEN e.event_id ~ '^[0-9]+$' THEN lpad(e.event_id, 120, '0')
                         ELSE lower(e.event_id) END AS "eventId",
                       lower(e.payload ->> 'gateway') AS "gateway", lower(p.payment_code) AS "paymentCode",
                       CASE WHEN jsonb_typeof(e.payload -> 'transferAmount') = 'number'
                            THEN trunc((e.payload ->> 'transferAmount')::numeric)
                            WHEN (e.payload ->> 'transferAmount') ~ '^[+-]?[0-9]{1,19}$'
                            THEN CASE WHEN (e.payload ->> 'transferAmount')::numeric
                                           BETWEEN -9223372036854775808 AND 9223372036854775807
                                      THEN (e.payload ->> 'transferAmount')::numeric ELSE 0 END
                            ELSE 0 END AS "transferAmount",
                       e.processing_attempts AS "attempts", e.received_at AS "receivedAt",
                       CASE e.processing_status::text WHEN 'RECEIVED' THEN 'Đã tiếp nhận'
                         WHEN 'PROCESSING' THEN 'Đang xử lý' WHEN 'PROCESSED' THEN 'Đã xử lý'
                         WHEN 'FAILED' THEN 'Thất bại' ELSE 'Bỏ qua' END AS "status"
                FROM payment_webhook_events e LEFT JOIN payments p ON p.id = e.payment_id
                WHERE (:status = '' OR e.processing_status::text = :status)
                  AND (:keyword = '' OR lower(e.event_id) LIKE '%' || lower(:keyword) || '%'
                       OR lower(coalesce(p.payment_code, '')) LIKE '%' || lower(:keyword) || '%')
                """,
                Set.of("eventId", "gateway", "paymentCode", "transferAmount", "status", "attempts", "receivedAt")),
        REFUNDS(
                """
                SELECT r.id, lower(b.booking_code) AS "bookingCode", lower(p.payment_code) AS "paymentCode",
                       r.amount AS "amount", lower(r.reason) AS "reason", r.requested_at AS "requestedAt",
                       r.status::text AS "status",
                       CASE WHEN r.bank_name IS NOT NULL AND r.account_number IS NOT NULL
                              AND r.account_holder_name IS NOT NULL
                         THEN lower(r.bank_name || ' ' || r.account_number || ' ' || r.account_holder_name)
                         ELSE NULL END AS "bankAccount"
                FROM payment_refunds r JOIN payments p ON p.id = r.payment_id
                JOIN bookings b ON b.id = p.booking_id WHERE r.status::text = 'PENDING'
                """,
                Set.of("bookingCode", "paymentCode", "amount", "bankAccount", "reason", "requestedAt", "status"));

        private final String sql;
        private final Set<String> fields;

        Table(String sql, Set<String> fields) {
            this.sql = sql;
            this.fields = fields;
        }
    }

    public <T> Page<T> findAll(
            Table table, String sort, int page, int size, Function<List<UUID>, List<T>> load, Function<T, UUID> getId) {
        return findAll(table, sort, page, size, "", "", load, getId);
    }

    public <T> Page<T> findAll(
            Table table,
            String sort,
            int page,
            int size,
            String keyword,
            String status,
            Function<List<UUID>, List<T>> load,
            Function<T, UUID> getId) {
        PaginationValidator.validate(page, size, 100);
        String orderBy = orderBy(table, sort);
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword == null ? "" : keyword.trim())
                .addValue("status", status == null ? "" : status)
                .addValue("overdueThrough", stayPolicy.overdueThrough())
                .addValue("size", size)
                .addValue("offset", (long) page * size);
        String source = " FROM (" + table.sql + ") AS visible_rows";
        Long total = jdbc.queryForObject("SELECT count(*)" + source, params, Long.class);
        List<UUID> ids = jdbc.query(
                "SELECT id" + source + " ORDER BY " + orderBy + " LIMIT :size OFFSET :offset",
                params,
                (row, index) -> row.getObject("id", UUID.class));
        Map<UUID, T> records = ids.isEmpty()
                ? Map.of()
                : load.apply(ids).stream().collect(Collectors.toMap(getId, Function.identity()));
        List<T> content =
                ids.stream().filter(records::containsKey).map(records::get).toList();
        return new PageImpl<>(content, PageRequest.of(page, size), total == null ? 0 : total);
    }

    static String orderBy(Table table, String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",", -1);
        if (parts.length != 2
                || !table.fields.contains(parts[0].trim())
                || !(parts[1].trim().equalsIgnoreCase("asc") || parts[1].trim().equalsIgnoreCase("desc"))) {
            throw new RequestValidationException(
                    "sort", "Sắp xếp không hợp lệ. Dùng tên cột được hỗ trợ và asc hoặc desc.");
        }
        // Only server-owned identifiers and a validated direction enter SQL; values remain bound parameters.
        String field = parts[0].trim();
        String expression = "\"" + field + "\"";
        if (!VALUE_COLUMNS.contains(field) && !(table == Table.DEPARTURES && field.equals("status"))) {
            // PostgreSQL's deployment collation may not be Vietnamese; keep text order accent/case insensitive.
            expression = "translate(lower(" + expression + "), '" + VIETNAMESE_ACCENTS + "', '" + ASCII_LETTERS + "')";
        }
        return expression + " " + parts[1].trim().toUpperCase(Locale.ROOT) + " NULLS LAST, id ASC";
    }
}
