CREATE TABLE room_type_reviews (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    order_id UUID REFERENCES booking_orders(id),
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    reviewer_id UUID REFERENCES users(id),
    reviewer_name VARCHAR(150) NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(1000),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_review_deletion_audit CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

-- Keep uniqueness after deletion: removing a review does not grant another review of the same stay.
CREATE UNIQUE INDEX uq_review_order_room_type
    ON room_type_reviews(order_id, room_type_id) WHERE order_id IS NOT NULL;
CREATE INDEX idx_reviews_public_room_type
    ON room_type_reviews(room_type_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_reviews_public_created
    ON room_type_reviews(created_at DESC, id DESC) WHERE deleted_at IS NULL;
