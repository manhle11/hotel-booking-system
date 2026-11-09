-- Fictional HANBAY catalog for local demonstration and acceptance testing.
-- All beds and capacities below are explicitly declared for NEW Room Types.
-- Preserve existing types, bookings, physical rooms, inventory and price rules.
CREATE TEMP TABLE seed_room_catalog (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    area_m2 DECIMAL(6, 2) NOT NULL,
    base_price BIGINT NOT NULL,
    capacity_adults INT NOT NULL,
    capacity_children INT NOT NULL,
    max_guests INT NOT NULL,
    beds JSONB NOT NULL CHECK (jsonb_typeof(beds) = 'object' AND beds <> '{}'::jsonb),
    short_description TEXT NOT NULL,
    description TEXT NOT NULL,
    extra_amenities TEXT[] NOT NULL,
    CHECK (max_guests >= capacity_adults AND max_guests <= capacity_adults + capacity_children)
) ON COMMIT DROP;

INSERT INTO seed_room_catalog VALUES
(
    'HB_STANDARD_DOUBLE', 'Standard Double - Giường đôi', 'STANDARD', 24, 1100000, 2, 0, 2,
    '{"DOUBLE": 1}',
    'Phòng Standard 24 m² với một giường đôi cố định, cửa sổ đón sáng và phòng tắm riêng, phù hợp cho hai người lớn nghỉ ngắn ngày.',
    E'Standard Double có diện tích 24 m², bố trí gọn gàng với một giường DOUBLE cố định, tủ quần áo và góc đặt hành lý. Cửa sổ hướng sân trong tạo không gian nghỉ ngơi yên tĩnh, phù hợp với khách công tác hoặc cặp đôi cần một căn phòng tiện nghi.\n\nPhòng tiếp nhận tối đa 2 khách, giới hạn 2 người lớn và 0 trẻ em. Các phòng Standard Double đều được bố trí cùng kiểu giường đôi.\n\nPhòng có điều hòa riêng, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm; bữa sáng và dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY[]::TEXT[]
),
(
    'HB_STANDARD_TWIN', 'Standard Twin - Hai giường đơn', 'STANDARD', 26, 1150000, 2, 0, 2,
    '{"SINGLE": 2}',
    'Phòng Standard 26 m² có hai giường đơn tách biệt, không gian sáng thoáng và phòng tắm riêng, thuận tiện cho bạn bè hoặc đồng nghiệp.',
    E'Standard Twin rộng 26 m², bố trí hai giường SINGLE riêng biệt cùng lối đi giữa hai giường. Cửa sổ đón sáng, tủ quần áo và khu để hành lý mang đến sự thuận tiện cho hai người muốn có không gian ngủ riêng.\n\nPhòng tiếp nhận tối đa 2 khách, giới hạn 2 người lớn và 0 trẻ em. Hai giường được bố trí tách biệt, phù hợp với khách muốn có chỗ nghỉ riêng trong cùng một phòng.\n\nTiện nghi gồm điều hòa riêng, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm; các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY[]::TEXT[]
),
(
    'HB_SUPERIOR_QUEEN', 'Superior Queen - Giường Queen', 'SUPERIOR', 32, 1650000, 2, 1, 3,
    '{"QUEEN": 1}',
    'Phòng Superior 32 m² với giường Queen cố định, bàn làm việc và cửa sổ hướng thành phố, dành cho kỳ nghỉ đôi hoặc gia đình nhỏ.',
    E'Superior Queen có diện tích 32 m² với một giường QUEEN cố định, bàn làm việc và góc ngồi thư giãn bên cửa sổ. Thiết kế sử dụng tông màu ấm, tủ quần áo riêng và khoảng để hành lý rộng hơn hạng Standard.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Một giường Queen là cấu hình cố định của hạng phòng này, phù hợp với cặp đôi hoặc gia đình nhỏ.\n\nPhòng được trang bị điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước và phòng tắm có vòi sen, máy sấy tóc. Bữa sáng cùng các dịch vụ bổ sung được lựa chọn riêng theo danh mục hiện có.',
    ARRAY['Bàn làm việc']
),
(
    'HB_SUPERIOR_TWIN', 'Superior Twin - Hai giường đơn', 'SUPERIOR', 32, 1700000, 2, 1, 3,
    '{"SINGLE": 2}',
    'Phòng Superior 32 m² có hai giường đơn, bàn làm việc và góc thư giãn, phù hợp cho đồng nghiệp, bạn bè hoặc gia đình nhỏ.',
    E'Superior Twin rộng 32 m² với hai giường SINGLE cố định, bàn làm việc và góc ngồi cạnh cửa sổ hướng thành phố. Cách bố trí tách biệt khu nghỉ ngơi và khu để hành lý giúp khách thuận tiện trong kỳ lưu trú nhiều ngày.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Loại phòng này luôn có hai giường đơn; khách muốn giường Queen chọn Superior Queen để có đúng cấu hình.\n\nTiện nghi gồm điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước, tủ quần áo và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc']
),
(
    'HB_DELUXE_KING', 'Deluxe King - Giường King, ban công', 'DELUXE', 42, 2450000, 2, 1, 3,
    '{"KING": 1}',
    'Phòng Deluxe 42 m² với giường King cố định, ban công riêng và góc thư giãn hướng biển, phù hợp cho kỳ nghỉ đôi hoặc gia đình nhỏ.',
    E'Deluxe King có diện tích 42 m², một giường KING cố định và ban công riêng hướng biển. Bên trong phòng có bàn làm việc, góc ngồi thư giãn và tủ quần áo; cửa kính lớn đưa ánh sáng tự nhiên vào khu vực nghỉ ngơi.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Một giường King là cấu hình cố định của Deluxe King, kết hợp với ban công riêng để thư giãn.\n\nPhòng có điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước, áo choàng tắm và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm; bữa sáng và các dịch vụ khác được lựa chọn riêng.',
    ARRAY['Bàn làm việc', 'Ban công riêng', 'Áo choàng tắm']
),
(
    'HB_DELUXE_TWIN', 'Deluxe Twin - Hai giường đơn, ban công', 'DELUXE', 42, 2500000, 2, 1, 3,
    '{"SINGLE": 2}',
    'Phòng Deluxe 42 m² có hai giường đơn cố định, ban công riêng và góc làm việc, dành cho khách muốn không gian ngủ tách biệt.',
    E'Deluxe Twin rộng 42 m², được bố trí hai giường SINGLE riêng biệt và ban công hướng biển. Bàn làm việc cùng góc ngồi thư giãn phù hợp cho khách vừa nghỉ dưỡng vừa cần xử lý công việc trong thời gian lưu trú.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Hai giường đơn được bố trí tách biệt, kết hợp với góc ngồi và ban công để nghỉ ngơi.\n\nTiện nghi gồm điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước, áo choàng tắm và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; bữa sáng và dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc', 'Ban công riêng', 'Áo choàng tắm']
),
(
    'HB_TRIPLE_SINGLE', 'Triple Single - Ba giường đơn', 'TRIPLE', 38, 2200000, 3, 0, 3,
    '{"SINGLE": 3}',
    'Phòng Triple 38 m² với ba giường đơn cố định, khu hành lý chung và phòng tắm riêng, phù hợp cho nhóm ba người lớn đi cùng nhau.',
    E'Triple Single có diện tích 38 m², bố trí ba giường SINGLE cố định và khu để hành lý chung. Cửa sổ đón sáng, bàn làm việc và tủ quần áo giúp nhóm bạn hoặc đồng nghiệp có không gian sinh hoạt thuận tiện trong cùng một phòng.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 3 người lớn và 0 trẻ em. Ba giường đơn được bố trí riêng biệt, thuận tiện cho nhóm ba người muốn mỗi người có một giường.\n\nPhòng có điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước và phòng tắm riêng có vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm, các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc']
),
(
    'HB_TRIPLE_MIXED', 'Triple Double & Single - Giường đôi và đơn', 'TRIPLE', 38, 2150000, 3, 0, 3,
    '{"DOUBLE": 1, "SINGLE": 1}',
    'Phòng Triple 38 m² gồm một giường đôi và một giường đơn cố định, phù hợp cho nhóm ba người muốn kết hợp hai kiểu giường.',
    E'Triple Double & Single rộng 38 m², kết hợp một giường DOUBLE và một giường SINGLE trong cùng không gian. Khu hành lý và bàn làm việc nằm cạnh cửa sổ, giữ lối đi thuận tiện giữa khu vực nghỉ ngơi và phòng tắm.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 3 người lớn và 0 trẻ em. Một giường đôi và một giường đơn là cách bố trí cố định, thuận tiện cho khách đi theo nhóm ba người.\n\nTiện nghi gồm điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước, tủ quần áo và phòng tắm có vòi sen, máy sấy tóc. Giá theo phòng mỗi đêm; các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc']
),
(
    'HB_FAMILY_QUEEN_SINGLE', 'Family Queen & Single - Gia đình', 'FAMILY', 48, 2950000, 3, 1, 4,
    '{"QUEEN": 1, "SINGLE": 1}',
    'Phòng Family 48 m² với một giường Queen, một giường đơn và góc tiếp khách, phù hợp cho gia đình cần nhiều không gian sinh hoạt.',
    E'Family Queen & Single có diện tích 48 m², gồm một giường QUEEN và một giường SINGLE cố định. Góc tiếp khách, bàn làm việc và tủ quần áo tạo thêm không gian sinh hoạt chung cho gia đình trong những kỳ nghỉ dài ngày.\n\nPhòng tiếp nhận tối đa 4 khách, giới hạn 3 người lớn và 1 trẻ em. Một giường Queen và một giường đơn tạo hai khu nghỉ ngơi thuận tiện cho gia đình đi cùng nhau.\n\nPhòng có điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước, áo choàng tắm và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; bữa sáng và các dịch vụ gia đình được lựa chọn riêng nếu có trong danh mục.',
    ARRAY['Bàn làm việc', 'Khu tiếp khách riêng', 'Áo choàng tắm']
),
(
    'HB_FAMILY_TWO_DOUBLE', 'Family Double - Hai giường đôi', 'FAMILY', 52, 3200000, 4, 0, 4,
    '{"DOUBLE": 2}',
    'Phòng Family 52 m² với hai giường đôi cố định và khu tiếp khách chung, thích hợp cho gia đình hoặc nhóm tối đa bốn người lớn.',
    E'Family Double rộng 52 m², bố trí hai giường DOUBLE cố định, khu tiếp khách và bàn làm việc. Tủ quần áo cùng khu để hành lý chung đáp ứng nhu cầu lưu trú của nhóm bạn hoặc gia đình nhiều người lớn.\n\nPhòng tiếp nhận tối đa 4 khách, giới hạn 4 người lớn và 0 trẻ em. Hai giường đôi là cách bố trí cố định, kết hợp khu tiếp khách để cả gia đình có thời gian quây quần.\n\nTiện nghi gồm điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, ấm đun nước, áo choàng tắm và phòng tắm có vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm; các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc', 'Khu tiếp khách riêng', 'Áo choàng tắm']
),
(
    'HB_JUNIOR_SUITE_KING', 'Junior Suite King - Phòng khách riêng', 'SUITE', 58, 3850000, 2, 1, 3,
    '{"KING": 1}',
    'Junior Suite 58 m² có giường King cố định, khu tiếp khách riêng và bồn tắm, dành cho khách muốn không gian nghỉ ngơi rộng rãi.',
    E'Junior Suite King có diện tích 58 m² với một giường KING cố định và khu tiếp khách riêng. Bàn làm việc, góc pha cà phê và cửa sổ lớn hướng thành phố tạo điều kiện thuận tiện cho cả nghỉ dưỡng và công tác nhiều ngày.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Khu tiếp khách có ghế ngồi thư giãn; chỗ ngủ chính là một giường King cố định.\n\nSuite có điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, máy pha cà phê, ấm đun nước, áo choàng tắm và phòng tắm có bồn tắm, vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm; dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc', 'Khu tiếp khách riêng', 'Áo choàng tắm', 'Bồn tắm', 'Máy pha cà phê']
),
(
    'HB_JUNIOR_SUITE_TWIN', 'Junior Suite Twin - Hai giường đơn', 'SUITE', 58, 3950000, 2, 1, 3,
    '{"SINGLE": 2}',
    'Junior Suite 58 m² kết hợp hai giường đơn cố định, khu tiếp khách riêng và bồn tắm, phù hợp cho bạn bè hoặc đồng nghiệp lưu trú dài ngày.',
    E'Junior Suite Twin rộng 58 m², bố trí hai giường SINGLE cố định cùng khu tiếp khách riêng. Góc làm việc và khu pha cà phê được tách khỏi lối đi chính, giúp hai khách có thể làm việc hoặc nghỉ ngơi thuận tiện.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Hai giường đơn được bố trí tách biệt, phù hợp với khách muốn không gian ngủ riêng và một khu tiếp khách chung.\n\nTiện nghi gồm điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, máy pha cà phê, ấm đun nước, áo choàng tắm và phòng tắm có bồn tắm, vòi sen, máy sấy tóc. Giá theo phòng mỗi đêm; dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc', 'Khu tiếp khách riêng', 'Áo choàng tắm', 'Bồn tắm', 'Máy pha cà phê']
),
(
    'HB_EXECUTIVE_SUITE_KING', 'Executive Suite King - Ban công hướng biển', 'SUITE', 72, 5600000, 2, 1, 3,
    '{"KING": 1}',
    'Executive Suite 72 m² với giường King, phòng khách, bàn ăn và ban công riêng hướng biển, dành cho kỳ lưu trú cần sự riêng tư.',
    E'Executive Suite King có diện tích 72 m², gồm phòng ngủ với một giường KING cố định, khu tiếp khách và bàn ăn riêng. Ban công hướng biển cùng góc làm việc tạo nên không gian sinh hoạt thoải mái cho kỳ nghỉ dài ngày hoặc chuyến công tác.\n\nPhòng tiếp nhận tối đa 3 khách, giới hạn 2 người lớn và 1 trẻ em. Phòng ngủ có một giường King cố định, kết hợp phòng khách và bàn ăn để khách thoải mái sinh hoạt.\n\nSuite có điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, máy pha cà phê, ấm đun nước, áo choàng tắm và phòng tắm có bồn tắm, vòi sen, máy sấy tóc. Giá cơ bản theo phòng mỗi đêm; các dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc', 'Khu tiếp khách riêng', 'Áo choàng tắm', 'Bồn tắm', 'Máy pha cà phê', 'Ban công riêng', 'Bàn ăn']
),
(
    'HB_FAMILY_SUITE', 'Family Suite - Hai giường Queen', 'SUITE', 68, 5200000, 4, 0, 4,
    '{"QUEEN": 2}',
    'Family Suite 68 m² với hai giường Queen cố định, khu tiếp khách và bàn ăn chung, phù hợp cho gia đình hoặc nhóm bốn người lớn.',
    E'Family Suite có diện tích 68 m², được bố trí hai giường QUEEN cố định, khu tiếp khách và bàn ăn chung. Không gian rộng cùng khu để hành lý và tủ quần áo giúp gia đình hoặc nhóm bạn có thể lưu trú thoải mái trong nhiều ngày.\n\nPhòng tiếp nhận tối đa 4 khách, giới hạn 4 người lớn và 0 trẻ em. Hai giường Queen cố định cùng khu sinh hoạt chung phù hợp với gia đình hoặc nhóm bạn muốn ở cùng một Suite.\n\nTiện nghi gồm điều hòa, Wi-Fi, Smart TV, két an toàn, tủ lạnh mini, máy pha cà phê, ấm đun nước, áo choàng tắm và phòng tắm có bồn tắm, vòi sen, máy sấy tóc. Giá cơ bản tính theo phòng mỗi đêm; dịch vụ bổ sung được lựa chọn riêng.',
    ARRAY['Bàn làm việc', 'Khu tiếp khách riêng', 'Áo choàng tắm', 'Bồn tắm', 'Máy pha cà phê', 'Bàn ăn']
);

