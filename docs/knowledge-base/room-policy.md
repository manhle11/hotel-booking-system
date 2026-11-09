---
documentId: HANBAY-ROOM-POLICY
title: Room Policy
category: ROOM
source: docs/knowledge-base/room-policy.md
version: 1.6
hotel: HANBAY HOTEL
language: vi
---

# HANBAY HOTEL — Chính sách phòng và availability

## 1. Mục đích

Phân biệt room type, phòng vật lý, availability và trạng thái vận hành.

## 2. Phạm vi

Áp dụng cho catalog room type, tìm phòng trống và vòng đời phòng vật lý trong hệ thống demo.

## 3. Quy định

### 3.1 Room type và phòng vật lý

Room type mô tả hạng phòng, giá cơ sở, sức chứa và tiện nghi. Phòng vật lý có số phòng, tầng,
room type và trạng thái vận hành riêng.

Room Category là nhóm hiển thị; Room Type là lựa chọn đặt riêng có cấu hình giường cố định;
Room là phòng vật lý thuộc đúng một Room Type. Cấu hình demo hiện hành gồm 6 nhóm,
8 Room Type và 35 phòng vật lý: Standard Queen (6), Superior Queen (5), Superior Twin (5),
Deluxe King (5), Deluxe Twin (5), Triple Mixed (3), Family Double Queen (4), Suite King (2).
Triple Mixed có 1 QUEEN + 1 SINGLE; Family Double Queen có 2 QUEEN. Twin có 2 SINGLE.

Giường và số lượng lưu trong room_type_beds. Người lớn, trẻ em và tổng tối đa được khai báo
riêng trên Room Type, không suy từ giường. Diện tích, hướng nhìn, ban công, tầng cao là
thuộc tính riêng. Wi-Fi, TV, điều hòa, tủ lạnh, minibar là tiện nghi; đưa đón, trang trí phòng
và bữa ăn mua thêm thuộc add_on_services. Không có nghiệp vụ giường phụ.

Giảm sức chứa bị chặn khi có booking CONFIRMED sắp tới, PENDING còn hạn hoặc CHECKED_IN
kể cả quá hạn trả đang vượt giới hạn mới. Giường của Room Type có lịch sử booking vẫn
khóa; tạo loại mới để thay cấu hình an toàn. Family Double Queen cho tổng 4 khách với
giới hạn 4 người lớn/2 trẻ em; không cộng hai giới hạn thành 6 khách.

Tìm phòng hỗ trợ lọc hướng sân trong/thành phố/biển, có/không ban công và tầng cao.
Dữ liệu đặc điểm chưa xác minh không được coi là không có. Các bộ lọc không thay cách tính tồn.

### 3.2 Availability theo khoảng ngày

Khả dụng để đặt được tính theo từng đêm trong khoảng `[checkInDate, checkOutDate)`.
Mỗi đêm trừ booking `CONFIRMED`, `CHECKED_IN` và `PENDING` còn hạn giữ chỗ. Số phòng
có thể đặt cho cả kỳ là số còn lại thấp nhất trong các đêm, không trừ gộp mọi booking
chạm khoảng ngày. Booking vẫn `CHECKED_IN` sau giờ trả phòng tiếp tục chiếm tồn kho
cho đến khi lễ tân xử lý; không tự giải phóng phòng vì đã qua ngày trả dự kiến.
Trạng thái hiện tại của một phòng vật lý không thay thế phép tính booking overlap.

Trên thẻ kết quả tìm kiếm, nhãn “Còn … phòng” hiển thị tồn kho có thể đặt theo khoảng
ngày nhận/trả phòng đã chọn (`availableRooms`). Số phòng có trạng thái `AVAILABLE`
hiện tại (`readyRooms`) là chỉ số vận hành riêng.

### 3.3 Trạng thái phòng

Các trạng thái là `AVAILABLE`, `OCCUPIED`, `DIRTY`, `CLEANING`, `MAINTENANCE`, `OUT_OF_SERVICE`.
Nguồn cung để tính tồn kho gồm `AVAILABLE`, `OCCUPIED`, `DIRTY`, `CLEANING`; loại các phòng
`MAINTENANCE` và `OUT_OF_SERVICE` khỏi số phòng có thể bán. Check-in chỉ chọn phòng `AVAILABLE`.
Luồng lưu trú/housekeeping canonical:

