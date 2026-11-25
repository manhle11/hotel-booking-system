CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE room_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES bookings(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    check_in DATE NOT NULL,
    check_out DATE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('TENTATIVE', 'LOCKED', 'OCCUPIED', 'BLOCKED', 'RELEASED')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (check_out IS NULL OR check_out > check_in),
    CHECK ((booking_id IS NULL AND status IN ('BLOCKED', 'RELEASED')) OR booking_id IS NOT NULL),
    CONSTRAINT room_allocations_no_overlap EXCLUDE USING gist
        (room_id WITH =, daterange(check_in, check_out, '[)') WITH &&)
        WHERE (active) DEFERRABLE INITIALLY DEFERRED
);
CREATE UNIQUE INDEX room_allocations_one_active_booking ON room_allocations(booking_id) WHERE active AND booking_id IS NOT NULL;
CREATE UNIQUE INDEX room_allocations_one_active_block ON room_allocations(room_id) WHERE active AND booking_id IS NULL;

CREATE TABLE room_assignment_plans (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    payload JSONB NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    applied_at TIMESTAMPTZ,
    applied_by UUID REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX room_assignment_plans_type_created ON room_assignment_plans(room_type_id, created_at DESC);

CREATE TABLE room_assignment_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES bookings(id) ON DELETE SET NULL,
    room_type_id UUID REFERENCES room_types(id) ON DELETE SET NULL,
    action VARCHAR(40) NOT NULL,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    details JSONB NOT NULL DEFAULT '{}'
);
CREATE INDEX room_assignment_history_type_created ON room_assignment_history(room_type_id, created_at DESC);

CREATE TABLE room_assignment_refresh_queue (
    room_type_id UUID PRIMARY KEY REFERENCES room_types(id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bootstrap only actual stays. A tentative allocation never sets bookings.assigned_room_id.
INSERT INTO room_allocations(booking_id, room_id, check_in, check_out, status)
SELECT id, assigned_room_id, check_in_date, check_out_date, 'OCCUPIED'
FROM bookings WHERE status = 'CHECKED_IN' AND assigned_room_id IS NOT NULL;

INSERT INTO room_allocations(room_id, check_in, status)
SELECT id, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'BLOCKED'
FROM rooms WHERE status IN ('MAINTENANCE', 'OUT_OF_SERVICE');

-- Keep every lifecycle path (including schedulers and order bookings) consistent.
CREATE FUNCTION synchronize_booking_room_allocation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status::text NOT IN ('CONFIRMED', 'CHECKED_IN') THEN
        UPDATE room_allocations SET active = FALSE, status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
        WHERE booking_id = NEW.id AND active;
    ELSIF NEW.status::text = 'CHECKED_IN' AND NEW.assigned_room_id IS NOT NULL THEN
        UPDATE room_allocations SET active = FALSE, status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
        WHERE booking_id = NEW.id AND active AND room_id <> NEW.assigned_room_id;
        UPDATE room_allocations SET check_in = NEW.check_in_date, check_out = NEW.check_out_date,
            status = 'OCCUPIED', updated_at = CURRENT_TIMESTAMP
        WHERE booking_id = NEW.id AND active;
        IF NOT FOUND THEN
            INSERT INTO room_allocations(booking_id, room_id, check_in, check_out, status)
            VALUES (NEW.id, NEW.assigned_room_id, NEW.check_in_date, NEW.check_out_date, 'OCCUPIED');
        END IF;
    ELSE
        IF TG_OP = 'UPDATE' AND (OLD.check_in_date <> NEW.check_in_date OR OLD.check_out_date <> NEW.check_out_date)
            AND OLD.check_in_date > COALESCE(NULLIF(current_setting('hotel.assignment.today', TRUE), '')::date,
                (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) + 1 THEN
            UPDATE room_allocations SET active = FALSE, status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
            WHERE booking_id = NEW.id AND active AND status = 'TENTATIVE';
        END IF;
        UPDATE room_allocations SET check_in = NEW.check_in_date, check_out = NEW.check_out_date,
            updated_at = CURRENT_TIMESTAMP WHERE booking_id = NEW.id AND active;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER booking_room_allocation_lifecycle AFTER INSERT OR UPDATE OF
    status, assigned_room_id, check_in_date, check_out_date ON bookings
    FOR EACH ROW EXECUTE FUNCTION synchronize_booking_room_allocation();

CREATE FUNCTION synchronize_room_operational_block() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status::text IN ('MAINTENANCE', 'OUT_OF_SERVICE') THEN
        IF NOT EXISTS (SELECT 1 FROM room_allocations WHERE room_id = NEW.id AND active AND booking_id IS NULL) THEN
            INSERT INTO room_allocations(room_id, check_in, status)
            VALUES (NEW.id, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'BLOCKED');
        END IF;
    ELSE
        UPDATE room_allocations SET active = FALSE, status = 'RELEASED', updated_at = CURRENT_TIMESTAMP
        WHERE room_id = NEW.id AND active AND booking_id IS NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER room_assignment_operational_block AFTER INSERT OR UPDATE OF status ON rooms
    FOR EACH ROW EXECUTE FUNCTION synchronize_room_operational_block();
