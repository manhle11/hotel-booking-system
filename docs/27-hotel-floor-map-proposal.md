# 27. Đề xuất sơ đồ khách sạn và chọn phòng khi nhận phòng

Ngày: **11/11/2026**. Nhánh: `feat/hotel-floor-map`.

**Trạng thái: đã triển khai MVP trên nhánh này.**
Tài liệu giữ thiết kế đề xuất ban đầu. Hướng dẫn sử dụng và hành vi của bản đã
triển khai nằm tại [28-hotel-floor-map.md](28-hotel-floor-map.md).

## 1. Hướng triển khai đề nghị

Dùng **sơ đồ mặt bằng 2D theo tầng**, có số phòng, hạng phòng, hành lang,
thang máy và cầu thang khi đã có thông tin mặt bằng thực tế. Cùng một bố cục
được dùng ở hai giao diện, với dữ liệu và quyền thao tác khác nhau:

| Người dùng | Mục đích | Thông tin và thao tác |
| --- | --- | --- |
| Khách truy cập | Hiểu khách sạn bố trí như thế nào | Xem tầng, vị trí, số phòng, hạng phòng; mở chi tiết hạng và tìm phòng theo ngày |
| Lễ tân | Theo dõi vận hành, chọn phòng để nhận khách | Xem trạng thái hiện tại, lọc phòng; chọn booking để đánh dấu đúng những phòng được nhận |
| Quản lý | Cấu hình bố cục thực tế | Đặt vị trí phòng và khu vực chung; lưu và công bố sơ đồ |

**Giữ nghiệp vụ đặt theo hạng phòng và gán phòng cụ thể khi check-in.**
Ở phiên bản đầu, khách xem vị trí để hiểu lựa chọn; thao tác đặt vẫn gửi
`roomTypeId`, ngày ở và số khách như hiện tại. Không cam kết số phòng cụ thể
chỉ vì khách bấm vào một ô trên sơ đồ.

Với 35 phòng, sơ đồ SVG 2D đủ để thể hiện vị trí và thao tác chọn phòng.
Phiên bản đầu chưa cần mô hình 3D hoặc trình vẽ mặt bằng chuyên dụng.

## 2. Những gì mã nguồn hiện tại đã có

| Thành phần | Hiện trạng đã đối chiếu | Cách tận dụng |
| --- | --- | --- |
| `Room` | Có `roomNumber`, `floor`, `roomType`, `status`; chưa có tọa độ hoặc bố cục | Giữ là nguồn thông tin phòng vật lý; thêm dữ liệu bố cục riêng |
| `RoomStatus` | `AVAILABLE`, `OCCUPIED`, `DIRTY`, `CLEANING`, `MAINTENANCE`, `OUT_OF_SERVICE` | Hiển thị đúng các trạng thái đã có |
| `RoomAvailabilityCalculator` | Tính tồn theo hạng, nhu cầu từng đêm và hạn giữ chỗ | Dùng kết quả hiện có cho khách tìm theo kỳ lưu trú |
| `CheckInServiceImpl` | Lọc phòng theo booking; khóa booking và phòng khi check-in | Tái sử dụng việc kiểm tra điều kiện và transaction |
| Trang check-in | Hiện cho chọn phòng bằng các nút radio | Bổ sung chế độ sơ đồ, vẫn giữ danh sách để dùng trên màn hình nhỏ hoặc khi thiếu bố cục |
| API quản lý phòng | Dành cho `MANAGER` | Tạo API đọc sơ đồ riêng cho lễ tân, không mở rộng quyền sửa phòng để phục vụ việc xem |

Danh mục demo hiện hành có **6 nhóm, 8 hạng, 35 phòng**, đối chiếu với
[danh mục phòng](data/room-type-catalog.md) và migration danh mục chuẩn:

| Tầng | Số phòng | Hạng phòng | Tổng |
| --- | --- | --- | ---: |
| 2 | 201–206 | Standard Queen | 6 |
| 3 | 301–305; 306–310 | Superior Queen; Superior Twin | 10 |
| 4 | 401–405; 406–410 | Deluxe King; Deluxe Twin | 10 |
| 5 | 501–503 | Triple Mixed | 3 |
| 6 | 601–604 | Family Double Queen | 4 |
| 7 | 701–702 | Suite King | 2 |

Đây là dữ liệu demo trong repository, không phải kết quả kiểm kê database đang chạy.
Khi triển khai, danh sách tầng/phòng lấy từ backend, không hard-code bảng trên.

