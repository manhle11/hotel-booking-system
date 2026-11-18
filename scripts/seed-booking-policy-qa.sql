-- Local QA only. Adds a dedicated room type, eight rooms and fictional bookings/receipts.
-- No existing booking, payment, account or room is updated or deleted.
-- Create/sign in to qa-policy@example.test first, or replace that email below.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
SELECT set_config('hanbay.qa_guest_email', 'qa-policy@example.test', true);

DO $$
BEGIN
    IF current_database() <> 'hotelbooking_db' THEN
        RAISE EXCEPTION 'QA seed requires local hotelbooking_db';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE email = current_setting('hanbay.qa_guest_email')) THEN
        RAISE EXCEPTION 'Create the QA guest account or change hanbay.qa_guest_email in this script first';
    END IF;
    IF to_regclass('booking_policy_fees') IS NULL THEN
        RAISE EXCEPTION 'Start the new backend and apply Flyway migrations first';
    END IF;
END $$;

CREATE TEMP TABLE policy_qa_context ON COMMIT DROP AS
SELECT 'QP' || to_char(clock_timestamp(), 'HH24MISS') || substr(gen_random_uuid()::text, 1, 6) AS run,
       gen_random_uuid() AS type_id,
       (SELECT id FROM users WHERE email = current_setting('hanbay.qa_guest_email')) AS guest_id;

INSERT INTO room_types (id, code, name, description, base_price, max_guests, capacity_adults,
                       capacity_children, bed_type, category_id, status, created_at, updated_at)
SELECT type_id, run, 'QA chính sách ' || run, 'Dữ liệu kiểm thử; không phải giao dịch thật',
       800000, 2, 2, 0, '1 KING', (SELECT id FROM room_categories WHERE code = 'STANDARD'),
       'ACTIVE', localtimestamp, localtimestamp FROM policy_qa_context;
INSERT INTO room_type_beds (room_type_id, bed_type, quantity)
SELECT type_id, 'KING', 1 FROM policy_qa_context;
INSERT INTO rooms (room_type_id, room_number, floor, status, created_at, updated_at)
SELECT type_id, run || '-' || n, 1, 'AVAILABLE', localtimestamp, localtimestamp
FROM policy_qa_context CROSS JOIN generate_series(1,8) n;

-- Default new price for the cheap reschedule: 100,000/night before any other period modifier.
INSERT INTO price_rules (reference_id, room_type_id, name, rule_type, adjustment_type, adjustment_direction,
                         adjustment_value, start_date, end_date, priority, active, created_at, updated_at)
SELECT gen_random_uuid(), type_id, 'QA đổi kỳ rẻ hơn', 'PROMOTION', 'FIXED', 'DISCOUNT',
       700000, current_date + 10, current_date + 12, 1000000, true, localtimestamp, localtimestamp
FROM policy_qa_context;

CREATE TEMP TABLE policy_qa_cases ON COMMIT DROP AS
SELECT gen_random_uuid() AS id, c.run || '-' || v.label AS code, v.label,
       current_date + v.arrival AS arrival, current_date + v.departure AS departure,
       v.nightly::bigint AS nightly, v.p::bigint AS p, v.revision, v.legacy,
       CASE WHEN v.label = 'EARLY' THEN 2400000 ELSE (v.departure-v.arrival)*v.nightly END::bigint AS received,
       c.type_id, c.guest_id
FROM policy_qa_context c CROSS JOIN (VALUES
    ('FREE', 5, 8, 800000, 800000, 0, false),
    ('HALF', CASE WHEN localtime <= time '14:00' THEN 1 ELSE 2 END,
             CASE WHEN localtime <= time '14:00' THEN 4 ELSE 5 END, 800000, 800000, 0, false),
    ('LATE', -2, 2, 800000, 800000, 0, false),
    ('NOSHOW', -2, 1, 800000, 800000, 0, false),
    ('DEBT', -2, 1, 100000, 800000, 1, false),
    ('LEGACY', 5, 8, 800000, 800000, 0, true),
    ('REPRICE', 5, 8, 800000, 800000, 0, false),
    ('BALANCE', 5, 8, 400000, 400000, 0, false),
    ('EARLY', 0, 3, 100000, 800000, 1, false)
) v(label, arrival, departure, nightly, p, revision, legacy);

INSERT INTO bookings (id, booking_code, booked_by, room_type_id, check_in_date, check_out_date,
                      adult_count, child_count, status, total_amount, paid_amount, stay_revision,
                      terms_version, accepted_terms, terms_accepted_at, arrival_policy_version,
                      original_check_in_date, original_first_night_amount, created_at, updated_at)
