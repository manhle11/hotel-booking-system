-- HANBAY HOTEL: one-time, fictional but operationally consistent demo data.
-- Run with psql against hotelbooking_db. Existing rooms and SePay records are preserved.
-- Guests use reserved example.test addresses; DEMO payment references are not bank events.
BEGIN;

DO $$
BEGIN
    IF current_database() <> 'hotelbooking_db' THEN
        RAISE EXCEPTION 'Demo seed must run against hotelbooking_db, got %', current_database();
    END IF;
    IF (SELECT count(*) FROM room_types WHERE code IN
        ('SUPERIOR', 'DELUXE', 'JUNIOR_SUITE', 'HANBAY-OCEAN-SUITE')) <> 4 THEN
        RAISE EXCEPTION 'The four HANBAY room types must exist before seeding';
    END IF;
    IF (SELECT count(*) FROM rooms) <> 108 THEN
        RAISE EXCEPTION 'Expected the existing 108-room inventory; no data was changed';
    END IF;
END $$;

-- The fixed first booking UUID is the installation marker. Re-running is a no-op.
CREATE TEMP TABLE demo_run ON COMMIT DROP AS
SELECT NOT EXISTS (
    SELECT 1 FROM bookings WHERE id = md5('hanbay-demo-booking-01')::uuid
) AS enabled;

UPDATE room_types SET
    bed_type = CASE code
        WHEN 'SUPERIOR' THEN '1 giường King'
        WHEN 'DELUXE' THEN '1 giường King'
        WHEN 'JUNIOR_SUITE' THEN '1 giường King và 1 giường sofa'
        ELSE bed_type END,
    area_m2 = CASE code
        WHEN 'SUPERIOR' THEN 32
        WHEN 'DELUXE' THEN 42
        WHEN 'JUNIOR_SUITE' THEN 58
        ELSE area_m2 END,
    updated_at = localtimestamp
WHERE code IN ('SUPERIOR', 'DELUXE', 'JUNIOR_SUITE')
  AND EXISTS (SELECT 1 FROM demo_run WHERE enabled);

-- Public rate plans: one weekend supplement per category, plus Vietnam holidays.
INSERT INTO holiday_calendar
    (id, reference_id, name, start_date, end_date, active, created_at)
SELECT md5('hanbay-demo-holiday-' || h.code)::uuid,
       md5('hanbay-demo-holiday-ref-' || h.code)::uuid,
       h.name, h.day, h.day, true, localtimestamp
FROM (VALUES
    ('NEW_YEAR_2027', 'Tết Dương lịch 2027', DATE '2027-01-01'),
    ('REUNIFICATION_2027', 'Ngày Giải phóng miền Nam 2027', DATE '2027-04-30'),
    ('LABOUR_2027', 'Ngày Quốc tế Lao động 2027', DATE '2027-05-01'),
    ('NATIONAL_DAY_2027', 'Quốc khánh Việt Nam 2027', DATE '2027-09-02')
) AS h(code, name, day)
JOIN demo_run d ON d.enabled;

INSERT INTO price_rules
    (id, reference_id, room_type_id, name, rule_type, adjustment_type,
     adjustment_value, adjustment_direction, start_date, end_date,
     priority, active, created_at)
SELECT md5('hanbay-demo-rate-' || r.kind || '-' || t.code)::uuid,
       md5('hanbay-demo-rate-ref-' || r.kind || '-' || t.code)::uuid,
       t.id,
       r.name || ' - ' || t.name,
       r.kind::price_rule_type, 'PERCENTAGE'::price_rule_adjustment_type,
       r.percent, 'INCREASE'::price_rule_adjustment_direction,
       CURRENT_DATE, CURRENT_DATE + 365, r.priority, true, localtimestamp
FROM room_types t
CROSS JOIN (VALUES
    ('WEEKEND', 'Phụ thu cuối tuần', 10, 10),
    ('HOLIDAY', 'Phụ thu ngày lễ', 20, 20)
) AS r(kind, name, percent, priority)
JOIN demo_run d ON d.enabled
WHERE t.code IN ('SUPERIOR', 'DELUXE', 'JUNIOR_SUITE', 'HANBAY-OCEAN-SUITE');