`AVAILABLE -> OCCUPIED -> DIRTY -> CLEANING -> AVAILABLE`.

Maintenance là nhánh riêng: `AVAILABLE -> MAINTENANCE -> AVAILABLE`.

### 3.4 Maintenance

Chỉ phòng `AVAILABLE` được bắt đầu maintenance. Chỉ phòng `MAINTENANCE` được kết thúc
maintenance. Thao tác maintenance hiện dành cho `MANAGER`.

Quản lý tạo phiếu bảo trì cho phòng trống. Phiếu đi qua `PENDING` (chờ xử lý),
`IN_PROGRESS` (đang xử lý), rồi `COMPLETED` (hoàn thành). Phòng chuyển sang bảo trì
ngay khi tạo phiếu và chỉ trở lại trống khi hoàn tất phiếu. Mỗi phòng chỉ có một phiếu mở.
API đổi trạng thái phòng trực tiếp không được đóng bảo trì nếu phòng còn phiếu chưa hoàn tất.

### 3.5 Xóa phòng vật lý và bảo toàn lịch sử

Chỉ phòng AVAILABLE chưa được booking nào tham chiếu qua phòng được gán mới có thể xóa.
Phòng đã có liên kết booking được giữ lại để đơn cũ không mất số phòng đã ở; API xóa trả
409 `ROOM_HAS_BOOKING_HISTORY`. Quy tắc này áp dụng cho mọi trạng thái booking tham chiếu
phòng đó, không chỉ booking đang ở. Phòng khác chưa được dùng trong cùng Room Type vẫn
có thể xóa nếu không còn nhu cầu đặt phòng chưa hoàn tất.

Đổi Room Type hoặc xóa phòng làm giảm nguồn phòng bị chặn khi loại cũ còn CONFIRMED có
ngày trả từ hôm nay, PENDING có ngày trả từ hôm nay và expiredAt > now, hoặc CHECKED_IN
kể cả quá ngày trả. Đúng expiredAt, PENDING không còn chặn thao tác; không cần chờ scheduler
đổi nhãn. Ngày và giờ lấy từ policy khách sạn, cùng khóa Room Type/phòng vật lý như trước.

## 4. Quy trình

1. Search tính availability theo room type, ngày và occupancy.
2. Check-in chọn một phòng vật lý `AVAILABLE` đúng room type.
3. Check-out đưa phòng `OCCUPIED` về `DIRTY`.
4. Housekeeping đưa `DIRTY` qua `CLEANING` về `AVAILABLE`.

## 5. Các trường hợp không hợp lệ

- Đồng nhất “room type còn inventory” với “mọi phòng vật lý đều AVAILABLE”.
- Bắt đầu maintenance trên phòng không `AVAILABLE`.
- Kết thúc maintenance trên phòng không `MAINTENANCE`.
- Bypass các domain transition bằng một trạng thái tự suy diễn.

## 6. Ví dụ

Một Deluxe có thể còn inventory cho tháng sau dù một phòng Deluxe cụ thể đang `OCCUPIED`
hôm nay; availability đặt phòng được tính theo overlap của khoảng ngày yêu cầu.

## 7. Source of Truth

- `docs/01-project-overview.md`
- `docs/03-software-requirements.md`
- `docs/04-system-architecture.md`
- `docs/07-business-workflows.md`
- `Room.java`, `RoomStatus.java`, `RoomType.java`
- `AvailabilityServiceImpl.java`, `RoomMaintenanceServiceImpl.java`

### Tồn hiển thị, liên kết và dữ liệu inventory

Nhấn tên hạng ở kết quả mở /room-types/{id}; nhãn Còn…phòng dùng availableRooms của kỳ đã tìm.
Ví dụ2 phòng, A ở05→06/11, B ở06→07/11 vẫn còn 1 cho05→07/11. Không trừ cảA vàB như
hai phòng bận đồng thời. Đúng expiredAt pending không giữ tồn; MAINTENANCE/OOS không bán.
Khách ở quá hạn không tự nhả phòng. Bộ demo 35 phòng giữ nguyên số lượng khi restart;
reset dữ liệu chỉ chạy chủ động theo yêu cầu, không tự chạy lúc khởi động.
Nguồn: RoomAvailabilityCalculator, BookingRepository, scripts/reset-room-catalog.sql;
[danh mục phòng](../data/room-type-catalog.md), [tài liệu23](../23-stay-time-and-inventory.md).