## 3. Phác thảo giao diện

Phác thảo dùng tầng 3 với đúng 10 số phòng trong danh mục. **Vị trí phòng, hành lang,
thang máy và toàn bộ trạng thái vận hành trong phác thảo chỉ là minh họa.** Chưa có
mặt bằng thực tế để khẳng định phòng nào đối diện, cạnh nhau hoặc gần thang máy.

### 3.1. Khách xem sơ đồ

Đường dẫn đề xuất: `/hotel-map`, thêm liên kết “Sơ đồ khách sạn” ở trang chủ,
danh mục và chi tiết hạng phòng.

1. Chọn tầng; sơ đồ hiển thị hạng phòng bằng màu kèm tên hoặc chú giải.
2. Bấm phòng để xem số phòng, tầng, hạng, giường và ảnh minh họa của hạng.
   Ảnh và đặc điểm theo hạng cần ghi rõ là thông tin của hạng, không phải ảnh
   xác minh riêng từng phòng vật lý.
3. Bấm “Xem hạng phòng” hoặc “Tìm phòng hạng này” để đi vào luồng hiện tại.
4. Nếu có chọn ngày, hiển thị “Superior Twin còn N phòng cho kỳ đã chọn” ở
   phần thông tin hạng; không tô N phòng vật lý bất kỳ thành “còn trống”.

Sơ đồ công khai không trả trạng thái đang ở, đang dọn, lịch nhận/trả, mã booking,
tên khách, số điện thoại hoặc ghi chú nội bộ. Chỉ lược bỏ các trường này ở giao
diện là chưa đủ: backend phải có DTO công khai riêng.

### 3.2. Lễ tân xem vận hành

Đường dẫn đề xuất: `/front-desk/room-map`.

- Bộ lọc tầng, hạng, trạng thái và tìm số phòng; thống kê trạng thái theo phạm vi
  đang lọc và nhãn thời điểm cập nhật.
- Mỗi ô có số phòng, hạng, trạng thái bằng chữ và màu; chọn ô mở bảng thông tin.
- Khi chưa chọn booking, sơ đồ chỉ thể hiện tình trạng hiện tại. Không gọi tất cả
  phòng `AVAILABLE` là “có thể gán” vì còn phụ thuộc booking và lịch xung đột.
- Từ danh sách khách đến hoặc trang check-in, truyền booking đã chọn vào sơ đồ.
  Phòng đủ điều kiện có thể chọn; các phòng khác vẫn nhìn thấy và có lý do bị khóa.
- Chọn phòng mới chỉ đặt lựa chọn trên form. Nút **“Xác nhận nhận phòng”** mới gọi
  API check-in, gán phòng và chuyển trạng thái trong cùng transaction.

Khi chọn booking Superior Twin, các phòng Superior Queen vẫn bị khóa dù cùng
nhóm Superior hoặc có giường/sức chứa tương tự. Không tự nâng/hạ hạng bằng sơ đồ.

### 3.3. Quản lý cấu hình sơ đồ

Đường dẫn đề xuất: `/management/floor-plans`. Với phiên bản đầu, dùng form hoặc
lưới tọa độ để đặt ô; kéo thả có thể bổ sung sau.

Quản lý chọn tầng, chọn phòng đã tồn tại, đặt vị trí/kích thước và thêm khu vực
chung. Giao diện cho xem trước rồi **“Lưu và công bố”**. Việc chỉnh trên form
chưa thay bản công khai; backend thay toàn bộ bố cục tầng trong một transaction.
MVP không cần quy trình phê duyệt hay lưu nhiều bản nháp trên server.

Một tầng chưa có mặt bằng phải hiển thị “Chưa cấu hình sơ đồ” cùng danh sách phòng
theo tầng. Phòng mới chưa đặt vào sơ đồ xuất hiện trong mục “Chưa bố trí” để quản
lý xử lý; lễ tân vẫn chọn được qua danh sách nếu API nghiệp vụ cho phép.

## 4. Quy tắc nghiệp vụ cần giữ

### 4.1. Ba thông tin khác nhau

| Thông tin | Trả lời câu hỏi | Nguồn |
| --- | --- | --- |
| Trạng thái vận hành | Phòng vật lý đang ở tình trạng nào? | `rooms.status` |
| Tồn theo kỳ lưu trú | Còn bán được bao nhiêu phòng của hạng này cho cả kỳ? | `RoomAvailabilityCalculator` và API tìm kiếm hiện có |
| Khả năng nhận cho booking | Phòng này có được nhận cho booking đang chọn ngay lúc này? | Quy tắc trong `CheckInServiceImpl` |

