CREATE TABLE booking_orders (
    id UUID PRIMARY KEY,
    order_code VARCHAR(50) NOT NULL UNIQUE,
    booked_by UUID REFERENCES users(id),
    contact_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(30),
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount BIGINT NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    access_token_hash VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_order_stay_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','EXPIRED'))
);
CREATE INDEX idx_booking_orders_owner ON booking_orders(booked_by, created_at);
CREATE INDEX idx_booking_orders_expiration ON booking_orders(status, expires_at);

ALTER TABLE bookings ADD COLUMN order_id UUID REFERENCES booking_orders(id);
ALTER TABLE bookings ADD COLUMN order_room_number INTEGER;
CREATE INDEX idx_bookings_order ON bookings(order_id);
CREATE UNIQUE INDEX idx_bookings_order_room ON bookings(order_id, order_room_number);

-- booking_id remains the representative room for existing staff reports and legacy payments.
-- For order payments the financial owner is order_id, with immutable per-room allocations below.
ALTER TABLE payments ADD COLUMN order_id UUID REFERENCES booking_orders(id);
CREATE INDEX idx_payments_order ON payments(order_id, created_at);

CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    amount BIGINT NOT NULL CHECK (amount >= 0),
    applied_amount BIGINT NOT NULL DEFAULT 0 CHECK (applied_amount >= 0 AND applied_amount <= amount),
    CONSTRAINT uq_payment_allocation UNIQUE (payment_id, booking_id)
);
CREATE INDEX idx_payment_allocations_booking ON payment_allocations(booking_id);
