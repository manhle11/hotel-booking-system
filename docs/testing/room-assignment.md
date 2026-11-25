# Lịch gán phòng: vận hành và kiểm thử

Nhánh triển khai: `feat/room-assignment-optimization`.
Phạm vi: [bản đề xuất đã được duyệt](../32-room-assignment-optimization-proposal.md).

## Sử dụng

Khởi động lại backend để Flyway chạy migration
`V202611241000__add_room_assignment_planning.sql`, sau đó chạy frontend như bình
thường. Migration tạo sổ phân bổ, bản gợi ý, lịch sử và hàng đợi làm mới; đồng bộ
khách đang ở cùng các phòng bảo trì hiện có.

1. Đăng nhập bằng tài khoản lễ tân hoặc quản lý, mở **Lịch gán phòng** trong menu.
2. Chọn loại phòng, ngày bắt đầu và khoảng 30/60/90 ngày. Nhấn vào booking trên
   lịch hoặc trong danh sách chưa gán để gán thủ công/khóa phòng.
3. Bấm **Gợi ý xếp phòng**, xem số khoảng trống 1/2 đêm, số lần đổi phòng và bảng
   thay đổi. Bản gợi ý không cập nhật lịch đang áp dụng.
4. Chọn **Áp dụng phương án**, xem lại và xác nhận. Gợi ý hết hạn hoặc nguồn lịch
   đã đổi thì phải tính lại. Gửi lại cùng mã phương án không áp dụng lần thứ hai.
5. Khi nhận khách, màn check-in điền sẵn phòng đã gán nếu sẵn sàng. Phòng chưa
   sẵn sàng cần được xử lý trước; không tự đổi một phòng đã cố định.

Khóa thủ công, khách đang ở và các gán có ngày nhận hôm nay/ngày mai được giữ
nguyên khi tối ưu. Chỉ quản lý được mở khóa hoặc thay phòng đã cố định, có lý do.
Khách đã nhận phòng không được chuyển phòng bằng chức năng này.

Pending còn hạn tham gia kiểm tra khả thi nhưng không được công bố một phòng cụ
thể. Phòng bẩn/đang dọn vẫn có thể được xếp cho kỳ ở tương lai; check-in vẫn yêu
cầu `AVAILABLE`. Phòng bảo trì/ngừng phục vụ bị chặn đến khi kết thúc tình trạng đó.

## Thiết kế triển khai

- `RoomAssignmentSolver` dựng greedy, dùng CP-SAT để tối ưu hoặc cứu một trường
  hợp greedy thất bại, rồi kiểm tra lời giải bằng validator độc lập.
  Thư viện native được nạp trong lúc khởi động backend để tránh làm chậm yêu cầu
  gợi ý đầu tiên; ngân sách giải được tính sau bước khởi tạo này.
- `room_allocations` chỉ chứa phân bổ đã áp dụng và khoảng chặn phòng. Phân bổ
  dự kiến không sửa `Booking.assignedRoom` hoặc chuyển phòng sang `OCCUPIED`.
- Trigger đồng bộ khi booking được nhận/trả, hủy/no-show, đổi ngày; khi phòng
  bắt đầu/kết thúc bảo trì. Đổi kỳ ở tương lai giải phóng gán mềm để gợi ý lại,
  giữ các gán cố định. Phân bổ đã giải phóng còn trong lịch sử.
- GiST exclusion theo phòng và khoảng `[check_in, check_out)` kiểm tra cuối
  transaction, cho phép hoán đổi nhiều phòng trong một lần áp dụng nguyên tử.
- `InventoryWriteGuard` lấy advisory transaction lock chung trước các khóa
  booking/loại phòng/phòng hiện có, kiểm tra toàn bộ nhu cầu bị ảnh hưởng trước
  commit. Transaction thủ công tạo/sửa pending cũng tham gia cơ chế này.
- MVP tuần tự hóa các giao dịch ghi tồn cho một khách sạn nhỏ. Đây là lựa chọn
  để các thứ tự khóa cũ không tạo chu trình; có thể phân chia khóa theo khách sạn
  hoặc loại phòng sau khi đo tải và thống nhất toàn bộ thứ tự khóa.
- `sourceVersion` là SHA-256 của snapshot nguồn, gồm phòng, booking, gán, ngày
  khách sạn và mốc xử lý ở quá hạn. MVP dùng snapshot chung nên thay đổi ở loại
  phòng khác cũng có thể làm gợi ý cũ hết hiệu lực.
- Gợi ý có hạn tối đa 5 phút, không vượt hạn pending sớm nhất hoặc nửa đêm theo
  giờ khách sạn. Không phụ thuộc scheduler đã đổi trạng thái pending hay chưa.
- Hàng đợi tạo gợi ý lưu trong PostgreSQL, được cập nhật cùng transaction nguồn.
  Job gom thay đổi, tạo bản nháp và chỉ xóa đúng phiên bản sự kiện đã xử lý.
  Tác vụ 02:00 đưa các loại phòng vào hàng đợi; không tự áp dụng phương án.

Khách đang ở quá hạn tiếp tục chặn phòng trong bài toán. Ràng buộc theo ngày
trên database chỉ bảo vệ lịch dự kiến; trạng thái thực tế và khóa phòng vẫn là
điều kiện bắt buộc trước check-in.

## API

Các đường dẫn sau nằm dưới `${api.prefix}/front-desk/room-assignments`.
Lễ tân và quản lý được truy cập; admin không mặc nhiên có quyền vận hành.