SELECT id, code, guest_id, type_id, arrival, departure, 1, 0, 'CONFIRMED',
       (departure-arrival)*nightly, (departure-arrival)*nightly, revision,
       CASE WHEN NOT legacy THEN 'HANBAY-STANDARD-2026-11-V2' END,
       CASE WHEN NOT legacy THEN 'QA: hủy/đổi theo mốc 48/24 giờ; 0/50%/100% P gốc, tổng tối đa P. Dữ liệu giả lập.' END,
       CASE WHEN NOT legacy THEN now()-interval '15 days' END,
       CASE WHEN NOT legacy THEN 'HANBAY-STANDARD-2026-11-V2' END,
       CASE WHEN NOT legacy THEN CASE WHEN revision>0 THEN current_date-10 ELSE arrival END END,
       CASE WHEN NOT legacy THEN p END, localtimestamp-interval '15 days', localtimestamp
FROM policy_qa_cases;
INSERT INTO booking_nights (reference_id, booking_id, stay_date, price_per_night, created_at)
SELECT gen_random_uuid(), id, arrival+n, nightly, localtimestamp-interval '15 days'
FROM policy_qa_cases CROSS JOIN LATERAL generate_series(0, departure-arrival-1) n;
INSERT INTO booking_guests (reference_id, booking_id, full_name, email, phone, is_primary, created_at)
SELECT gen_random_uuid(), id, 'Khách QA ' || label, current_setting('hanbay.qa_guest_email'),
       '0900000000', true, localtimestamp FROM policy_qa_cases;
INSERT INTO booking_status_histories (reference_id, booking_id, new_status, reason, created_at)
SELECT gen_random_uuid(), id, 'CONFIRMED', 'Dữ liệu QA: ' || label || '; tiền giả lập, không phải chuyển khoản thật',
       localtimestamp FROM policy_qa_cases;

CREATE TEMP TABLE policy_qa_payments ON COMMIT DROP AS
SELECT gen_random_uuid() AS payment_id, gen_random_uuid() AS refund_transaction_id, q.* FROM policy_qa_cases q;
INSERT INTO payments (id, booking_id, payment_code, amount, currency, status, paid_at, expires_at, created_at, updated_at)
SELECT payment_id, id, 'QA-' || code, received, 'VND',
       CASE WHEN label='EARLY' THEN 'PARTIALLY_REFUNDED' ELSE 'SUCCESS' END::payment_status,
       localtimestamp-interval '15 days', localtimestamp-interval '14 days', localtimestamp-interval '15 days', localtimestamp
FROM policy_qa_payments;
INSERT INTO payment_transactions (payment_id, transaction_ref, type, status, amount, currency, provider_message,
                                  occurred_at, received_at, created_at)
SELECT payment_id, 'QA-' || code, 'PAYMENT', 'SUCCESS', received, 'VND', 'QA: không phải tiền ngân hàng',
       localtimestamp-interval '15 days', localtimestamp-interval '15 days', localtimestamp
FROM policy_qa_payments;
INSERT INTO payment_transactions (id, payment_id, transaction_ref, type, status, amount, currency,
                                  provider_message, occurred_at, received_at, created_at)
SELECT refund_transaction_id, payment_id, 'QA-REFUND-' || code, 'REFUND', 'SUCCESS', 2100000, 'VND',
       'QA: hoàn chênh lệch giả lập', localtimestamp-interval '1 day', localtimestamp-interval '1 day', localtimestamp
FROM policy_qa_payments WHERE label='EARLY';
INSERT INTO payment_refunds (payment_id, transaction_id, source_booking_id, amount, currency, status, reason,
                            idempotency_key, refund_method, bank_name, account_holder_name, account_number,
                            provider_refund_id, requested_at, refunded_at, created_at, updated_at)
SELECT payment_id, refund_transaction_id, id, 2100000, 'VND', 'SUCCESS', 'QA: hoàn chênh lệch đổi lịch',
       'qa-refund:' || id, 'BANK_TRANSFER', 'Ngân hàng QA', 'KHACH QA', '00000000', 'QA-REFUND-' || code,
       localtimestamp-interval '1 day', localtimestamp-interval '1 day', localtimestamp, localtimestamp
FROM policy_qa_payments WHERE label='EARLY';

SELECT label AS case_name, code AS booking_code, arrival, departure,
       current_date+10 AS cheap_new_arrival, current_date+20 AS higher_new_arrival
FROM policy_qa_cases ORDER BY label;
COMMIT;
