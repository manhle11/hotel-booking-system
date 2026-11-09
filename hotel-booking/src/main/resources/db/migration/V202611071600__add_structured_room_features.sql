-- Descriptive/searchable room features are independent of beds, capacity and amenities.
-- NULL preserves "not specified" for pre-existing types; do not infer features from prose.
ALTER TABLE room_types
    ADD COLUMN view_type VARCHAR(30),
    ADD COLUMN has_balcony BOOLEAN,
    ADD COLUMN high_floor BOOLEAN;

ALTER TABLE room_types ADD CONSTRAINT ck_room_type_view_type
    CHECK (view_type IS NULL OR view_type IN ('COURTYARD', 'CITY', 'SEA'));
