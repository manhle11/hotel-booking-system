-- One canonical demo catalog shared by explicit reset and guarded local bootstrap.
-- Defining these functions does not change an existing catalog.
CREATE FUNCTION seed_hanbay_room_demo_catalog() RETURNS VOID LANGUAGE plpgsql AS $seed$
BEGIN
    IF EXISTS (SELECT 1 FROM room_types) OR EXISTS (SELECT 1 FROM rooms)
       OR EXISTS (SELECT 1 FROM room_categories) OR EXISTS (SELECT 1 FROM amenities)
       OR EXISTS (SELECT 1 FROM add_on_services) THEN
        RAISE EXCEPTION 'Canonical seed requires an empty catalog; use the explicit backup/reset procedure';
    END IF;
INSERT INTO room_categories (id, code, name, created_at, updated_at)
SELECT md5('hanbay-v2-category:' || c.code)::UUID, c.code, c.name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES ('STANDARD','Standard'), ('SUPERIOR','Superior'), ('DELUXE','Deluxe'),
             ('TRIPLE','Triple'), ('FAMILY','Family'), ('SUITE','Suite')) AS c(code,name);

CREATE TEMP TABLE reset_room_catalog (
    code TEXT PRIMARY KEY, name TEXT NOT NULL, category_code TEXT NOT NULL,
    beds JSONB NOT NULL, area_m2 DECIMAL(6,2) NOT NULL, base_price BIGINT NOT NULL,
    adults INT NOT NULL, children INT NOT NULL, max_guests INT NOT NULL,
    view_type TEXT NOT NULL, has_balcony BOOLEAN NOT NULL, high_floor BOOLEAN NOT NULL,
    floor INT NOT NULL, first_room INT NOT NULL, room_count INT NOT NULL,
    short_description TEXT NOT NULL, description TEXT NOT NULL, extra_amenities TEXT[] NOT NULL,
    photos TEXT[] NOT NULL,
    CHECK (adults > 0 AND children >= 0 AND max_guests >= adults AND max_guests <= adults + children)
) ON COMMIT DROP;

