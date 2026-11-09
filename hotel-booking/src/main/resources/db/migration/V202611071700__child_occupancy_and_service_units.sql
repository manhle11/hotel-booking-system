-- Nullable ages keep legacy bookings readable without guessing ages.
ALTER TABLE bookings ADD COLUMN child_ages JSONB;
ALTER TABLE bookings ADD CONSTRAINT ck_booking_child_ages_array CHECK (child_ages IS NULL OR (jsonb_typeof(child_ages) = 'array' AND jsonb_array_length(child_ages) = child_count));

ALTER TABLE add_on_services ADD COLUMN quantity_unit VARCHAR(20) NOT NULL DEFAULT 'UNIT'
    CHECK (quantity_unit IN ('UNIT','SERVING','TRIP','PACKAGE'));
ALTER TABLE booking_add_ons ADD COLUMN quantity_unit VARCHAR(20) NOT NULL DEFAULT 'UNIT'
    CHECK (quantity_unit IN ('UNIT','SERVING','TRIP','PACKAGE'));
-- Only the explicitly defined demo service identities get a known unit. Historical
-- booking snapshots remain UNIT: do not guess how a past service was sold.
UPDATE add_on_services SET quantity_unit = CASE
    WHEN code IN ('BREAKFAST_BUFFET','EXTRA_MEAL') THEN 'SERVING'
    WHEN code IN ('AIRPORT_PICKUP','AIRPORT_DROPOFF') THEN 'TRIP'
    WHEN code = 'ROOM_DECORATION' THEN 'PACKAGE' END
WHERE code IN ('BREAKFAST_BUFFET','EXTRA_MEAL','AIRPORT_PICKUP','AIRPORT_DROPOFF','ROOM_DECORATION')
  AND id = md5('hanbay-v2-addon:' || code)::UUID;

-- Approved demo policy: the limits are independent; 4 adults + 2 children is NOT allowed.
UPDATE room_types SET capacity_children = 2,
    description = replace(description, 'giới hạn 4 người lớn và 0 trẻ em', 'giới hạn 4 người lớn và 2 trẻ em, tổng tối đa 4 khách; trẻ em dùng giường có sẵn'),
    updated_at = CURRENT_TIMESTAMP
WHERE id = md5('hanbay-v2-type:FAMILY_DOUBLE_QUEEN')::UUID
  AND code = 'FAMILY_DOUBLE_QUEEN' AND capacity_adults = 4 AND capacity_children = 0 AND max_guests = 4;
