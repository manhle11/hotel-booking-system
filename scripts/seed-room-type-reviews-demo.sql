-- Local, fictional fixtures for room-type reviews. Run after Flyway migrations.
-- Three accounts share the demo password ReviewDemo@123 (BCrypt below).
-- One transaction; a second run preserves all manual review/reply/deletion changes.
-- Re-running also repairs the three legacy demo account IDs without resetting their data.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
SELECT pg_advisory_xact_lock(hashtext('hanbay-review-demo-v1'));

DO $$
BEGIN
    IF current_database() <> 'hotelbooking_db' THEN
        RAISE EXCEPTION 'Use the local hotelbooking_db database, got %', current_database();
    END IF;
    IF to_regclass('public.room_type_reviews') IS NULL
       OR EXISTS (SELECT 1 FROM information_schema.tables
                  WHERE table_schema = 'public' AND table_name = 'review_access_challenges') THEN
        RAISE EXCEPTION 'Start the updated backend first to apply all review migrations';
    END IF;
END $$;

CREATE TEMP TABLE review_demo_run ON COMMIT DROP AS
SELECT NOT EXISTS (SELECT 1 FROM bookings WHERE booking_code = 'RV-DEMO-01') AS enabled;

-- UUID v5 (URL namespace); strict frontend UUID validation accepts these identities.
CREATE OR REPLACE FUNCTION pg_temp.review_demo_user_id(user_key text) RETURNS uuid
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE user_key
        WHEN 'customer' THEN '2b513a5a-8e19-5c7c-84c7-cd13df6cd541'::uuid
        WHEN 'other' THEN '18a1fe26-902a-5663-b6ef-b4e9a08d750a'::uuid
        WHEN 'manager' THEN '4c7b258b-5794-5279-9b26-0d5cf65ac636'::uuid
    END
$$;

DO $$
BEGIN
    IF NOT (SELECT enabled FROM review_demo_run) THEN
        RAISE NOTICE 'Review demo already installed; existing test results are preserved';
    ELSE
        IF (SELECT count(*) FROM room_types WHERE status = 'ACTIVE'
            AND code IN ('DELUXE_TWIN', 'DELUXE_KING')) <> 2 THEN
            RAISE EXCEPTION 'Active DELUXE_TWIN and DELUXE_KING room types are required';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM roles WHERE code = 'MANAGER') THEN
            RAISE EXCEPTION 'MANAGER role is missing';
        END IF;
        IF EXISTS (SELECT 1 FROM users WHERE email IN
            ('review.customer@example.test', 'review.other@example.test', 'review.manager@example.test')
            OR id IN (md5('hanbay-review-demo-user-customer')::uuid,
                      md5('hanbay-review-demo-user-other')::uuid,
                      md5('hanbay-review-demo-user-manager')::uuid,
                      pg_temp.review_demo_user_id('customer'),
                      pg_temp.review_demo_user_id('other'),
                      pg_temp.review_demo_user_id('manager'))) THEN
            RAISE EXCEPTION 'A demo identity already exists without the installation marker; no accounts changed';
        END IF;
    END IF;
END $$;

-- Repair only a previously installed demo and only identities with the expected demo email.
-- Copy the identity before moving foreign keys: no FK is disabled, and all work rolls back on error.
DO $$
DECLARE
    demo_key text;
    legacy_id uuid;
    replacement_id uuid;
    legacy_user public.users%ROWTYPE;
    dependency record;