-- Stay dates are relative to the day the script is installed. Room numbers are
-- the actual physical inventory; only checked-in/out stays have an assignment.
CREATE TEMP TABLE demo_plan ON COMMIT DROP AS
SELECT p.n,
       md5('hanbay-demo-booking-' || lpad(p.n::text, 2, '0'))::uuid AS id,
       'BK' || to_char((localtimestamp - make_interval(days => p.booked_days_ago))::date,
                       'YYYYMMDD') || lpad((200 + p.n)::text, 6, '0') AS booking_code,
       t.id AS room_type_id, t.base_price,
       CURRENT_DATE + p.start_offset AS check_in_date,
       CURRENT_DATE + p.start_offset + p.nights AS check_out_date,
       p.nights, p.status::booking_status AS status,
       r.id AS assigned_room_id, p.room_number,
       p.adults, p.children, p.guest_name,
       'demo' || lpad(p.n::text, 2, '0') || '@example.test' AS guest_email,
       p.add_on_code, p.add_on_quantity,
       (localtimestamp - make_interval(days => p.booked_days_ago))::timestamp AS booked_at
FROM (VALUES
    ( 1, 'SUPERIOR',            0, 2, 'CHECKED_IN',  '301',  2, 0, 'Nguyễn Minh Anh',    'BREAKFAST_BUFFET', 2, 3),
    ( 2, 'DELUXE',              0, 3, 'CHECKED_IN',  '701',  2, 1, 'Trần Gia Bảo',       'AIRPORT_PICKUP',   1, 4),
    ( 3, 'JUNIOR_SUITE',       -1, 3, 'CHECKED_IN',  '1101', 2, 2, 'Lê Khánh Linh',      'BREAKFAST_BUFFET', 4, 7),
    ( 4, 'HANBAY-OCEAN-SUITE',  0, 2, 'CHECKED_IN',  '1301', 2, 0, 'Phạm Hải Nam',       'SPA_60_MINUTES',   1, 6),
    ( 5, 'SUPERIOR',           -1, 3, 'CHECKED_IN',  '302',  1, 0, 'Võ Thanh Hà',        'LAUNDRY',          1, 5),
    ( 6, 'SUPERIOR',           -3, 2, 'CHECKED_OUT', '303',  2, 0, 'Đặng Thảo Nguyên',   'BREAKFAST_BUFFET', 2, 10),
    ( 7, 'DELUXE',             -2, 2, 'CHECKED_OUT', '704',  2, 1, 'Bùi Hoàng Phúc',     'DA_NANG_CITY_TOUR',3, 9),
    ( 8, 'JUNIOR_SUITE',       -4, 2, 'CHECKED_OUT', '1102', 2, 1, 'Đỗ Minh Châu',       'AIRPORT_DROPOFF',  1, 11),
    ( 9, 'HANBAY-OCEAN-SUITE', -3, 2, 'CHECKED_OUT', '1302', 2, 0, 'Nguyễn Quốc Việt',   'SPA_60_MINUTES',   2, 12),
    (10, 'SUPERIOR',            0, 1, 'CONFIRMED',   NULL,   2, 0, 'Hoàng Thu Trang',    NULL,               0, 2),
    (11, 'DELUXE',              0, 2, 'CONFIRMED',   NULL,   2, 2, 'Phan Đức Huy',      'BREAKFAST_BUFFET', 4, 3),
    (12, 'JUNIOR_SUITE',        1, 2, 'CONFIRMED',   NULL,   3, 1, 'Nguyễn Bảo Trâm',   NULL,               0, 5),
    (13, 'HANBAY-OCEAN-SUITE',  2, 2, 'CONFIRMED',   NULL,   2, 0, 'Trịnh Thành Đạt',   'AIRPORT_PICKUP',   1, 4),
    (14, 'SUPERIOR',            3, 2, 'CONFIRMED',   NULL,   2, 1, 'Lâm Ngọc Diệp',     NULL,               0, 3),
    (15, 'DELUXE',              4, 3, 'CONFIRMED',   NULL,   2, 0, 'Dương Quốc Khánh',  'HOI_AN_SHUTTLE',  2, 4),
    (16, 'SUPERIOR',            5, 2, 'CONFIRMED',   NULL,   1, 0, 'Mai Phương Thảo',   NULL,               0, 2),
    (17, 'JUNIOR_SUITE',        6, 2, 'CONFIRMED',   NULL,   2, 2, 'Đinh Hải Yến',      'BREAKFAST_BUFFET', 4, 6),
    (18, 'DELUXE',              7, 3, 'CONFIRMED',   NULL,   2, 1, 'Hồ Quang Vinh',     NULL,               0, 5),
    (19, 'HANBAY-OCEAN-SUITE',  8, 2, 'CONFIRMED',   NULL,   2, 0, 'Tạ Ngọc Bích',      'SPA_60_MINUTES',   2, 4),
    (20, 'SUPERIOR',           10, 2, 'CONFIRMED',   NULL,   2, 0, 'Vũ Minh Quân',      NULL,               0, 3),
    (21, 'DELUXE',              2, 2, 'CANCELLED',   NULL,   2, 0, 'Ngô Thiên Kim',     NULL,               0, 5),
    (22, 'JUNIOR_SUITE',        4, 2, 'CANCELLED',   NULL,   2, 1, 'Lý Gia Hân',        NULL,               0, 3),
    (23, 'SUPERIOR',            3, 1, 'EXPIRED',     NULL,   2, 0, 'Phùng Anh Khoa',    NULL,               0, 2)
) AS p(n, type_code, start_offset, nights, status, room_number,
       adults, children, guest_name, add_on_code, add_on_quantity, booked_days_ago)
