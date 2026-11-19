-- A cheaper rescheduled stay does not change the first-night fee base accepted for V2.
ALTER TABLE bookings DROP CONSTRAINT ck_no_show_settlement;

ALTER TABLE bookings ADD CONSTRAINT ck_no_show_settlement CHECK (
    status::text <> 'NO_SHOW' OR (
        no_show_at IS NOT NULL AND no_show_fee IS NOT NULL
        AND no_show_original_total IS NOT NULL AND no_show_policy_version IS NOT NULL
        AND no_show_fee <= CASE
            WHEN terms_version = 'HANBAY-STANDARD-2026-11-V2'
                AND original_first_night_amount IS NOT NULL
            THEN original_first_night_amount
            ELSE no_show_original_total
        END
        AND total_amount = no_show_fee
        AND checked_in_at IS NULL AND assigned_room_id IS NULL
    )
);
