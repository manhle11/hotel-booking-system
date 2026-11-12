-- Add the fourth Triple Mixed room to the local demo without resetting data.
-- Run with psql -X -v ON_ERROR_STOP=1 after backing up hotelbooking_db.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '20s';

DO $add_triple_504$
DECLARE
    triple_id uuid;
    existing_room rooms%ROWTYPE;
BEGIN
    IF current_database() <> 'hotelbooking_db' THEN
        RAISE EXCEPTION 'This local demo update requires hotelbooking_db';
    END IF;
    SELECT id INTO STRICT triple_id FROM room_types WHERE code='TRIPLE_MIXED' AND status='ACTIVE' FOR UPDATE;
    SELECT * INTO existing_room FROM rooms WHERE room_number='504' FOR UPDATE;
    IF FOUND THEN
        IF existing_room.room_type_id IS DISTINCT FROM triple_id OR existing_room.floor IS DISTINCT FROM 5 THEN
            RAISE EXCEPTION 'Room 504 already belongs to a different type or floor';
        END IF;
        -- Preserve its current status, note and timestamps on repeat execution.
        RETURN;
    END IF;
    IF (SELECT count(*) FROM rooms WHERE room_type_id=triple_id AND floor=5 AND room_number IN ('501','502','503'))<>3 THEN
        RAISE EXCEPTION 'Expected the existing Triple Mixed rooms on floor 5';
    END IF;
    INSERT INTO rooms (id,room_type_id,room_number,floor,status,note,created_at,updated_at)
    VALUES (md5('hanbay-v2-room:504')::uuid,triple_id,'504',5,'AVAILABLE'::room_status,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
END;
$add_triple_504$;
COMMIT;