JOIN room_types t ON t.code = p.type_code
LEFT JOIN rooms r ON r.room_number = p.room_number AND r.room_type_id = t.id
JOIN demo_run d ON d.enabled;

DO $$
BEGIN
    IF (SELECT enabled FROM demo_run) THEN
        IF (SELECT count(*) FROM demo_plan) <> 23
           OR EXISTS (SELECT 1 FROM demo_plan WHERE room_number IS NOT NULL AND assigned_room_id IS NULL) THEN
            RAISE EXCEPTION 'Invalid demo room plan';
        END IF;
        IF EXISTS (
            SELECT 1 FROM demo_plan p JOIN rooms r ON r.id = p.assigned_room_id
            WHERE r.status <> 'AVAILABLE'
        ) THEN
            RAISE EXCEPTION 'A planned demo room is no longer AVAILABLE';
        END IF;
    END IF;
END $$;

CREATE TEMP TABLE demo_nights ON COMMIT DROP AS
SELECT p.id AS booking_id,
       (p.check_in_date + gs.day_offset)::date AS stay_date,
       p.base_price + CASE
           WHEN p.check_in_date + gs.day_offset >= CURRENT_DATE
                AND extract(dow FROM p.check_in_date + gs.day_offset) IN (0, 6)
           THEN p.base_price / 10 ELSE 0 END AS price_per_night,
       CASE WHEN p.check_in_date + gs.day_offset >= CURRENT_DATE
                 AND extract(dow FROM p.check_in_date + gs.day_offset) IN (0, 6)
            THEN md5('hanbay-demo-rate-WEEKEND-' || t.code)::uuid
            ELSE NULL END AS price_rule_id,
       p.booked_at
FROM demo_plan p
JOIN room_types t ON t.id = p.room_type_id
CROSS JOIN LATERAL generate_series(0, p.nights - 1) AS gs(day_offset);

CREATE TEMP TABLE demo_totals ON COMMIT DROP AS
SELECT p.id,
       sum(n.price_per_night)::bigint
       + coalesce(a.price * p.add_on_quantity, 0) AS total_amount
FROM demo_plan p
JOIN demo_nights n ON n.booking_id = p.id
LEFT JOIN add_on_services a ON a.code = p.add_on_code
GROUP BY p.id, a.price, p.add_on_quantity;

INSERT INTO bookings
    (id, booking_code, room_type_id, assigned_room_id, check_in_date,
     check_out_date, adult_count, child_count, status, total_amount,
     paid_amount, expired_at, cancelled_at, created_at, updated_at)
SELECT p.id, p.booking_code, p.room_type_id, p.assigned_room_id,
       p.check_in_date, p.check_out_date, p.adults, p.children, p.status,
       x.total_amount,
       CASE WHEN p.status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT')
            THEN x.total_amount ELSE 0 END,
       CASE WHEN p.status = 'EXPIRED' THEN p.booked_at + interval '15 minutes' END,
       CASE WHEN p.status = 'CANCELLED' THEN p.booked_at + interval '12 minutes' END,
       p.booked_at, localtimestamp
FROM demo_plan p JOIN demo_totals x ON x.id = p.id;

INSERT INTO booking_guests
    (id, reference_id, booking_id, full_name, email, identity_number,
     is_primary, created_at)
SELECT md5('hanbay-demo-guest-' || p.n)::uuid,
       md5('hanbay-demo-guest-ref-' || p.n)::uuid,
       p.id, p.guest_name, p.guest_email, NULL, true, p.booked_at
FROM demo_plan p;