Ví dụ: hôm nay 306 đang có khách nhưng trả trước kỳ ở tuần sau. Hạng Superior Twin
vẫn có thể còn tồn cho tuần sau. Ngược lại, 309 đang `AVAILABLE` không bảo đảm hạng
còn tồn cho một kỳ tương lai có nhiều booking chưa gán phòng.

Booking `PENDING` còn hạn giữ tồn ở cấp hạng; không tự gán một phòng trên sơ đồ
thành “đang giữ”. Chỉ thêm “đã phân phòng trước” khi có nghiệp vụ phân phòng trước
và lịch gán cụ thể; không thêm `RESERVED` vào `RoomStatus` ở phiên bản này.

### 4.2. Màu và điều kiện chọn

| Trạng thái hiện có | Nhãn lễ tân | Màu đề xuất | Nhận phòng ngay |
| --- | --- | --- | --- |
| `AVAILABLE` | Sẵn sàng | Xanh lá | Chỉ khi đúng hạng và vượt qua toàn bộ kiểm tra booking/lịch |
| `OCCUPIED` | Đang có khách | Đỏ | Không |
| `DIRTY` | Chờ dọn | Cam | Không |
| `CLEANING` | Đang dọn | Xanh dương nhạt | Không |
| `MAINTENANCE` | Bảo trì | Tím | Không |
| `OUT_OF_SERVICE` | Ngừng phục vụ | Xám | Không |

Viền đậm thể hiện ô đang chọn. Dấu khóa và dòng “Khác hạng”, “Trùng lịch” hoặc lý do
tương ứng thể hiện điều kiện theo booking; không ghi đè trạng thái vận hành.
Luôn có chữ hoặc biểu tượng để người dùng không phải phân biệt chỉ bằng màu.

### 4.3. Nhận, trả và thời gian

- Booking phải `CONFIRMED`; chỉ `RECEPTIONIST` được xác nhận check-in như API hiện tại.
  Cho `MANAGER` xem sơ đồ không đồng nghĩa thêm quyền nhận phòng.
- Phòng phải đúng `roomTypeId`, đang `AVAILABLE`, không có lịch gán xung đột.
  Dùng lại kiểm tra backend, không tự viết phép kiểm khác trong component SVG.
- Nhận sớm vẫn cần `earlyArrivalReason`; nhận muộn sau nửa đêm vẫn theo cửa sổ
  `lateArrivalCutoff`. Đọc `HotelStayPolicy`, không hard-code 06:00/14:00 ở sơ đồ.
- Check-in thành công: booking `CHECKED_IN`, phòng `OCCUPIED`, ghi giờ thực tế/lịch sử.
- Check-out: phòng `DIRTY`; phải qua `CLEANING` rồi `AVAILABLE` trước khi nhận khách tiếp.
  Không cho bấm đổi màu để bỏ qua quy trình dọn hoặc kết thúc bảo trì.
- Ngày ở là `[checkInDate, checkOutDate)` theo múi giờ khách sạn. Khách quá hạn trả
  vẫn chiếm phòng cho tới khi nhân viên xử lý, không tự chuyển thành sẵn sàng lúc 12:00.

Sơ đồ vận hành MVP thể hiện **hiện tại**, không có bộ chọn ngày tương lai mang ý
nghĩa lịch phòng vật lý. Lịch phân phòng theo ngày là phạm vi mở rộng riêng.

### 4.4. Hai lễ tân thao tác cùng lúc

Danh sách và sơ đồ là ảnh chụp tại thời điểm đọc; chọn ô không giữ phòng.
Giữ transaction và khóa booking/phòng trong API check-in hiện có. Hai yêu cầu nhận
cùng một phòng phải có tối đa một yêu cầu thành công; yêu cầu còn lại nhận `409`,
giao diện bỏ lựa chọn không còn hợp lệ, tải lại sơ đồ và cho chọn phòng khác.

Ở MVP, tập phòng được chọn lấy từ `GET /bookings/{bookingCode}/available-rooms`.
Nếu cần trả lý do chi tiết cho tất cả phòng, tách bộ kiểm tra dùng chung từ
`CheckInServiceImpl` cho cả truy vấn và lệnh; tránh tạo một bộ luật độc lập.
Kiểm tra cuối cùng luôn nằm trong transaction check-in.

## 5. Dữ liệu bố cục đề xuất

