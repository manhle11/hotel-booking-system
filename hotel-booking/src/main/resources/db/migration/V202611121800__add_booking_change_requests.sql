ALTER TABLE bookings
    ADD COLUMN stay_revision INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN cancellation_original_total BIGINT CHECK (cancellation_original_total >= 0),
    ADD COLUMN cancellation_fee BIGINT CHECK (cancellation_fee >= 0);

CREATE TABLE booking_change_requests (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    scope_key VARCHAR(80) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('CANCELLATION', 'RESCHEDULE')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_check_in DATE,
    requested_check_out DATE,
    reason VARCHAR(500) NOT NULL,
    requested_by UUID NOT NULL REFERENCES users(id),
    requested_at TIMESTAMPTZ NOT NULL,
    decided_by UUID REFERENCES users(id),
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    snapshot JSONB,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_change_request_dates CHECK (
        (type = 'CANCELLATION' AND requested_check_in IS NULL AND requested_check_out IS NULL)
        OR (type = 'RESCHEDULE' AND requested_check_in IS NOT NULL AND requested_check_out > requested_check_in)
    )
);
CREATE UNIQUE INDEX uk_pending_booking_change_scope ON booking_change_requests(scope_key) WHERE status = 'PENDING';
CREATE INDEX idx_booking_change_status_date ON booking_change_requests(status, requested_at);