-- Reuse amenities by exact name; never change an existing amenity or legacy association.
INSERT INTO amenities (id, name, icon)
SELECT md5('hanbay-catalog-amenity-v1:' || a.name)::UUID, a.name, a.icon
FROM (VALUES
    ('Điều hòa riêng', 'air-conditioner'),
    ('Phòng tắm riêng', 'bathroom'),
    ('Vòi sen', 'shower'),
    ('Máy sấy tóc', 'hair-dryer'),
    ('Két an toàn', 'safe'),
    ('Tủ quần áo', 'wardrobe'),
    ('Ấm đun nước', 'kettle'),
    ('Tủ lạnh mini', 'fridge'),
    ('Bàn làm việc', 'desk'),
    ('Ban công riêng', 'balcony'),
    ('Áo choàng tắm', 'bathrobe'),
    ('Khu tiếp khách riêng', 'living-room'),
    ('Bồn tắm', 'bathtub'),
    ('Máy pha cà phê', 'coffee'),
    ('Bàn ăn', 'dining')
) AS a(name, icon)
ON CONFLICT (name) DO NOTHING;

INSERT INTO room_types (
    id, code, name, category_id, area_m2, base_price, capacity_adults, capacity_children,
    max_guests, bed_type, short_description, description, status, created_at, updated_at
)
SELECT md5('hanbay-room-catalog-v1:' || s.code)::UUID, s.code, s.name, c.id, s.area_m2,
       s.base_price, s.capacity_adults, s.capacity_children, s.max_guests,
       (SELECT string_agg(b.value || ' ' || b.key, ' + ' ORDER BY b.key) FROM jsonb_each_text(s.beds) b),
       s.short_description, s.description, 'ACTIVE'::room_type_status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM seed_room_catalog s
