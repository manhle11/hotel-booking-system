ALTER TABLE bookings ADD COLUMN checked_in_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN checked_out_at TIMESTAMPTZ;

-- Legacy JDBC timestamps use GMT+7 (spring.jpa.properties.hibernate.jdbc.time_zone).
ALTER TABLE bookings ALTER COLUMN expired_at TYPE TIMESTAMPTZ
    USING expired_at AT TIME ZONE 'Asia/Ho_Chi_Minh';

-- Existing stays keep their original status history; do not invent actual arrival/departure times.
CREATE INDEX idx_active_booking_nights ON bookings (room_type_id, check_in_date, check_out_date)
    WHERE status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN');