Giữ trạng thái vận hành trong `rooms`, không lưu bản sao trong sơ đồ. Với một
khách sạn hiện tại, có thể bắt đầu bằng hai bảng:

| Bảng | Các trường chính | Trách nhiệm |
| --- | --- | --- |
| `hotel_floor_plans` | `id`, `floor_number` unique, `name`, `grid_columns`, `grid_rows`, `version`, `published_at` | Bố cục đã công bố của một tầng; version dùng chống ghi đè |
| `hotel_floor_plan_elements` | `id`, `floor_plan_id`, `kind`, `room_id` nullable, `label`, `x`, `y`, `width`, `height` | Ô phòng hoặc khu vực chung trên lưới tọa độ |

`kind`: `ROOM`, `CORRIDOR`, `ELEVATOR`, `STAIRS`, `COMMON_AREA`.
Tọa độ và kích thước là số nguyên theo lưới, ví dụ 24 cột × 16 hàng; SVG dùng
`viewBox` tương ứng để co giãn. Chúng mô tả vị trí tương đối, không phải số đo xây dựng.

Ràng buộc khi lưu và công bố:

- `ROOM` phải liên kết một phòng tồn tại, cùng tầng với `floor_number`; mỗi phòng
  chỉ xuất hiện một lần trong sơ đồ tầng. Phần tử khác không gắn `room_id`.
- Kích thước dương, tọa độ không âm và toàn bộ phần tử nằm trong lưới. Chặn các
  ô phòng chồng nhau; hành lang/khu vực chung không che ô phòng hoặc nhãn chọn.
- Số phòng, hạng và trạng thái đọc từ `Room` qua khóa ngoại; không nhập lại vào layout.
  Không suy vị trí, hướng nhìn hay tính liền kề từ tên/số phòng.
- Có khóa ngoại và unique `(floor_plan_id, room_id)` cho ô phòng; xóa phòng đã đặt
  trong sơ đồ cần gỡ phần tử trước. Bổ sung lỗi nghiệp vụ `409 ROOM_LAYOUT_CONFLICT`
  vào quản lý phòng thay vì để lộ lỗi khóa ngoại.
- Đổi tầng của phòng đã bố trí cũng cần gỡ bố trí trước hoặc triển khai thao tác di
  chuyển đồng bộ riêng. Đổi số phòng chỉ cập nhật nhãn vì layout tham chiếu bằng UUID.
- PUT gửi `expectedVersion`; bản đã bị người khác sửa trả `409 FLOOR_PLAN_VERSION_CONFLICT`.
  Cập nhật các phần tử, version và `published_at` trong cùng transaction.

Thiếu bố cục không ảnh hưởng tồn phòng hoặc chặn check-in qua danh sách. Không chạy
reset danh mục để thêm sơ đồ. Dữ liệu ví dụ chỉ được dùng rõ ràng cho môi trường demo;
database có phòng tùy chỉnh cần cấu hình dựa trên phòng thực tế.

## 6. API và giao diện đề xuất

Các đường dẫn API bên dưới có tiền tố `/api/v1`.

| API | Quyền | Kết quả hoặc tác dụng |
| --- | --- | --- |
| `GET /hotel-map` | Công khai, đề xuất mới | Danh sách tầng, bố cục đã công bố và thông tin phòng công khai; không có dữ liệu vận hành |
| `GET /front-desk/room-map?floor=3` | `RECEPTIONIST`, `MANAGER`, đề xuất mới | Bố cục tầng, phòng chưa bố trí, trạng thái hiện tại, `serverNow`; không cần dữ liệu định danh khách ở MVP |
| `GET /management/floor-plans/{floorNumber}` | `MANAGER`, đề xuất mới | Bố cục hiện hành, version và các phòng chưa bố trí |
| `PUT /management/floor-plans/{floorNumber}` | `MANAGER`, đề xuất mới | Kiểm tra, lưu và công bố toàn bộ bố cục tầng với `expectedVersion` |
| `GET /bookings/{bookingCode}/available-rooms` | `RECEPTIONIST`, đã có | Tập `roomId` đủ điều kiện nhận cho booking ở thời điểm truy vấn |
| `POST /bookings/{bookingCode}/check-in` | `RECEPTIONIST`, đã có | Nhận `{ roomId, earlyArrivalReason? }`; gán phòng và check-in |
| `GET /room-types/available` | Công khai, đã có | Tồn và giá theo kỳ lưu trú, số khách |