JOIN room_categories c ON c.code = s.category_code;

INSERT INTO room_type_beds (id, room_type_id, bed_type, quantity, created_at, updated_at)
SELECT md5('hanbay-catalog-bed-v1:' || s.code || ':' || b.key)::UUID,
       md5('hanbay-room-catalog-v1:' || s.code)::UUID, b.key, b.value::INT,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM seed_room_catalog s
CROSS JOIN LATERAL jsonb_each_text(s.beds) b;

INSERT INTO room_type_amenities (room_type_id, amenity_id)
SELECT md5('hanbay-room-catalog-v1:' || s.code)::UUID, a.id
FROM seed_room_catalog s
CROSS JOIN LATERAL unnest(ARRAY[
    'Wi-Fi tốc độ cao', 'Smart TV 65"', 'Dịch vụ 24/7', 'Điều hòa riêng', 'Phòng tắm riêng',
    'Vòi sen', 'Máy sấy tóc', 'Két an toàn', 'Tủ quần áo', 'Ấm đun nước', 'Tủ lạnh mini'
]::TEXT[] || s.extra_amenities) AS selected(name)
JOIN amenities a ON a.name = selected.name;

-- Reuse the project's existing stock-photo collection as illustrations only.
-- Photos do not determine bed configuration, capacity, inventory or actual room assignments.
CREATE TEMP TABLE seed_room_catalog_photos (
    category_code VARCHAR(50) PRIMARY KEY,
    photos TEXT[] NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_room_catalog_photos VALUES
    ('STANDARD', ARRAY['photo-1566665797739-1674de7a421a', 'photo-1631049307264-da0ec9d70304', 'photo-1596394516093-501ba68a0ba6']),
    ('SUPERIOR', ARRAY['photo-1611892440504-42a792e24d32', 'photo-1568495248636-6432b97bd949', 'photo-1598928506311-c55ded91a20c']),
    ('DELUXE', ARRAY['photo-1590490360182-c33d57733427', 'photo-1618773928121-c32242e63f39', 'photo-1566665797739-1674de7a421a']),
    ('TRIPLE', ARRAY['photo-1631049307264-da0ec9d70304', 'photo-1596394516093-501ba68a0ba6', 'photo-1568495248636-6432b97bd949']),
    ('FAMILY', ARRAY['photo-1598928506311-c55ded91a20c', 'photo-1618773928121-c32242e63f39', 'photo-1590490360182-c33d57733427']),
    ('SUITE', ARRAY['photo-1582719478250-c89cae4dc85b', 'photo-1631049307264-da0ec9d70304', 'photo-1596394516093-501ba68a0ba6']);

INSERT INTO room_type_images (id, reference_id, room_type_id, image_url, sort_order, created_at, updated_at)
SELECT md5('hanbay-catalog-image-v1:' || s.code || ':' || p.ordinality)::UUID,
       md5('hanbay-catalog-image-ref-v1:' || s.code || ':' || p.ordinality)::UUID,
       md5('hanbay-room-catalog-v1:' || s.code)::UUID,
       'https://images.unsplash.com/' || p.photo || '?auto=format&fit=crop&w=1600&q=85',
       p.ordinality::INT - 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM seed_room_catalog s
JOIN seed_room_catalog_photos images ON images.category_code = s.category_code
CROSS JOIN LATERAL unnest(images.photos) WITH ORDINALITY AS p(photo, ordinality);

UPDATE room_types rt
SET thumbnail_url = images.image_url
FROM room_type_images images
JOIN seed_room_catalog s ON images.room_type_id = md5('hanbay-room-catalog-v1:' || s.code)::UUID
WHERE rt.id = images.room_type_id AND images.sort_order = 0;

-- Do not create, move, duplicate or delete physical rooms.
-- New catalog entries have zero bookable inventory until physical rooms are explicitly assigned.
