-- Explicit local demo reset requested by the owner. Never run automatically on startup.
-- Run Flyway through 202611071800 and take a database backup before executing this file.
-- Accounts, authentication, Flyway history, holidays and AI knowledge are preserved.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

DO $$
BEGIN
    IF current_database() <> 'hotelbooking_db' THEN
        RAISE EXCEPTION 'Room demo reset requires hotelbooking_db; got %', current_database();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '202611071800' AND success) THEN
        RAISE EXCEPTION 'Apply Flyway migration 202611071800 before resetting the room catalog';
    END IF;
END $$;

-- List every dependent table explicitly. An unexpected FK stops the transaction;
-- do not use CASCADE to silently delete additional datasets.
TRUNCATE TABLE
    payment_allocations, payment_refunds, payment_webhook_events, payment_transactions, payments,
    booking_night_price_rules, booking_nights, booking_add_ons, booking_guests,
    booking_status_histories, bookings, booking_orders,
    maintenance_cost_history, maintenance_costs, maintenance_requests,
    price_rules, rooms, room_type_images, room_type_beds, room_type_amenities,
    room_types, room_categories, amenities, add_on_services;

SELECT seed_hanbay_room_demo_catalog();
COMMIT;
