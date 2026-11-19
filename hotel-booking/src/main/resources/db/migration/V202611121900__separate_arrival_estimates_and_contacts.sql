ALTER TABLE bookings
    ADD COLUMN expected_arrival_at TIMESTAMPTZ,
    ADD COLUMN arrival_contact_at TIMESTAMPTZ,
    ADD COLUMN arrival_contact_channel VARCHAR(30),
    ADD COLUMN arrival_contact_outcome VARCHAR(30),
    ADD COLUMN arrival_contact_note VARCHAR(500);

CREATE INDEX idx_booking_arrival_work ON bookings(status, check_in_date, expected_arrival_at);
CREATE INDEX idx_booking_arrival_received ON bookings(checked_in_at) WHERE checked_in_at IS NOT NULL;
CREATE INDEX idx_booking_arrival_no_show ON bookings(no_show_at) WHERE no_show_at IS NOT NULL;

-- Keep legacy hold approvals as approvals. Do not infer an ETA from a hold deadline.