INSERT INTO reset_room_catalog VALUES
(
    'STANDARD_QUEEN', 'Standard Queen', 'STANDARD', '{"QUEEN":1}', 22, 1100000, 2, 0, 2,
    'COURTYARD', false, false, 2, 201, 6,
    'Phòng Standard 22 m² với một giường Queen cố định, cửa sổ hướng sân trong và phòng tắm riêng, phù hợp cho hai người lớn nghỉ ngắn ngày.',
    E'Standard Queen có diện tích 22 m² với một giường Queen, tủ quần áo và khu để hành lý. Cửa sổ hướng sân trong giúp không gian nghỉ ngơi yên tĩnh, phù hợp cho khách công tác hoặc cặp đôi.\n\nPhòng tiếp nhận tối đa 2 khách, giới hạn 2 người lớn và 0 trẻ em. Phòng được bố trí cố định một giường Queen trong toàn bộ kỳ lưu trú.\n\nTiện nghi gồm Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, ấm đun nước và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm; bữa sáng, đồ dùng minibar và các dịch vụ mua thêm được tính riêng theo lựa chọn.',
    ARRAY[]::TEXT[],
    ARRAY['photo-1566665797739-1674de7a421a','photo-1631049307264-da0ec9d70304','photo-1596394516093-501ba68a0ba6']
),
(
    'SUPERIOR_QUEEN', 'Superior Queen', 'SUPERIOR', '{"QUEEN":1}', 30, 1650000, 2, 1, 3,
    'CITY', false, false, 3, 301, 5,
    'Phòng Superior 30 m² với một giường Queen, bàn làm việc và cửa sổ hướng thành phố, phù hợp cho kỳ nghỉ đôi hoặc gia đình nhỏ.',
    E'Superior Queen rộng 30 m², bố trí một giường Queen cố định, bàn làm việc và góc thư giãn cạnh cửa sổ hướng thành phố. Khu để hành lý và tủ quần áo giúp khách thuận tiện trong những kỳ lưu trú nhiều ngày.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Một giường Queen là lựa chọn cố định của Superior Queen.\n\nPhòng có Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, ấm đun nước và phòng tắm riêng có vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; bữa sáng và các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc'],
    ARRAY['photo-1611892440504-42a792e24d32','photo-1568495248636-6432b97bd949','photo-1598928506311-c55ded91a20c']
),
(
    'SUPERIOR_TWIN', 'Superior Twin', 'SUPERIOR', '{"SINGLE":2}', 30, 1700000, 2, 1, 3,
    'CITY', false, false, 3, 306, 5,
    'Phòng Superior 30 m² gồm hai giường đơn cố định, bàn làm việc và cửa sổ hướng thành phố, thuận tiện cho bạn bè hoặc đồng nghiệp.',
    E'Superior Twin có diện tích 30 m² với hai giường đơn được bố trí tách biệt. Bàn làm việc, tủ quần áo và khu hành lý tạo không gian thuận tiện cho hai khách cần chỗ ngủ riêng trong cùng một phòng.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Hai giường đơn là cách bố trí cố định của Superior Twin.\n\nTiện nghi gồm Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, ấm đun nước và phòng tắm riêng có vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; bữa sáng và các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc'],
    ARRAY['photo-1611892440504-42a792e24d32','photo-1568495248636-6432b97bd949','photo-1598928506311-c55ded91a20c']
),
(
    'DELUXE_KING', 'Deluxe King', 'DELUXE', '{"KING":1}', 36, 2450000, 2, 1, 3,
    'SEA', true, true, 4, 401, 5,
    'Phòng Deluxe 36 m² với giường King cố định, ban công riêng và tầm nhìn biển từ tầng cao, phù hợp cho kỳ nghỉ thư giãn.',
    E'Deluxe King rộng 36 m² với một giường King, góc ngồi thư giãn và bàn làm việc. Ban công riêng hướng biển cùng vị trí tầng cao tạo không gian đón sáng và nghỉ ngơi thoáng đãng.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Giường King là cấu hình cố định; ban công và hướng nhìn được ghi rõ trong thông tin phòng.\n\nPhòng có Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, ấm đun nước, áo choàng và phòng tắm riêng có vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; bữa sáng và các dịch vụ mua thêm được tính riêng.',
    ARRAY['Bàn làm việc','Áo choàng tắm'],
    ARRAY['photo-1590490360182-c33d57733427','photo-1618773928121-c32242e63f39','photo-1566665797739-1674de7a421a']
),
(
    'DELUXE_TWIN', 'Deluxe Twin', 'DELUXE', '{"SINGLE":2}', 36, 2500000, 2, 1, 3,
    'SEA', true, true, 4, 406, 5,
    'Phòng Deluxe 36 m² có hai giường đơn cố định, ban công riêng và tầm nhìn biển từ tầng cao, dành cho khách muốn chỗ ngủ riêng.',
    E'Deluxe Twin có diện tích 36 m², bố trí hai giường đơn tách biệt, bàn làm việc và góc thư giãn. Ban công riêng hướng biển và vị trí tầng cao phù hợp với khách vừa nghỉ dưỡng vừa cần không gian làm việc.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Hai giường đơn được bố trí cố định trong suốt kỳ lưu trú.\n\nTiện nghi gồm Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, ấm đun nước, áo choàng và phòng tắm riêng có vòi sen, máy sấy tóc. Giá theo phòng mỗi đêm; bữa sáng và các dịch vụ mua thêm được lựa chọn riêng.',
    ARRAY['Bàn làm việc','Áo choàng tắm'],
    ARRAY['photo-1590490360182-c33d57733427','photo-1618773928121-c32242e63f39','photo-1566665797739-1674de7a421a']
),
(
    'TRIPLE_MIXED', 'Triple Mixed', 'TRIPLE', '{"QUEEN":1,"SINGLE":1}', 38, 2800000, 3, 0, 3,
    'CITY', false, true, 5, 501, 3,
    'Phòng Triple 38 m² kết hợp một giường Queen và một giường đơn cố định, phù hợp cho nhóm ba người lớn muốn ở cùng nhau.',
    E'Triple Mixed rộng 38 m², kết hợp một giường Queen và một giường đơn trong cùng không gian. Bàn làm việc, tủ quần áo và khu hành lý chung giúp nhóm ba người thuận tiện khi sinh hoạt và nghỉ ngơi.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 3 người lớn và 0 trẻ em. Phòng nằm ở tầng cao, có cửa sổ hướng thành phố và giữ cố định cách bố trí Queen kết hợp Single.\n\nPhòng có Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, ấm đun nước và phòng tắm riêng có vòi sen, máy sấy tóc. Giá cơ bản tính theo cả phòng mỗi đêm; bữa sáng và các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc'],
    ARRAY['photo-1631049307264-da0ec9d70304','photo-1596394516093-501ba68a0ba6','photo-1568495248636-6432b97bd949']
),
(
    'FAMILY_DOUBLE_QUEEN', 'Family Double Queen', 'FAMILY', '{"QUEEN":2}', 48, 3600000, 4, 2, 4,
    'SEA', true, true, 6, 601, 4,
    'Phòng Family 48 m² có hai giường Queen cố định, ban công và khu sinh hoạt chung, phù hợp cho gia đình hoặc nhóm bốn người lớn.',
    E'Family Double Queen có diện tích 48 m² với hai giường Queen, khu sinh hoạt chung và bàn làm việc. Tủ quần áo cùng khu để hành lý rộng tạo sự thuận tiện cho gia đình hoặc nhóm bạn đi cùng nhau.\n\nPhòng tiếp nhận tối đa 4 khách, giới hạn 4 người lớn và 2 trẻ em, tổng tối đa 4 khách; trẻ em dùng giường có sẵn. Ban công riêng hướng biển cùng vị trí tầng cao mang đến không gian thư giãn; hai giường Queen là cấu hình cố định.\n\nTiện nghi gồm Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, ấm đun nước, áo choàng và phòng tắm riêng có vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; bữa sáng và các dịch vụ gia đình được lựa chọn riêng.',
    ARRAY['Bàn làm việc','Áo choàng tắm'],
    ARRAY['photo-1598928506311-c55ded91a20c','photo-1618773928121-c32242e63f39','photo-1590490360182-c33d57733427']
),
(
    'SUITE_KING', 'Suite King', 'SUITE', '{"KING":1}', 60, 5200000, 2, 1, 3,
    'SEA', true, true, 7, 701, 2,
    'Suite 60 m² với giường King cố định, khu tiếp khách, bồn tắm và ban công hướng biển, dành cho kỳ nghỉ cần không gian riêng tư.',
    E'Suite King rộng 60 m² với một giường King, khu tiếp khách và góc làm việc riêng. Ban công hướng biển trên tầng cao cùng khu pha cà phê mang đến không gian nghỉ ngơi thoải mái cho kỳ lưu trú dài ngày.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Chỗ ngủ chính là một giường King cố định; khu tiếp khách dành cho sinh hoạt và thư giãn.\n\nSuite có Wi-Fi, TV, điều hòa, tủ lạnh mini, minibar, két an toàn, máy pha cà phê, ấm đun nước, áo choàng và phòng tắm có bồn tắm, vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; bữa sáng và các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc','Áo choàng tắm','Bồn tắm','Máy pha cà phê'],
    ARRAY['photo-1582719478250-c89cae4dc85b','photo-1631049307264-da0ec9d70304','photo-1596394516093-501ba68a0ba6']
);