DTO công khai chỉ gồm thông tin bố cục, nhãn số phòng, liên kết hạng và thông tin hạng
được phép công khai; không cần trả UUID phòng vật lý. DTO nội bộ trả thêm `roomId`,
`operationalStatus`, `roomTypeId`. Lễ tân ghép tập phòng khả dụng bằng `roomId`.
Các API trả đủ phòng của tầng, tránh chỉ lấy trang đầu trong API danh sách hiện tại.

Cần thêm route công khai chính xác cho `GET /hotel-map` trong `SecurityConfig`, cùng
kiểm tra quyền ở controller cho API nội bộ. Không đổi chính sách ADMIN hiện có:
ADMIN quản trị tài khoản, không mặc nhiên được vào nghiệp vụ vận hành khách sạn.

Frontend đề xuất dùng chung `HotelFloorMap` để vẽ bố cục; truyền chế độ hiển thị và
dữ liệu theo quyền, không tự tính tồn. Tách trang khách, trang lễ tân và form quản lý.
Trên mobile, dùng sơ đồ cuộn/phóng vừa khung và danh sách thay thế; hỗ trợ bàn phím,
nhãn đọc màn hình và xem chi tiết bằng chạm thay vì chỉ hover.

## 7. Cập nhật dữ liệu và xử lý lỗi

- Sơ đồ lễ tân đọc lại mỗi **15 giây** khi tab đang hoạt động; làm mới khi focus hoặc
  reconnect. Khi đang chọn booking, danh sách phòng khả dụng cũng được làm mới và
  đối chiếu cùng ngữ cảnh booking; không giữ ô chọn sau khi đổi mã booking.
- Hiển thị thời điểm cập nhật. Khi mất kết nối hoặc truy vấn lỗi, ghi rõ dữ liệu cũ
  và chặn xác nhận từ chế độ sơ đồ tới khi tải lại thành công. API vẫn kiểm tra lại
  khi ghi, kể cả giao diện vừa tải xong.
- Sau nhận/trả, bắt đầu/kết thúc dọn, bảo trì hoặc cập nhật phòng: invalidate sơ đồ,
  phòng khả dụng của booking, tồn hạng và danh sách nghiệp vụ liên quan trên client.
  Các máy khác nhận thay đổi ở lần polling tiếp theo.
- Sau sửa bố cục: invalidate sơ đồ công khai và nội bộ. Cache phải tách dữ liệu công
  khai và dữ liệu theo quyền; không dùng cache công khai cho response lễ tân.
- Gom truy vấn phòng và hạng theo tầng, không gọi một request cho từng ô. Chỉ tải
  bố cục đang xem trên giao diện lễ tân. SSE/WebSocket là bước sau nếu polling chưa đủ.

## 8. Chia việc để triển khai

| Bước | Backend | Frontend | Kết quả nghiệm thu |
| --- | --- | --- | --- |
| 1. Chốt mặt bằng | Đối chiếu phòng/tầng thực tế, thiết kế DTO và migration | Xác nhận bố cục và nội dung hiển thị | Có sơ đồ vị trí được chủ dự án xác nhận hoặc trạng thái chưa cấu hình rõ ràng |
| 2. Bố cục và khách xem | Hai bảng layout, API công khai và API quản lý có version | Form cấu hình đơn giản, trang khách và chi tiết ô | Khách xem bố trí; quản lý công bố; không lộ vận hành |
| 3. Lễ tân và nhận phòng | API trạng thái theo tầng; tận dụng bộ kiểm tra check-in | Sơ đồ vận hành, bộ lọc, chọn phòng trong check-in, polling | Nhận khách bằng sơ đồ với cùng điều kiện nghiệp vụ hiện có |
| 4. Hồi quy | Quyền, transaction, đồng thời, tác động quản lý phòng | Cache, lỗi mạng, 409, mobile và bàn phím | Vượt qua các ca bên dưới và kiểm tra build/lint hiện hành |

Sau MVP mới xem xét phân phòng trước, yêu cầu ưu tiên vị trí, đổi phòng đang ở,
đảm bảo hai phòng liền kề hoặc lịch phòng theo nhiều ngày. Những tính năng này cần
lịch gán phòng, quy tắc thay đổi, lịch sử và xử lý xung đột riêng. Đơn đặt nhiều
phòng hiện có vẫn nhận từng booking con; sơ đồ có thể hỗ trợ thao tác từng phòng,
không hứa gán cả nhóm nguyên tử khi backend chưa có transaction nhận cả nhóm.

## 9. Kịch bản nghiệm thu cần có khi triển khai

Đây là **kịch bản dự kiến, chưa phải kết quả test**.