INSERT INTO booking_nights
    (id, reference_id, booking_id, stay_date, price_per_night, created_at)
SELECT md5('hanbay-demo-night-' || n.booking_id || '-' || n.stay_date)::uuid,
       md5('hanbay-demo-night-ref-' || n.booking_id || '-' || n.stay_date)::uuid,
       n.booking_id, n.stay_date, n.price_per_night, n.booked_at
FROM demo_nights n;

INSERT INTO booking_night_price_rules
    (id, booking_night_id, price_rule_id, adjustment_amount, created_at)
SELECT md5('hanbay-demo-night-rule-' || n.booking_id || '-' || n.stay_date)::uuid,
       bn.id, n.price_rule_id, p.base_price / 10, p.booked_at
FROM demo_nights n
JOIN demo_plan p ON p.id = n.booking_id
JOIN booking_nights bn ON bn.booking_id = n.booking_id AND bn.stay_date = n.stay_date
WHERE n.price_rule_id IS NOT NULL;

INSERT INTO booking_add_ons
    (id, booking_id, add_on_service_id, quantity, unit_price, total_price, created_at)
SELECT md5('hanbay-demo-addon-' || p.n)::uuid,
       p.id, a.id, p.add_on_quantity, a.price,
       p.add_on_quantity * a.price, p.booked_at
FROM demo_plan p
JOIN add_on_services a ON a.code = p.add_on_code;

INSERT INTO booking_status_histories
    (id, reference_id, booking_id, old_status, new_status, changed_by,
     reason, created_at)
SELECT md5('hanbay-demo-history-' || p.n || '-' || h.step)::uuid,
       md5('hanbay-demo-history-ref-' || p.n || '-' || h.step)::uuid,
       p.id, h.old_status::booking_status, h.new_status::booking_status,
       CASE WHEN h.step >= 3 THEN receptionist.id ELSE NULL END,
       'Dữ liệu demo: ' || h.reason, h.changed_at
FROM demo_plan p
LEFT JOIN users receptionist ON receptionist.email = 'receptionist@hanbay.com'
CROSS JOIN LATERAL (
    SELECT 1 AS step, NULL::text AS old_status, 'PENDING'::text AS new_status,
           'tạo đặt phòng'::text AS reason, p.booked_at AS changed_at
    UNION ALL SELECT 2, 'PENDING', 'CONFIRMED', 'đã thanh toán đủ',
           p.booked_at + interval '10 minutes'
    WHERE p.status IN ('CONFIRMED','CHECKED_IN','CHECKED_OUT')
    UNION ALL SELECT 2, 'PENDING', 'CANCELLED', 'khách hủy khi chưa thanh toán',
           p.booked_at + interval '12 minutes'
    WHERE p.status = 'CANCELLED'
    UNION ALL SELECT 2, 'PENDING', 'EXPIRED', 'quá hạn giữ phòng 15 phút',
           p.booked_at + interval '15 minutes'
    WHERE p.status = 'EXPIRED'
    UNION ALL SELECT 3, 'CONFIRMED', 'CHECKED_IN', 'lễ tân nhận phòng',
           p.check_in_date + time '14:00'
    WHERE p.status IN ('CHECKED_IN','CHECKED_OUT')
    UNION ALL SELECT 4, 'CHECKED_IN', 'CHECKED_OUT', 'lễ tân trả phòng',
           p.check_out_date + time '11:00'
    WHERE p.status = 'CHECKED_OUT'
) h;

-- The synthetic payments are already settled. They have no SePay webhook event
-- and are clearly marked DEMO in both code and transaction metadata.
INSERT INTO payments
    (id, booking_id, payment_code, amount, status, currency,
     idempotency_key, paid_at, expires_at, created_at, updated_at)
SELECT md5('hanbay-demo-payment-' || p.n)::uuid,
       p.id, 'DEMO-PAY-' || to_char(p.booked_at, 'YYYYMMDD') || '-'
             || lpad((200 + p.n)::text, 6, '0'),
       x.total_amount, 'SUCCESS', 'VND',
       'hanbay-demo-payment-' || p.n,
       p.booked_at + interval '10 minutes',
       p.booked_at + interval '15 minutes',
       p.booked_at + interval '2 minutes',
       p.booked_at + interval '10 minutes'
FROM demo_plan p JOIN demo_totals x ON x.id = p.id
WHERE p.status IN ('CONFIRMED','CHECKED_IN','CHECKED_OUT');

