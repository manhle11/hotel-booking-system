-- One-time reset for the local hotelbooking_db.
-- Room numbers encode the floor: 301 is floor 3, 1404 is floor 14.
-- Floors 1 and 2 are reserved for shared hotel facilities in this demo layout.
-- Run with psql -v ON_ERROR_STOP=1 -d hotelbooking_db -f this_file.
-- This is deliberately not a Flyway migration: other local databases may have
-- bookings assigned to their existing rooms and must keep those references.

BEGIN;

LOCK TABLE public.rooms IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF current_database() <> 'hotelbooking_db' THEN
        RAISE EXCEPTION 'This inventory reset is only for hotelbooking_db';
    END IF;

    IF EXISTS (SELECT 1 FROM public.bookings)
       OR EXISTS (SELECT 1 FROM public.maintenance_requests) THEN
        RAISE EXCEPTION 'Inventory reset requires no bookings or maintenance history';
    END IF;

    IF (
        SELECT count(*)
        FROM public.room_types
        WHERE code IN ('SUPERIOR', 'DELUXE', 'JUNIOR_SUITE', 'HANBAY-OCEAN-SUITE')
          AND status = 'ACTIVE'
    ) <> 4 THEN
        RAISE EXCEPTION 'All four required room types must exist and be active';
    END IF;
END;
$$;

DELETE FROM public.rooms;

WITH floor_plan(room_type_code, first_floor, last_floor, rooms_per_floor) AS (
    VALUES
        ('SUPERIOR', 3, 6, 12),
        ('DELUXE', 7, 10, 10),
        ('JUNIOR_SUITE', 11, 12, 6),
        ('HANBAY-OCEAN-SUITE', 13, 14, 4)
), physical_rooms AS (
    SELECT
        plan.room_type_code,
        floor_number,
        (floor_number * 100 + room_on_floor)::varchar(30) AS room_number
    FROM floor_plan AS plan
    CROSS JOIN LATERAL generate_series(plan.first_floor, plan.last_floor) AS floor_number
    CROSS JOIN LATERAL generate_series(1, plan.rooms_per_floor) AS room_on_floor
)
INSERT INTO public.rooms (id, room_type_id, room_number, floor, status, created_at)
SELECT gen_random_uuid(), room_types.id, physical_rooms.room_number,
       physical_rooms.floor_number, 'AVAILABLE'::room_status, CURRENT_TIMESTAMP
FROM physical_rooms
JOIN public.room_types ON room_types.code = physical_rooms.room_type_code;

DO $$
BEGIN
    IF (SELECT count(*) FROM public.rooms) <> 108 THEN
        RAISE EXCEPTION 'Expected exactly 108 rooms after inventory reset';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.rooms
        WHERE floor < 3 OR floor > 14 OR room_number::integer / 100 <> floor
    ) THEN
        RAISE EXCEPTION 'Room numbering does not match the floor plan';
    END IF;
END;
$$;

COMMIT;