BEGIN
    IF (SELECT enabled FROM review_demo_run) THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE contype = 'f' AND confrelid = 'public.users'::regclass
                 AND (cardinality(conkey) <> 1 OR cardinality(confkey) <> 1)) THEN
        RAISE EXCEPTION 'Unsupported composite user foreign key; no demo identities changed';
    END IF;

    FOREACH demo_key IN ARRAY ARRAY['customer', 'other', 'manager'] LOOP
        legacy_id := md5('hanbay-review-demo-user-' || demo_key)::uuid;
        replacement_id := pg_temp.review_demo_user_id(demo_key);
        SELECT * INTO legacy_user FROM public.users WHERE id = legacy_id FOR UPDATE;
        IF NOT FOUND THEN
            CONTINUE;
        END IF;
        IF legacy_user.email IS DISTINCT FROM 'review.' || demo_key || '@example.test' THEN
            RAISE EXCEPTION 'Unexpected legacy demo identity for %; no accounts changed', demo_key;
        END IF;
        IF EXISTS (SELECT 1 FROM public.users WHERE id = replacement_id) THEN
            RAISE EXCEPTION 'Replacement demo UUID already exists for %; no accounts changed', demo_key;
        END IF;

        INSERT INTO public.users
            (id, password_hash, full_name, avatar_url, status, created_at, updated_at)
        VALUES (replacement_id, legacy_user.password_hash, legacy_user.full_name,
                legacy_user.avatar_url, legacy_user.status, legacy_user.created_at, legacy_user.updated_at);

        FOR dependency IN
            SELECT ns.nspname AS schema_name, child.relname AS table_name, col.attname AS column_name
            FROM pg_constraint fk
            JOIN pg_class child ON child.oid = fk.conrelid
            JOIN pg_namespace ns ON ns.oid = child.relnamespace
            JOIN pg_attribute col ON col.attrelid = child.oid AND col.attnum = fk.conkey[1]
            WHERE fk.contype = 'f' AND fk.confrelid = 'public.users'::regclass
        LOOP
            EXECUTE format('UPDATE %I.%I SET %I = $1 WHERE %I = $2',
                           dependency.schema_name, dependency.table_name,
                           dependency.column_name, dependency.column_name)
            USING replacement_id, legacy_id;
        END LOOP;

        DELETE FROM public.users WHERE id = legacy_id;
        UPDATE public.users SET email = legacy_user.email, phone = legacy_user.phone
        WHERE id = replacement_id;
        RAISE NOTICE 'Repaired UUID for demo account review.%@example.test', demo_key;
    END LOOP;
END $$;

INSERT INTO users (id, email, password_hash, full_name, status, created_at, updated_at)
SELECT pg_temp.review_demo_user_id(u.key),
       'review.' || u.key || '@example.test',
       '$2y$10$5nTl/spIP15Qb1auIWdnw.aDpWcuF/3MDk0t.ST93gi0lQzk5z.Oe',
       u.name, 'ACTIVE', current_timestamp, current_timestamp
FROM (VALUES ('customer', 'Nguyễn Minh An'),
             ('other', 'Trần Bảo Linh'),
             ('manager', 'Quản lý đánh giá mẫu')) AS u(key, name)
JOIN review_demo_run d ON d.enabled;

-- Customers have no staff role; the manager has exactly one role.
INSERT INTO user_roles (user_id, role_id)
SELECT pg_temp.review_demo_user_id('manager'), r.id
FROM roles r JOIN review_demo_run d ON d.enabled WHERE r.code = 'MANAGER';