| Phương thức | Đường dẫn | Nội dung |
| --- | --- | --- |
| GET | `/` | Lịch theo `from`, `to`, tùy chọn `roomTypeId` |
| POST | `/plans` | Tạo gợi ý theo loại phòng và khoảng ngày |
| GET | `/plans/latest` | Bản nháp mới nhất còn phù hợp với nguồn và khoảng ngày |
| GET | `/plans/{id}` | Tra kết quả, phục hồi sau khi mất phản hồi mạng |
| POST | `/plans/{id}/apply` | Áp dụng với `expectedVersion` |
| PUT | `/bookings/{id}` | Gán/khóa/mở khóa với phiên bản nguồn và lý do |
| GET | `/history` | Tối đa 100 thao tác gần nhất theo loại phòng |
| GET | `/suggested-room` | Phòng đã gán cho một `bookingCode`, phục vụ check-in |

`ASSIGNMENT_STALE` trả HTTP 409 để lấy gợi ý mới. `ASSIGNMENT_INFEASIBLE` nghĩa
là đã chứng minh không xếp được với ràng buộc hiện tại; `ASSIGNMENT_UNDETERMINED`
là chưa có lời giải hợp lệ, không được hiển thị thành kết luận đã hết phòng.

## Cấu hình

Các thuộc tính có mặc định trong code, có thể ghi đè bằng cấu hình Spring:

| Thuộc tính | Mặc định |
| --- | --- |
| `hotel.assignment.solver-seconds` | `5` |
| `hotel.assignment.weights.orphan-one` | `10` |
| `hotel.assignment.weights.orphan-two` | `4` |
| `hotel.assignment.weights.changed` | `3` |
| `hotel.assignment.scheduler-enabled` | `true` |
| `hotel.assignment.refresh-delay-ms` | `30000` |
| `hotel.assignment.nightly-cron` | `0 0 2 * * *` |
| Múi giờ job | `hotel.stay.zone` |

Mỗi phương án lưu cấu hình trọng số/ngân sách đã dùng. Solver có các kết quả
`OPTIMAL`, `FEASIBLE`, `INFEASIBLE`, `UNKNOWN`, `MODEL_INVALID`; chỉ hai trạng thái
đầu có thể áp dụng. Fallback phải xếp đủ toàn bộ nhu cầu và qua validator.

## Kiểm thử

Backend, chạy tại `hotel-booking/` với Docker sẵn sàng:

```sh
./mvnw -Dtest=RoomAssignmentSolverTest,RoomAssignmentIntegrationTest,RoomAssignmentSecurityTest,RoomAssignmentMigrationTest test
./mvnw --batch-mode clean verify
```

Frontend, chạy tại `client/`:

```sh
npm run test:run
npm run build
```

Kiểm thử trình duyệt dùng Spring HTTP server thật, PostgreSQL Testcontainers và
Chromium đã cài cho Playwright. Build frontend trước, rồi chạy tại `hotel-booking/`:

```sh
./mvnw -Dassignment.e2e=true -Dtest=RoomAssignmentBrowserIntegrationTest test
```

Kiểm thử này chỉ tạo dữ liệu và tài khoản trong database tạm. JWT kiểm thử truyền
qua stdin, không lưu vào mã nguồn. Script sở hữu và dừng Vite preview cổng 5183.
Ảnh kiểm tra lưu tại `output/room-assignment-validation/`.

Những ca được kiểm tra riêng: khóa chéo làm booking dài không xếp được, CP-SAT
cứu greedy, greedy trên 100 bộ khoảng ngẫu nhiên không khóa, mốc hết hạn pending,
nguồn đổi trong lúc tính, phân quyền mở khóa, kiểm tra admission, bảo trì giảm tồn,
trigger vòng đời, ràng buộc PostgreSQL và áp dụng hai phương án đồng thời.
Kiểm thử nâng cấp chạy từ schema trước tính năng, có khách đang ở, lịch sử phòng
đã trả và phòng bảo trì. Script reset demo cũng xóa tường minh bốn bảng gán phòng,
giữ nguyên tài khoản, vai trò, token và lịch sử Flyway.

## Kết quả xác minh ngày 25/11/2026

- Backend: 158 bộ kiểm thử, 1.144 test, không có failure/error. Có 1.142 test
  thực thi thành công; hai test trình duyệt tùy chọn của booking policy và review
  được bỏ qua. Kiểm thử trình duyệt gán phòng được bật bằng `-Dassignment.e2e=true`.
- Frontend: 531/531 test thành công khi chạy
  `npm run test:run -- --maxWorkers=1`; `npm run build` thành công.
- Trình duyệt thật kiểm tra gợi ý không sửa lịch, áp dụng phương án, phiên bản
  cũ, phân quyền khóa/mở khóa, lịch sử và bố cục điện thoại. Ảnh xác minh:
  [desktop](../../output/room-assignment-validation/desktop.png),
  [mobile](../../output/room-assignment-validation/mobile.png).
- Các bài tích hợp dùng PostgreSQL Testcontainers riêng; không chạy migration
  hoặc reset dữ liệu trên database phát triển đang có.

Chưa mở rộng upgrade, yêu cầu tầng/VIP/liền kề, bảo trì có lịch tương lai, chuyển
phòng giữa kỳ và tự động áp dụng. Replay dữ liệu lịch sử cần nguồn sự kiện đầy
đủ; các fixture kiểm thử không thay thế đánh giá tác động doanh thu thực tế.