INSERT INTO amenities (id,name,icon)
SELECT md5('hanbay-v2-amenity:' || a.name)::UUID,a.name,a.icon
FROM (VALUES ('Wi-Fi tốc độ cao','wifi'), ('TV','tv'), ('Điều hòa','air-conditioner'),
    ('Tủ lạnh mini','fridge'), ('Minibar','minibar'), ('Két an toàn','safe'),
    ('Máy sấy tóc','hair-dryer'), ('Ấm đun nước','kettle'), ('Phòng tắm riêng','bathroom'),
    ('Vòi sen','shower'), ('Tủ quần áo','wardrobe'), ('Bàn làm việc','desk'),
    ('Áo choàng tắm','bathrobe'), ('Bồn tắm','bathtub'), ('Máy pha cà phê','coffee')) AS a(name,icon);

INSERT INTO room_types (id,code,name,category_id,bed_type,area_m2,base_price,capacity_adults,
    capacity_children,max_guests,view_type,has_balcony,high_floor,short_description,description,status,created_at,updated_at)
SELECT md5('hanbay-v2-type:' || s.code)::UUID,s.code,s.name,c.id,
    (SELECT string_agg(b.value || ' ' || b.key,' + ' ORDER BY b.key) FROM jsonb_each_text(s.beds) b),
    s.area_m2,s.base_price,s.adults,s.children,s.max_guests,s.view_type,s.has_balcony,s.high_floor,
    s.short_description,s.description,'ACTIVE'::room_type_status,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM reset_room_catalog s JOIN room_categories c ON c.code=s.category_code;

