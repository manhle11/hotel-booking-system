-- No backfill: historical reservations keep their existing accepted contract.
ALTER TABLE bookings
    ADD COLUMN balance_due_at TIMESTAMPTZ,
    ADD COLUMN terms_version VARCHAR(50),
    ADD COLUMN accepted_terms TEXT,
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN original_check_in_date DATE,
    ADD COLUMN original_first_night_amount BIGINT CHECK (original_first_night_amount >= 0),
    ADD COLUMN cancellation_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN policy_fee_total BIGINT NOT NULL DEFAULT 0 CHECK (policy_fee_total >= 0),
    ADD CONSTRAINT ck_booking_policy_fee_cap CHECK (original_first_night_amount IS NULL OR policy_fee_total <= original_first_night_amount);

CREATE TABLE booking_policy_fees (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    event VARCHAR(30) NOT NULL,
    previous_amount BIGINT NOT NULL CHECK (previous_amount >= 0),
    amount BIGINT NOT NULL,
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    actor_id UUID REFERENCES users(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL
);
CREATE INDEX idx_booking_policy_fees ON booking_policy_fees(booking_id, occurred_at);

ALTER TABLE booking_change_requests ALTER COLUMN requested_by DROP NOT NULL;
ALTER TABLE booking_change_requests ADD COLUMN consent_token VARCHAR(128), ADD COLUMN consent_at TIMESTAMPTZ;

ALTER TABLE booking_change_requests ADD COLUMN drop_services BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE booking_change_requests ADD COLUMN manager_exception BOOLEAN NOT NULL DEFAULT FALSE;