INSERT INTO payment_transactions
    (id, payment_id, provider_transaction_id, transaction_ref,
     type, status, amount, currency, provider_response_code,
     provider_message, raw_payload, occurred_at, received_at, created_at)
SELECT md5('hanbay-demo-payment-transaction-' || p.n)::uuid,
       md5('hanbay-demo-payment-' || p.n)::uuid,
       NULL, 'DEMO-TXN-' || lpad(p.n::text, 3, '0'),
       'PAYMENT', 'SUCCESS', x.total_amount, 'VND',
       'DEMO', 'Giao dịch mô phỏng để trình diễn, không phải chuyển khoản thật',
       jsonb_build_object('demo', true, 'source', 'seed-five-star-demo.sql'),
       p.booked_at + interval '10 minutes',
       p.booked_at + interval '10 minutes',
       p.booked_at + interval '10 minutes'
FROM demo_plan p JOIN demo_totals x ON x.id = p.id
WHERE p.status IN ('CONFIRMED','CHECKED_IN','CHECKED_OUT');

UPDATE rooms r SET status = 'OCCUPIED', updated_at = localtimestamp
FROM demo_plan p
WHERE p.assigned_room_id = r.id AND p.status = 'CHECKED_IN';

UPDATE rooms r SET
    status = CASE r.room_number WHEN '303' THEN 'DIRTY'::room_status
                                WHEN '704' THEN 'CLEANING'::room_status
                                ELSE 'AVAILABLE'::room_status END,
    updated_at = localtimestamp
FROM demo_plan p
WHERE p.assigned_room_id = r.id AND p.status = 'CHECKED_OUT';

INSERT INTO maintenance_requests
    (id, room_id, title, description, status, created_by,
     created_at, updated_at, started_at, completed_at)
SELECT md5('hanbay-demo-maintenance-' || m.room_number)::uuid,
       r.id, m.title, m.description, m.status::maintenance_request_status,
       manager.id,
       current_timestamp - make_interval(days => m.days_ago),
       current_timestamp - make_interval(days => m.days_ago),
       CASE WHEN m.status <> 'PENDING'
            THEN current_timestamp - make_interval(days => m.days_ago) + interval '2 hours' END,
       CASE WHEN m.status = 'COMPLETED'
            THEN current_timestamp - make_interval(days => m.days_ago) + interval '6 hours' END
FROM (VALUES
    ('504', 'Kiểm tra điều hòa phòng 504',
     'Điều hòa làm mát không ổn định; kỹ thuật kiểm tra và vệ sinh dàn lạnh.',
     'IN_PROGRESS', 1),
    ('805', 'Thay khóa cửa điện tử phòng 805',
     'Khóa cửa báo pin yếu và cần kiểm tra bộ đọc thẻ trước khi bán phòng.',
     'PENDING', 0),
    ('305', 'Thay vòi sen phòng 305',
     'Đã thay vòi sen và kiểm tra áp lực nước trước khi trả phòng vào kho bán.',
     'COMPLETED', 4)
) AS m(room_number, title, description, status, days_ago)
JOIN rooms r ON r.room_number = m.room_number
JOIN users manager ON manager.email = 'manager@hanbay.com'
JOIN demo_run d ON d.enabled;

UPDATE rooms r SET status = 'MAINTENANCE',
                   note = 'Đang xử lý phiếu bảo trì demo',
                   updated_at = localtimestamp
FROM maintenance_requests m
JOIN demo_run d ON d.enabled
WHERE m.room_id = r.id AND m.status IN ('PENDING', 'IN_PROGRESS')
  AND m.id IN (md5('hanbay-demo-maintenance-504')::uuid,
               md5('hanbay-demo-maintenance-805')::uuid);

INSERT INTO maintenance_costs
    (request_id, amount, expense_date, note, revision, recorded_by, recorded_at)
SELECT m.id, 280000, CURRENT_DATE - 4,
       'Vật tư thay vòi sen và gioăng chống rò (demo)', 1,
       manager.id, current_timestamp - interval '4 days' + interval '6 hours'
FROM maintenance_requests m
JOIN users manager ON manager.email = 'manager@hanbay.com'
JOIN demo_run d ON d.enabled
WHERE m.id = md5('hanbay-demo-maintenance-305')::uuid;

-- Reserve the seeded code suffixes while keeping the existing sequence ahead.
SELECT setval('booking_code_seq',
              greatest((SELECT last_value FROM booking_code_seq), 1000), true)
WHERE EXISTS (SELECT 1 FROM demo_run WHERE enabled);

COMMIT;