INSERT INTO room_type_beds (id,room_type_id,bed_type,quantity,created_at,updated_at)
SELECT md5('hanbay-v2-bed:' || s.code || ':' || b.key)::UUID,
    md5('hanbay-v2-type:' || s.code)::UUID,b.key,b.value::INT,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM reset_room_catalog s CROSS JOIN LATERAL jsonb_each_text(s.beds) b;

INSERT INTO room_type_amenities (room_type_id,amenity_id)
SELECT md5('hanbay-v2-type:' || s.code)::UUID,a.id
FROM reset_room_catalog s
CROSS JOIN LATERAL unnest(ARRAY['Wi-Fi tốc độ cao','TV','Điều hòa','Tủ lạnh mini','Minibar',
    'Két an toàn','Máy sấy tóc','Ấm đun nước','Phòng tắm riêng','Vòi sen','Tủ quần áo']::TEXT[] || s.extra_amenities) selected(name)
JOIN amenities a ON a.name=selected.name;

-- Existing project stock photographs are illustrations, not evidence of bed layout.
INSERT INTO room_type_images (id,reference_id,room_type_id,image_url,sort_order,created_at,updated_at)
SELECT md5('hanbay-v2-image:' || s.code || ':' || p.ordinality)::UUID,
    md5('hanbay-v2-image-ref:' || s.code || ':' || p.ordinality)::UUID,
    md5('hanbay-v2-type:' || s.code)::UUID,
    'https://images.unsplash.com/' || p.photo || '?auto=format&fit=crop&w=1600&q=85',
    p.ordinality::INT-1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM reset_room_catalog s CROSS JOIN LATERAL unnest(s.photos) WITH ORDINALITY p(photo,ordinality);

UPDATE room_types rt SET thumbnail_url=i.image_url
FROM room_type_images i WHERE i.room_type_id=rt.id AND i.sort_order=0;

INSERT INTO rooms (id,room_type_id,room_number,floor,status,note,created_at,updated_at)
SELECT md5('hanbay-v2-room:' || (s.first_room+n-1))::UUID,
    md5('hanbay-v2-type:' || s.code)::UUID,(s.first_room+n-1)::TEXT,s.floor,
    'AVAILABLE'::room_status,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM reset_room_catalog s CROSS JOIN LATERAL generate_series(1,s.room_count) n;

-- Paid services are separate from room amenities. No extra-bed business is seeded.
INSERT INTO add_on_services (id,code,name,description,price,active,created_at,updated_at,quantity_unit)
SELECT md5('hanbay-v2-addon:' || a.code)::UUID,a.code,a.name,a.description,a.price,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
    CASE WHEN a.code IN ('BREAKFAST_BUFFET','EXTRA_MEAL') THEN 'SERVING'
         WHEN a.code IN ('AIRPORT_PICKUP','AIRPORT_DROPOFF') THEN 'TRIP' ELSE 'PACKAGE' END
FROM (VALUES
    ('AIRPORT_PICKUP','Đón tại sân bay','Đưa đón một chiều từ sân bay đến khách sạn theo thông tin chuyến bay đã xác nhận.',350000),
    ('AIRPORT_DROPOFF','Tiễn ra sân bay','Đưa đón một chiều từ khách sạn đến sân bay theo giờ khởi hành đã xác nhận.',350000),
    ('ROOM_DECORATION','Trang trí phòng','Trang trí phòng theo gói cơ bản và nội dung đã thống nhất với khách trước khi nhận phòng.',450000),
    ('BREAKFAST_BUFFET','Buffet sáng','Suất buffet sáng mua thêm; khách chọn số lượng khi đặt dịch vụ.',250000),
    ('EXTRA_MEAL','Bữa ăn thêm','Suất ăn mua thêm theo thực đơn áp dụng; khách chọn số lượng khi đặt dịch vụ.',250000)
) AS a(code,name,description,price);

    IF (SELECT count(*) FROM room_categories) <> 6 OR (SELECT count(*) FROM room_types) <> 8
       OR (SELECT count(*) FROM rooms) <> 35 OR (SELECT count(*) FROM room_type_beds) <> 9 THEN
        RAISE EXCEPTION 'Room catalog must contain 6 groups, 8 types, 9 bed rows and 35 physical rooms';
    END IF;
    IF EXISTS (SELECT 1 FROM reset_room_catalog s LEFT JOIN rooms r
               ON r.room_type_id=md5('hanbay-v2-type:' || s.code)::UUID
               GROUP BY s.code,s.room_count HAVING count(r.id)<>s.room_count) THEN
        RAISE EXCEPTION 'Physical inventory does not match the required 6/5/5/5/5/3/4/2 distribution';
    END IF;

