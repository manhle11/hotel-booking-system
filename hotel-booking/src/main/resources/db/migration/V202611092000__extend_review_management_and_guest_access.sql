ALTER TABLE room_type_reviews
    ADD COLUMN delete_reason VARCHAR(500),
    ADD COLUMN hotel_reply VARCHAR(1000),
    ADD COLUMN replied_at TIMESTAMPTZ,
    ADD COLUMN replied_by UUID REFERENCES users(id),
    ADD CONSTRAINT ck_review_reply_audit CHECK (
        (hotel_reply IS NULL AND replied_at IS NULL AND replied_by IS NULL)
        OR (hotel_reply IS NOT NULL AND replied_at IS NOT NULL AND replied_by IS NOT NULL)
    );
CREATE INDEX idx_reviews_deleted ON room_type_reviews(deleted_at DESC, id DESC) WHERE deleted_at IS NOT NULL;

-- These tokens authorize reviews only; they never grant access to booking/payment APIs.
CREATE TABLE review_access_challenges (
    id UUID PRIMARY KEY,
    booking_id UUID REFERENCES bookings(id),
    order_id UUID REFERENCES booking_orders(id),
    otp_hash VARCHAR(100) NOT NULL,
    otp_expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    consumed_at TIMESTAMPTZ,
    token_hash VARCHAR(64) UNIQUE,
    token_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_review_access_subject CHECK ((booking_id IS NULL) <> (order_id IS NULL)),
    CONSTRAINT ck_review_access_grant CHECK ((token_hash IS NULL) = (token_expires_at IS NULL))
);
CREATE INDEX idx_review_access_expiry ON review_access_challenges(otp_expires_at, token_expires_at);
