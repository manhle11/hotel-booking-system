# Kiểm tra trước khi push — 09/11/2026

Nhánh: `feat/room-category-bed-configuration`.

**Cập nhật sau sửa:** đã xử lý cả **2 lỗi và 1 rủi ro mất thông tin lịch sử** tìm thấy trong lượt rà soát sâu, kèm test hồi quy trong source. Chi tiết bản sửa và kết quả kiểm tra được ghi ở cuối báo cáo. Chưa chạy GitHub Actions trên remote vì chưa commit/push.

## Phạm vi đã đối chiếu

- Phân cấp Category → Room Type → phòng vật lý; cấu hình giường độc lập với sức chứa và tồn phòng.
- SINGLE/DOUBLE/QUEEN/KING và số lượng; chống cấu hình trùng; đọc dữ liệu cũ không đoán giường.
- Khóa thay giường khi có lịch sử booking; chặn giảm sức chứa gây xung đột; ngừng bán và mở lại qua trạng thái.
- Chính sách trẻ em 6/12 tuổi, Family tổng tối đa 4; lưu tuổi và tương thích request/booking cũ.
- Đơn vị và snapshot dịch vụ; tự cập nhật số suất/tiền khi nhập người × ngày; chặn ngày không hợp lệ.
- Bộ lọc đặc điểm, phân trang, quyền quản lý, migration, bootstrap local và bảo toàn dữ liệu đã phát sinh.

Chi tiết thiết kế, các lỗi đã sửa và phần dư thừa đã dọn: [review 08/11](room-catalog-review-2026-11-08.md). Danh mục hiện hành và hướng dẫn: [room-type-catalog.md](../data/room-type-catalog.md), [room-business-fixes.md](../testing/room-business-fixes.md).

## Kết quả trước ba bản sửa bổ sung

| Kiểm tra | Kết quả |
| --- | --- |
| Backend `./mvnw -o clean verify` | **838 test đạt**, 134 suite; 0 failure/error/skipped; đóng gói và Spotless đạt |
| Bản sao source backend không có `.env`/`application-local.yml` | `verify` đạt; **59 test** thuộc bootstrap/cấu hình phòng/đơn đặt phòng đạt, 0 bỏ qua |
| Frontend `npm run test:run` | **314 test đạt**, 61 file trong lượt chạy lại toàn bộ |
| Frontend `npm run build` | TypeScript và Vite đạt |
| Frontend `npm run lint` | Đạt |
| Frontend `npm run format:check` | Đạt trên toàn bộ frontend |
| `git diff --check`, liên kết nội bộ trong Markdown thay đổi | Đạt; không có đích liên kết bị thiếu |
| JAR sau clean build | Đủ 5 migration mới; chỉ còn `RoomCatalogInitializer`, không còn ba initializer phòng/tiện nghi/dịch vụ cũ |

Bản sao source được tạo từ danh sách file Git theo dõi và file mới không bị ignore, bao gồm tài liệu knowledge base. Bộ 59 test kiểm tra riêng `RoomCatalogInitializerConfigurationTest`, `RoomCatalogInitializerIntegrationTest`, `RoomTypeConfigurationIntegrationTest`, `BookingOrderIntegrationTest`; đây là kiểm tra bổ sung, không thay thế bộ 838 test đầy đủ. Integration test dùng database PostgreSQL Testcontainers riêng.

Lượt frontend đầu có một test kích hoạt lại tài khoản bị timeout 5 giây khi chạy đồng thời với backend/build. Chạy riêng file tài khoản đạt 8/8, sau đó chạy lại toàn bộ frontend đạt 314/314. Không tái hiện lỗi chức năng từ timeout này. Vẫn có cảnh báo không chặn kiểm tra từ jsdom/Node và kích thước bundle Vite.

## Khi đưa code lên Git

- Đưa cả file mới vào commit, đặc biệt 5 migration `202611071000`–`202611071800`, class/domain/DTO và component/utility/test mới. `git diff` thông thường không hiển thị nội dung file chưa được theo dõi.
- Giữ nguyên nội dung các migration đã áp dụng; thay đổi schema tiếp theo dùng migration mới.
- `.env`, cấu hình local riêng và `.local-backups/` đang được ignore; không cần đưa chúng vào Git.
- Reset dữ liệu là script thao tác riêng. Không chạy reset để deploy; Flyway áp dụng migration khi khởi động theo cấu hình hiện có. Bootstrap danh mục demo chỉ chạy profile `local` và giữ lại dữ liệu đã tùy chỉnh/phát sinh nghiệp vụ.