-- Offsets are relative to the first installation day. Booking 21 returned early.
-- Bookings 17/18/19 form one order: two Twins and one King.
CREATE TEMP TABLE review_demo_plan ON COMMIT DROP AS
SELECT p.*, md5('hanbay-review-demo-booking-' || p.n)::uuid AS booking_id,
       md5('hanbay-review-demo-order-' || p.order_n)::uuid AS order_id,
       CASE WHEN p.owner_key IS NULL THEN NULL
            ELSE pg_temp.review_demo_user_id(p.owner_key) END AS owner_id,
       t.id AS room_type_id, t.base_price,
       current_date + p.start_offset AS check_in_date,
       current_date + p.end_offset AS check_out_date,
       CASE WHEN p.state IN ('CHECKED_IN', 'CHECKED_OUT')
            THEN (current_date + p.start_offset + time '14:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END AS checked_in_at,
       CASE WHEN p.state = 'CHECKED_OUT'
            THEN (current_date + p.actual_end_offset + time '11:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' END AS checked_out_at,
       (current_date + p.start_offset - 7 + time '09:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS booked_at
FROM (VALUES
    -- n, order, room number, type, owner, status, arrival, planned departure, actual departure
    ( 1,  1, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT', -4, -2, -2),
    ( 2,  2, 1, 'DELUXE_KING', 'customer', 'CHECKED_OUT', -3, -1, -1),
    ( 3,  3, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_IN',  -1,  2, NULL),
    ( 4,  4, 1, 'DELUXE_KING', 'customer', 'CONFIRMED',    5,  7, NULL),
    ( 5,  5, 1, 'DELUXE_TWIN', 'customer', 'CANCELLED',    6,  8, NULL),
    ( 6,  6, 1, 'DELUXE_TWIN', 'customer', 'EXPIRED',      7,  9, NULL),
    ( 7,  7, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT', -34,-32,-32),
    ( 8,  8, 1, 'DELUXE_TWIN', NULL,       'CHECKED_OUT', -5, -3, -3),
    ( 9,  9, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT', -8, -6, -6),
    (10, 10, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT', -9, -7, -7),
    (11, 11, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT',-10, -8, -8),
    (12, 12, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT',-11, -9, -9),
    (13, 13, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT',-12,-10,-10),
    (14, 14, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT',-13,-11,-11),
    (15, 15, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT',-14,-12,-12),
    (16, 16, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT',-15,-13,-13),
    (17, 17, 1, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT', -7, -5, -5),
    (18, 17, 2, 'DELUXE_TWIN', 'customer', 'CHECKED_OUT', -7, -5, -5),
    (19, 17, 3, 'DELUXE_KING', 'customer', 'CHECKED_OUT', -7, -5, -5),
    (20, 20, 1, 'DELUXE_TWIN', 'other',    'CHECKED_OUT', -6, -4, -4),
    (21, 21, 1, 'DELUXE_KING', 'customer', 'CHECKED_OUT', -5, -1, -3)
) AS p(n, order_n, room_n, type_code, owner_key, state, start_offset, end_offset, actual_end_offset)
JOIN room_types t ON t.code = p.type_code
JOIN review_demo_run d ON d.enabled;

INSERT INTO booking_orders
    (id, order_code, booked_by, contact_name, contact_email, check_in_date, check_out_date,
     status, total_amount, access_token_hash, idempotency_key, request_hash, expires_at, cancelled_at,
     created_at, updated_at)
SELECT p.order_id, 'RV-ORDER-' || lpad(p.order_n::text, 2, '0'), p.owner_id,
       CASE WHEN p.owner_key = 'other' THEN 'Trần Bảo Linh' ELSE 'Nguyễn Minh An' END,
       -- Guest order deliberately has the same email as the main customer.
       CASE WHEN p.owner_key = 'other' THEN 'review.other@example.test'
            ELSE 'review.customer@example.test' END,
       min(p.check_in_date), max(p.check_out_date),
       CASE WHEN p.state IN ('CANCELLED', 'EXPIRED') THEN p.state ELSE 'CONFIRMED' END,
       sum((p.end_offset - p.start_offset) * p.base_price),
       -- Tokens are demo-only; never confer review eligibility.
       encode(sha256(convert_to('ReviewDemoOrder' || lpad(p.order_n::text, 2, '0') || '-LocalOnly-AccessToken', 'UTF8')), 'hex'),
       'hanbay-review-demo-order-' || p.order_n,
       encode(sha256(convert_to('hanbay-review-demo-request-' || p.order_n, 'UTF8')), 'hex'),
       min(p.booked_at) + interval '15 minutes',
       CASE WHEN p.state = 'CANCELLED' THEN min(p.booked_at) + interval '5 minutes' END,
       min(p.booked_at), current_timestamp
FROM review_demo_plan p
GROUP BY p.order_id, p.order_n, p.owner_id, p.owner_key, p.state;

INSERT INTO bookings
    (id, booking_code, booked_by, order_id, order_room_number, room_type_id,
     check_in_date, check_out_date, adult_count, child_count, child_ages, status, total_amount,
     paid_amount, expired_at, cancelled_at, checked_in_at, checked_out_at, created_at, updated_at)
SELECT p.booking_id, 'RV-DEMO-' || lpad(p.n::text, 2, '0'), p.owner_id,
       p.order_id, p.room_n, p.room_type_id, p.check_in_date, p.check_out_date,
       2, 0, '[]'::jsonb, p.state::booking_status,
       (p.end_offset - p.start_offset) * p.base_price,
       CASE WHEN p.state IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT')
            THEN (p.end_offset - p.start_offset) * p.base_price ELSE 0 END,
       p.booked_at + interval '15 minutes',
       CASE WHEN p.state = 'CANCELLED' THEN p.booked_at + interval '5 minutes' END,
       p.checked_in_at, p.checked_out_at, p.booked_at, current_timestamp
FROM review_demo_plan p;

INSERT INTO booking_guests
    (id, reference_id, booking_id, full_name, email, is_primary, created_at)
SELECT md5('hanbay-review-demo-guest-' || p.n)::uuid,
       md5('hanbay-review-demo-guest-ref-' || p.n)::uuid, p.booking_id,
       CASE WHEN p.owner_key = 'other' THEN 'Trần Bảo Linh' ELSE 'Nguyễn Minh An' END,
       CASE WHEN p.owner_key = 'other' THEN 'review.other@example.test'
            ELSE 'review.customer@example.test' END,
       true, p.booked_at
FROM review_demo_plan p;

INSERT INTO booking_nights
    (id, reference_id, booking_id, stay_date, price_per_night, created_at)
SELECT md5('hanbay-review-demo-night-' || p.n || '-' || night.day_offset)::uuid,
       md5('hanbay-review-demo-night-ref-' || p.n || '-' || night.day_offset)::uuid,
       p.booking_id, p.check_in_date + night.day_offset, p.base_price, p.booked_at
FROM review_demo_plan p
CROSS JOIN LATERAL generate_series(0, p.end_offset - p.start_offset - 1) AS night(day_offset);

INSERT INTO room_type_reviews
    (id, booking_id, order_id, room_type_id, reviewer_id, reviewer_name, rating, comment,
     hotel_reply, replied_at, replied_by, deleted_at, deleted_by, delete_reason, created_at, updated_at)
SELECT md5('hanbay-review-demo-review-' || p.n)::uuid,
       p.booking_id, p.order_id, p.room_type_id, p.owner_id, 'Nguyễn Minh An', r.rating, r.comment,
       CASE WHEN p.n = 10 THEN 'Cảm ơn anh An. Khách sạn đã kiểm tra và cải thiện khả năng cách âm.' END,
       CASE WHEN p.n = 10 THEN p.checked_out_at + interval '2 days' END,
       CASE WHEN p.n = 10 THEN pg_temp.review_demo_user_id('manager') END,
       CASE WHEN p.n = 16 THEN p.checked_out_at + interval '2 days' END,
       CASE WHEN p.n = 16 THEN pg_temp.review_demo_user_id('manager') END,
       CASE WHEN p.n = 16 THEN 'Dữ liệu mẫu: nội dung quảng cáo không liên quan đến trải nghiệm lưu trú.' END,
       p.checked_out_at + interval '1 day', p.checked_out_at + interval '2 days'
FROM review_demo_plan p
JOIN (VALUES
    ( 9, 5, 'Phòng sạch, hai giường thoải mái. Tôi rất hài lòng.'),
    (10, 4, 'Phòng đẹp, nhân viên nhiệt tình; buổi tối còn nghe tiếng hành lang.'),
    (11, 3, 'Trải nghiệm ổn, mong bổ sung thêm đồ dùng trong phòng.'),
    (12, 2, 'Điều hòa làm lạnh chậm, cần kiểm tra trước khi giao phòng.'),
    (13, 1, 'Vệ sinh phòng chưa đạt kỳ vọng. Mong khách sạn cải thiện.'),
    (14, 5, 'Không gian yên tĩnh, giường ngủ dễ chịu. Sẽ quay lại.'),
    (15, 4, NULL),
    (16, 1, '[MẪU ĐÃ XÓA] Nội dung quảng cáo không liên quan.')
) AS r(n, rating, comment) ON r.n = p.n;

-- Print public links and stable IDs for API checks without exposing real data.
SELECT code AS room_type_code, name, '/room-types/' || id AS public_path
FROM room_types WHERE code IN ('DELUXE_TWIN', 'DELUXE_KING') ORDER BY code;
SELECT b.booking_code, b.id AS booking_id, o.id AS order_id, b.status
FROM bookings b JOIN booking_orders o ON o.id = b.order_id
WHERE b.id IN (SELECT md5('hanbay-review-demo-booking-' || n)::uuid FROM generate_series(1, 21) AS n)
ORDER BY b.booking_code;
COMMIT;
