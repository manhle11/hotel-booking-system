-- Categories are presentation groups only; inventory continues to come from rooms.
CREATE TABLE room_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9_-]{1,50}$'),
    name VARCHAR(150) NOT NULL CHECK (length(trim(name)) > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

INSERT INTO room_categories (code, name) VALUES
    ('STANDARD', 'Standard'),
    ('SUPERIOR', 'Superior'),
    ('DELUXE', 'Deluxe'),
    ('TRIPLE', 'Triple'),
    ('FAMILY', 'Family'),
    ('SUITE', 'Suite');

-- Nullable for legacy types: assigning a category requires an explicit operator decision.
ALTER TABLE room_types ADD COLUMN category_id UUID;
ALTER TABLE room_types ADD CONSTRAINT fk_room_type_category
    FOREIGN KEY (category_id) REFERENCES room_categories(id);
CREATE INDEX idx_room_types_category_id ON room_types(category_id);

CREATE TABLE room_type_beds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    -- VARCHAR rather than a PostgreSQL enum allows additional bed types in later releases.
    bed_type VARCHAR(50) NOT NULL CHECK (bed_type ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    quantity INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_room_type_bed UNIQUE (room_type_id, bed_type)
);

-- Deliberately do not parse room_types.bed_type, room names or capacities.
-- No bed rows means UNCONFIGURED. The original bed_type text is retained verbatim.
-- Do not modify rooms, bookings, pricing or capacity columns during this migration.