END;
$seed$;

-- Safe local-only bootstrap: replace ONLY untouched historical Flyway demo rows.
-- Any edit, business transaction or custom row makes this a no-op.
CREATE FUNCTION initialize_hanbay_room_demo_catalog() RETURNS BOOLEAN LANGUAGE plpgsql AS $bootstrap$
DECLARE expected RECORD; actual_hash TEXT;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('hanbay-room-demo-bootstrap'));
    LOCK TABLE room_types, rooms, room_categories, room_type_beds, room_type_images,
        room_type_amenities, amenities, add_on_services, bookings, booking_orders,
        payments, maintenance_requests, price_rules IN SHARE ROW EXCLUSIVE MODE;
    IF EXISTS (SELECT 1 FROM bookings) OR EXISTS (SELECT 1 FROM booking_orders)
       OR EXISTS (SELECT 1 FROM payments) OR EXISTS (SELECT 1 FROM maintenance_requests)
       OR EXISTS (SELECT 1 FROM price_rules) THEN RETURN FALSE; END IF;
    IF NOT EXISTS (SELECT 1 FROM room_types) AND NOT EXISTS (SELECT 1 FROM rooms)
       AND NOT EXISTS (SELECT 1 FROM room_categories) AND NOT EXISTS (SELECT 1 FROM amenities)
       AND NOT EXISTS (SELECT 1 FROM add_on_services) THEN
        PERFORM seed_hanbay_room_demo_catalog(); RETURN TRUE;
    END IF;
    -- Stable hashes cover every column except audit timestamps, including prices,
    -- descriptions, statuses, beds, room numbers, amenities and image identities.
    FOR expected IN SELECT * FROM (VALUES
        ('room_categories','be48ffaba8d1c6f3ae43d2f85c2429b7'),
        ('room_types','5f24bcabba5d858049fadcc89aaaf287'),
        ('room_type_beds','0068fc48102d8f0ddfb97520d4e8bffb'),
        ('room_type_images','675ec5a80e69710f82e06a83dea99d66'),
        ('room_type_amenities','8c45115626aaf7907a3bea7a105ad556'),
        ('rooms','0337d87b94fb371c7563e0f3ddb91e81'),
        ('amenities','769f321a16983fd589ef98bf0a3bd9d3'),
        ('add_on_services','d41d8cd98f00b204e9800998ecf8427e')
    ) AS baseline(table_name, fingerprint) LOOP
        IF expected.table_name = 'room_categories' THEN
            SELECT md5(coalesce(string_agg(data::text,',' ORDER BY data::text),'')) INTO actual_hash
            FROM (SELECT to_jsonb(t)-'created_at'-'updated_at'-'id' AS data FROM room_categories t) x;
        ELSIF expected.table_name = 'room_types' THEN
            SELECT md5(coalesce(string_agg(data::text,',' ORDER BY data::text),'')) INTO actual_hash
            FROM (SELECT (to_jsonb(t)-'created_at'-'updated_at'-'category_id') || jsonb_build_object('category_code',c.code) AS data
                  FROM room_types t LEFT JOIN room_categories c ON c.id=t.category_id) x;
        ELSE
            EXECUTE format($query$SELECT md5(coalesce(string_agg(data::text,',' ORDER BY data::text),'')) FROM (SELECT to_jsonb(t)-'created_at'-'updated_at' AS data FROM %I t) x$query$, expected.table_name) INTO actual_hash;
        END IF;
        IF actual_hash <> expected.fingerprint THEN RETURN FALSE; END IF;
    END LOOP;
    DELETE FROM room_type_images;
    DELETE FROM room_type_beds;
    DELETE FROM room_type_amenities;
    DELETE FROM rooms;
    DELETE FROM room_types;
    DELETE FROM room_categories;
    DELETE FROM amenities;
    -- The pristine baseline contains no add-on services.
    PERFORM seed_hanbay_room_demo_catalog();
    RETURN TRUE;
END;
$bootstrap$;
