CREATE TABLE hotel_floor_plans (
    id UUID PRIMARY KEY,
    floor_number INTEGER NOT NULL UNIQUE CHECK (floor_number BETWEEN -10 AND 200),
    name VARCHAR(80) NOT NULL,
    grid_columns INTEGER NOT NULL CHECK (grid_columns BETWEEN 8 AND 80),
    grid_rows INTEGER NOT NULL CHECK (grid_rows BETWEEN 8 AND 80),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    published_at TIMESTAMPTZ
);

CREATE TABLE hotel_floor_plan_elements (
    id UUID PRIMARY KEY,
    floor_plan_id UUID NOT NULL REFERENCES hotel_floor_plans(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ROOM', 'CORRIDOR', 'ELEVATOR', 'STAIRS', 'COMMON_AREA')),
    room_id UUID REFERENCES rooms(id) ON DELETE RESTRICT,
    label VARCHAR(80),
    x INTEGER NOT NULL CHECK (x >= 0),
    y INTEGER NOT NULL CHECK (y >= 0),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    CHECK ((kind = 'ROOM') = (room_id IS NOT NULL)),
    UNIQUE (floor_plan_id, room_id)
);

CREATE INDEX idx_floor_plan_elements_room ON hotel_floor_plan_elements(room_id) WHERE room_id IS NOT NULL;
CREATE INDEX idx_floor_plan_elements_plan ON hotel_floor_plan_elements(floor_plan_id);
