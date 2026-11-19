ALTER TABLE bookings
    ADD COLUMN arrival_policy_version VARCHAR(50),
    ADD COLUMN arrival_hold_until TIMESTAMPTZ,
    ADD COLUMN arrival_hold_requested_until TIMESTAMPTZ,
    ADD COLUMN no_show_at TIMESTAMPTZ,
    ADD COLUMN no_show_fee BIGINT CHECK (no_show_fee >= 0),
    ADD COLUMN no_show_original_total BIGINT CHECK (no_show_original_total >= 0),
    ADD COLUMN no_show_policy_version VARCHAR(50),
    ADD COLUMN replacement_for_booking_id UUID REFERENCES bookings(id);

ALTER TABLE bookings ADD CONSTRAINT ck_no_show_settlement CHECK (
    status::text <> 'NO_SHOW' OR (
        no_show_at IS NOT NULL AND no_show_fee IS NOT NULL
        AND no_show_original_total IS NOT NULL AND no_show_policy_version IS NOT NULL
        AND no_show_fee <= no_show_original_total AND total_amount = no_show_fee
        AND checked_in_at IS NULL AND assigned_room_id IS NULL
    )
);

ALTER TABLE payment_refunds
    ADD COLUMN source_booking_id UUID REFERENCES bookings(id);
CREATE INDEX idx_refund_source_booking ON payment_refunds(source_booking_id);