| Ca | Kết quả cần đạt |
| --- | --- |
| Danh mục demo đủ 35 phòng | Tất cả phòng xuất hiện trong sơ đồ hoặc mục chưa bố trí; đúng tầng/hạng, không đếm trùng |
| Khách đọc trực tiếp API công khai | Không có trạng thái vận hành, booking, dữ liệu cá nhân hoặc ghi chú nội bộ |
| Khách/housekeeper/ADMIN gọi API sơ đồ lễ tân | Bị từ chối theo phân quyền; frontend và backend thống nhất |
| Quản lý xem sơ đồ rồi gọi API check-in | Xem được nhưng không tự có quyền check-in nếu không có vai trò lễ tân |
| Booking Superior Twin chọn phòng Superior Queen đang sẵn sàng | Không chọn được; gọi API trực tiếp cũng bị từ chối |
| Phòng bẩn, đang dọn, bảo trì, ngừng phục vụ hoặc đang có khách | Không nhận được dù ô đã được chọn từ dữ liệu cũ |
| Phòng AVAILABLE có lịch gán trùng kỳ booking | Không được nhận; trạng thái AVAILABLE riêng lẻ không đủ |
| Hai lễ tân nhận cùng phòng cho hai booking | Một thành công, một `409`; không tạo hai khách đang ở một phòng |
| Booking còn PENDING hoặc sai ngày nhận | Không nhận được; sơ đồ không bỏ qua xác nhận và thời gian |
| Nhận sớm và đến muộn sau nửa đêm | Giữ đúng lý do và cửa sổ giờ của `HotelStayPolicy` |
| Booking chưa gán phòng giữ tồn, sau đó hết hạn | Tồn hạng đổi đúng, không tự tô một phòng cụ thể thành đang giữ |
| Tìm kỳ tương lai, phòng hiện tại đang có khách | Kết quả dựa vào tồn từng đêm; không dựa vào số ô AVAILABLE |
| Check-out rồi dọn phòng | Sơ đồ lần lượt phản ánh DIRTY → CLEANING → AVAILABLE |
| Khách quá hạn trả hoặc gia hạn | Phòng không tự rảnh; giữ kiểm tra tồn và lịch phòng hiện có |
| Tầng chưa cấu hình hoặc vừa thêm phòng mới | Có danh sách thay thế; không giấu phòng hoặc tự đoán vị trí |
| Đổi tầng/xóa phòng đang có phần tử bố cục | Trả lỗi nghiệp vụ rõ ràng; không làm hỏng tham chiếu sơ đồ |
| Hai quản lý lưu cùng version | Yêu cầu sau trả `409`; không ghi đè âm thầm |
| Mất mạng, đổi booking, dữ liệu cũ hoặc API trả 409 | Cảnh báo, bỏ lựa chọn không hợp lệ và tải lại đúng ngữ cảnh |
| Đặt nhiều phòng | Mỗi booking con nhận đúng hạng; không tự cam kết phòng liền kề |
| Mobile, bàn phím và không phân biệt được màu | Xem/chọn qua nhãn, focus và danh sách thay thế |

## 10. Mã nguồn tham chiếu

- [Room](../hotel-booking/src/main/java/com/hanbay/hotel/room/domain/Room.java),
  [RoomStatus](../hotel-booking/src/main/java/com/hanbay/hotel/room/domain/RoomStatus.java).
- [RoomAvailabilityCalculator](../hotel-booking/src/main/java/com/hanbay/hotel/room/service/RoomAvailabilityCalculator.java).
- [CheckInServiceImpl](../hotel-booking/src/main/java/com/hanbay/hotel/booking/service/impl/CheckInServiceImpl.java),
  [CheckInController](../hotel-booking/src/main/java/com/hanbay/hotel/booking/controller/CheckInController.java).
- [Trang check-in hiện tại](../client/src/features/booking/pages/check-in-guest-page.tsx),
  [Router](../client/src/app/router.tsx).
- [SecurityConfig](../hotel-booking/src/main/java/com/hanbay/hotel/common/config/SecurityConfig.java),
  [RoomManagementService](../hotel-booking/src/main/java/com/hanbay/hotel/room/service/RoomManagementService.java).
- [Danh mục chuẩn](../hotel-booking/src/main/resources/db/migration/V202611071800__canonical_local_room_catalog.sql),
  [Thời gian và tồn phòng](23-stay-time-and-inventory.md),
  [Đặt nhiều phòng](24-multi-room-booking.md).