Thông số giá/diện tích/sức chứa/ảnh vẫn là dữ liệu demo, cần khách sạn xác nhận trước khi bán phòng thực tế. Lượt này xác minh source và test/build; không chạy thanh toán ngân hàng thật hoặc UAT trực tiếp trên trình duyệt. Kết quả kiểm tra trình duyệt ngày 08/11 được ghi riêng trong tài liệu review/test.

Lượt kiểm tra ban đầu chỉ bổ sung báo cáo và cập nhật kết quả trong tài liệu danh mục. Lượt sửa theo yêu cầu tiếp theo thay đổi runtime và thêm test như phần cuối báo cáo; không reset/sửa database local, không commit và không push.

## Rà soát sâu theo yêu cầu tiếp theo ngày 09/11/2026

Các test tái hiện được viết và chạy trong bản sao source ở `/private/tmp`, không thêm test thất bại vào working tree. PostgreSQL Testcontainers riêng được dùng cho các thao tác dữ liệu. Hai điểm quản lý phòng vật lý và thiếu invalidation cache đã có trong source nền; đây là các vấn đề còn tồn tại của luồng liên quan, không phải bằng chứng migration giường làm hỏng tồn phòng.

### 1. Rủi ro: xóa phòng vật lý làm mất số phòng trong chi tiết đơn cũ

- Vị trí: [RoomManagementService.delete](../../hotel-booking/src/main/java/com/hanbay/hotel/room/service/RoomManagementService.java#L74), [FK assigned room](../../hotel-booking/src/main/resources/db/migration/V202607291337__create_booking_domain_tables.sql#L100), [BookingOrderService.toResponse](../../hotel-booking/src/main/java/com/hanbay/hotel/booking/service/BookingOrderService.java#L369).
- Tái hiện: khách đã trả phòng, phòng được dọn về AVAILABLE, không còn nhu cầu booking sắp tới; quản lý xóa phòng vật lý này.
- Thực tế: thao tác xóa được chấp nhận, `bookings.assigned_room_id` của booking lịch sử chuyển thành NULL theo `ON DELETE SET NULL`. API lấy số phòng từ quan hệ này nên đơn cũ không còn hiển thị số phòng đã ở. Booking, giá và cấu hình giường vẫn được giữ.
- Xác nhận bằng test `reviewCannotPhysicallyDeleteRoomReferencedOnlyByHistoricalBooking`: kiểm tra bảo toàn ID phòng thất bại vì nhận NULL. Log: `/private/tmp/room-deeper-review-history.log`.
- Đề xuất: dùng trạng thái OUT_OF_SERVICE đã có cho phòng từng phục vụ khách và chặn xóa cứng, hoặc bổ sung snapshot số phòng tại check-in trước khi cho phép xóa. Không tự dựng lại số phòng cho booking đã mất liên kết. Nếu thêm cột snapshot phải dùng migration mới; không sửa migration FK đã áp dụng.
- Đây là rủi ro trong cơ chế phòng vật lý hiện có. Việc mở rộng cách xóa/lưu lịch sử cần được triển khai riêng có test; lượt review này giữ nguyên cơ chế theo phạm vi đã thống nhất.

### 2. Lỗi: sửa/ngừng bán Room Type chưa làm mới đúng cache công khai và checkout

- Vị trí: [RoomTypeManagementPage.refresh](../../client/src/features/inventory/room-type-management-page.tsx#L97). Hàm invalidation gọi `room-types` và `available-room-types` nhưng danh mục dùng [room-type-catalog](../../client/src/features/room/api/room-type-query.ts#L6), còn màn đặt nhiều phòng dùng [order-room-types](../../client/src/features/booking/pages/multi-room-booking-page.tsx#L77).
- Tái hiện: mở danh mục/checkout để tạo cache, vào quản lý đổi giá/tên hoặc ngừng bán một loại, quay lại ngay trong cùng phiên ứng dụng.
- Thực tế: hai query trên vẫn `isInvalidated=false`. Với `staleTime=60_000` và `refetchOnWindowFocus=false` của ứng dụng, có thể tiếp tục hiển thị dữ liệu cũ trong thời gian cache còn mới; màn đang mở không được chủ động refetch chỉ vì đã qua staleTime. Backend vẫn kiểm tra trạng thái/tồn khi tạo booking, nên chưa có bằng chứng bán vượt tồn từ lỗi cache này.
- Xác nhận bằng test UI `review refreshes catalog and checkout queries after changing a room type`: cả hai giá trị nhận được đều false thay vì true. Log: `/private/tmp/room-deeper-review-frontend.log`.
- Đề xuất: dùng chung query key và invalidate cả catalog/checkout sau sửa, ngừng bán, mở bán lại; bổ sung test kiểm tra cache và quay lại màn công khai. Cơ chế này chỉ cập nhật QueryClient trong phiên hiện tại; tab/thiết bị khác cần refetch riêng.

### 3. Lỗi: booking PENDING hết hạn vẫn chặn đổi loại/xóa phòng vật lý

- Vị trí: [RoomManagementService.requireNoDemand](../../hotel-booking/src/main/java/com/hanbay/hotel/room/service/RoomManagementService.java#L86).
- Tái hiện: một booking nhận phòng trong tương lai vẫn mang trạng thái PENDING nhưng `expired_at` đã qua; phòng vật lý đang AVAILABLE và không có booking còn hiệu lực khác. Đổi phòng sang một Room Type đang hoạt động.
- Thực tế: availability xác nhận phòng không bị giữ, nhưng truy vấn quản lý vẫn đếm booking này và từ chối thay đổi, cho tới khi scheduler chuyển trạng thái. Truy vấn chưa xét `expired_at`, dùng ngày database thay cho cùng đồng hồ/múi giờ nghiệp vụ.
- Xác nhận bằng test `reviewExpiredPendingMustNotBlockMovingAnAvailablePhysicalRoom`: availability trả 1, update vẫn ném `RoomValidationException`. Log: `/private/tmp/room-deeper-review-backend.log`.
- Đề xuất: đồng bộ điều kiện booking còn nhu cầu với policy về hạn giữ chỗ và thời gian khách sạn, giữ khóa Room Type hiện có. Thêm test pending còn hạn/hết hạn, khách đang ở quá hạn và booking lịch sử trước khi điều chỉnh guard. Không thay phép tính tồn để giải quyết lỗi này.

## Những phần nên bổ sung

1. **Chi tiết dịch vụ sau tạo đơn:** `BookingOrderResponse.Room` hiện chỉ có tổng tiền, chưa trả từng dịch vụ/số lượng/đơn vị/đơn giá/thành tiền đã snapshot. Khách chọn 6 suất ăn nhưng khi sang đơn hoặc tải lại trang chỉ thấy tổng tiền; nên trả và hiển thị các dòng này cho từng phòng. Nếu cần lưu cả tên dịch vụ lịch sử, bổ sung snapshot tên; không lấy giá hiện tại để dựng lại hóa đơn cũ.
2. **Cách đặt đặc điểm về “chưa xác minh”:** dữ liệu có NULL nhưng form chỉ cho Có/Không, hướng nhìn không có lựa chọn bỏ xác nhận; `RoomType.updateFeatures` bỏ qua NULL. Muốn sửa một khai báo nhầm về trạng thái chưa biết hiện chưa có thao tác rõ ràng. Cần phân biệt trường bị bỏ qua với yêu cầu xóa giá trị để giữ tương thích client cũ.
3. **Test hồi quy cho các tình huống vừa tìm thấy:** bổ sung cùng bản sửa; bộ 838/314 test hiện tại không chứng minh các tình huống này đã đúng.

Thông số phòng mẫu và kiểm tra phòng khách sạn thực tế vẫn là giới hạn đã nêu trong báo cáo trước. Lượt rà soát sâu chưa tìm thấy bằng chứng mới về nhân đôi tồn từ cấu hình giường. Các mô tả lỗi ở trên phản ánh thời điểm trước bản sửa bên dưới.

## Bản sửa ba điểm theo yêu cầu

1. **Giữ liên kết phòng lịch sử:** sau khi khóa Room Type/phòng vật lý, kiểm tra mọi booking có `assigned_room_id` là phòng đang xóa. Nếu có, trả HTTP 409 `ROOM_HAS_BOOKING_HISTORY` và giữ nguyên phòng/booking. Một phòng chưa được booking tham chiếu vẫn xóa được dù phòng khác cùng loại có lịch sử. Không thêm nghiệp vụ chuyển OUT_OF_SERVICE hoặc thay FK/migration cũ. Bản sửa ngăn mất liên kết trong các lần xóa tiếp theo; không tự khôi phục số phòng đã bị xóa trước đây.
2. **Làm mới cache:** dùng query key chung cho catalog/checkout, invalidate cả hai sau sửa, ngừng bán hoặc mở lại Room Type. Giữ invalidation của chi tiết, bộ chọn phòng và tìm phòng đã có. Kiểm tra QueryClient thật với cache mới 60 giây; cache dịch vụ không bị invalidation ngoài phạm vi.
3. **Điều kiện nhu cầu còn hiệu lực:** chuyển truy vấn quản lý sang BookingRepository, lấy một mốc thời gian từ HotelStayPolicy thay cho CURRENT_DATE của database. PENDING chỉ chặn khi `expired_at > now`; đúng hạn hoặc không có expiry không giữ tồn theo cơ chế availability hiện có. CONFIRMED từ hôm nay và CHECKED_IN kể cả quá ngày trả vẫn chặn giảm nguồn phòng. Giữ thứ tự khóa Room Type → phòng vật lý và cách tính tồn hiện hành.

Không có thay đổi schema hay migration mới trong ba bản sửa. Các đề xuất chi tiết dịch vụ sau tạo đơn và đặc điểm “Chưa xác minh” là công việc bổ sung riêng, chưa triển khai trong lượt này.

Test mới:

- [RoomInventoryManagementIntegrationTest](../../hotel-booking/src/test/java/com/hanbay/hotel/room/RoomInventoryManagementIntegrationTest.java): 17 trường hợp PostgreSQL/HTTP, gồm mọi trạng thái booking tham chiếu phòng, xóa phòng chưa dùng, pending còn hạn/quá hạn/đúng hạn/không có expiry, khách ở quá hạn và ngày khách sạn khác ngày UTC.
- [RoomTypeManagementPage](../../client/src/features/inventory/room-type-management-page.test.tsx): thêm 3 trường hợp sửa giá, mở bán lại và ngừng bán, kiểm tra cache catalog/checkout bằng QueryClient thật.

Các test mới đã tái hiện lỗi trên phiên bản trước sửa. Sau sửa, bộ test tập trung đạt **20 backend** (17 integration + 3 unit) và **15 frontend** (quản lý Room Type + checkout).

Kết quả chạy lại toàn bộ sau ba bản sửa:

| Kiểm tra | Kết quả |
| --- | --- |
| `./mvnw -o --batch-mode clean verify` | **855 test backend**, 135 suite; 0 lỗi, 0 bỏ qua; JAR và Spotless đạt, exit code 0 |
| `npm run test:run` | **317 test frontend**, 61 file; tất cả đạt |
| `npm run build` | TypeScript/Vite đạt |
| `npm run lint`, `npm run format:check` | Đạt |
| `git diff --check`, liên kết Markdown trong tài liệu cập nhật | Đạt |
| JAR sau clean build | Có service đã sửa, giữ đủ 5 migration trước đó, không có các initializer cũ đã xóa |

Log backend có thông báo Surefire kết thúc JVM sau khi chờ shutdown 30 giây. Thread dump cho thấy Spring đang đợi Hikari dọn connection pool của các context test; toàn bộ 855 test đã hoàn tất, XML báo 0 failure/error/skipped và Maven trả BUILD SUCCESS/exit 0. Chưa điều chỉnh cấu hình teardown chung của bộ test trong phạm vi ba bản sửa. Các cảnh báo jsdom/Node và bundle Vite vẫn như lượt kiểm tra trước.

Các thao tác ghi để kiểm thử dùng PostgreSQL Testcontainers riêng. Không reset/sửa database local, không chạy thanh toán ngân hàng thật, không commit/push. Khởi động lại backend và frontend trước khi tự thử các luồng mới trong [room-business-fixes.md](../testing/room-business-fixes.md).
