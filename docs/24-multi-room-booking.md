# Đặt nhiều phòng trong một đơn

## Phạm vi

- Một đơn từ 1 đến 10 phòng, cùng ngày nhận/trả phòng tại HANBAY HOTEL.
- Có thể kết hợp nhiều loại phòng. Mỗi phòng có số người lớn, trẻ em và dịch vụ riêng.
- Một người đặt và thông tin liên hệ chung; backend tiếp tục sử dụng quy trình lưu thông tin khách chính hiện có.
- Một mã đơn `ORD-…`, nhiều mã booking cho từng phòng và một mã QR thanh toán tổng tiền ban đầu.
- Trả trước toàn bộ; chỉ xác nhận tất cả phòng sau khi nhận đủ tiền trước thời hạn giữ phòng.
- Hủy toàn đơn theo chính sách hủy hiện có; chưa hỗ trợ hủy riêng một phòng hoặc gia hạn riêng một phòng thuộc đơn.
- Lễ tân vẫn phân phòng, check-in, check-out và xử lý trả sớm từng booking. Khách bổ sung thông tin khách lưu trú theo quy trình của lễ tân.

## Luồng giao diện

1. Tìm phòng rồi chọn một loại phòng, hoặc bấm **Đặt nhiều phòng cùng lúc** trên trang tìm kiếm.
2. `/bookings/new?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD` hiển thị phòng trống theo kỳ lưu trú. Có thể truyền `roomTypeId`, `adults`, `children` để chọn sẵn phòng đầu.
3. Chọn số lượng từng loại phòng, số khách và dịch vụ của từng phòng; nhập thông tin người đặt một lần.
4. Bấm giữ phòng. Backend kiểm tra lại giá, sức chứa và tổng số phòng; chuyển tới `/booking-orders/:orderId` sau khi giữ thành công.
5. Đồng ý điều khoản và tạo QR. Mã QR ban đầu dùng tổng tiền toàn đơn; sau thanh toán một phần, QR và hướng dẫn chuyển khoản dùng số còn thiếu. Trang chi tiết tự cập nhật trạng thái thanh toán.
6. Tài khoản đã đăng nhập xem các đơn mới trong lịch sử đặt phòng; booking cũ tiếp tục được hiển thị riêng. Khách vãng lai xem lại đơn trên cùng tab/trình duyệt bằng token trong `sessionStorage`.
7. Có thể hủy toàn đơn nếu tất cả phòng còn đủ điều kiện. Nếu có tiền hoàn, khách cung cấp tài khoản ngân hàng. Với tiền chuyển thừa/đến muộn, trang đơn cho phép bổ sung tài khoản nhận hoàn sau khi phát sinh yêu cầu.

## Dữ liệu và tương thích

Migration: `V202611041000__add_multi_room_booking_orders.sql`. Flyway áp dụng khi backend khởi động hoặc chạy kiểm thử tích hợp. Không thay đổi hay xóa booking cũ.

Migration bổ sung `V202611061800__link_refund_transactions.sql` liên kết một-một yêu cầu hoàn với giao dịch hoàn, bao gồm backfill có điều kiện cho dữ liệu cũ. API payment bổ sung `remainingAmount`; API đơn bổ sung `bookedAmount`, `additionalChargeAmount` và trả `totalAmount` theo tổng thực tế của các phòng.

- `booking_orders`: mã đơn, người đặt, liên hệ, kỳ lưu trú, tổng tiền ban đầu, hạn giữ phòng, trạng thái, hash token và thông tin chống tạo trùng.
- `bookings.order_id`, `bookings.order_room_number`: liên kết từng phòng với đơn chung; mỗi booking giữ nguyên giá theo đêm, dịch vụ, lịch sử trạng thái và phân phòng.
- `payments.order_id`: chủ thể thanh toán cho đơn nhiều phòng. `booking_id` tiếp tục tham chiếu booking đại diện để tương thích các màn hình/báo cáo cũ; không dùng booking đại diện làm tổng tiền hoặc trạng thái toàn đơn.
- `payment_allocations`: phần tiền dự kiến và phần đã ghi nhận cho mỗi booking. Tiền ghi nhận của một phòng không vượt phần phân bổ; tiền thừa tạo yêu cầu hoàn riêng.
- Đơn có quan hệ 1–N với payment để mở rộng; phạm vi hiện tại tái sử dụng một yêu cầu thanh toán đang có cho lần trả trước ban đầu. Các khoản thu phát sinh tại quầy tiếp tục thuộc booking tương ứng.
- Trạng thái đơn (`PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`) quản lý việc đặt và thanh toán. Trạng thái lưu trú nằm trên từng booking; một phòng đã check-in không buộc các phòng khác check-in cùng lúc.

## API

Tiền tố `/api/v1`:

| Phương thức | Đường dẫn | Nội dung |
|---|---|---|
| POST | `/booking-orders` | Tạo đơn và giữ toàn bộ phòng |
| GET | `/booking-orders/my-orders?page=0&size=6` | Lịch sử đơn của tài khoản đăng nhập |
| GET | `/booking-orders/{id}` | Chi tiết đơn, từng phòng, thanh toán và hoàn tiền |
| POST | `/booking-orders/{id}/payments` | Tạo hoặc tái sử dụng QR tổng đơn |
| GET | `/booking-orders/{id}/cancellation-eligibility` | Kiểm tra điều kiện và tổng tiền hoàn |
| POST | `/booking-orders/{id}/cancel` | Hủy tất cả phòng trong một giao dịch |
| POST | `/booking-orders/{id}/refund-information` | Bổ sung thông tin ngân hàng cho khoản hoàn tự động đang chờ |

Tạo đơn cần hai header độc lập: `Idempotency-Key` (16–120 ký tự) và `X-Booking-Access-Token` (32–120 ký tự ngẫu nhiên). Frontend sinh bằng Web Crypto, giữ cùng key/token khi thử lại yêu cầu thất bại. Backend chỉ lưu hash token. Cùng key nhưng khác payload hoặc token không thể tạo thêm hoặc đọc đơn cũ. Không đưa token vào URL.

Các thao tác theo ID cần chủ tài khoản hoặc `X-Booking-Access-Token` phù hợp. Lịch sử luôn yêu cầu đăng nhập. Các endpoint booking cũ không cho sửa, hủy riêng hay thanh toán riêng booking con đang chờ thanh toán.

Ví dụ body tạo đơn, không nhận giá từ frontend:

```json
{
  "checkInDate": "2026-12-10",
  "checkOutDate": "2026-12-12",
  "guestInfo": {
    "fullName": "Khách thử nghiệm",
    "email": "guest@example.test",
    "phone": "0912345678",
    "identityNumber": "TEST-IDENTITY"
  },
  "rooms": [
    { "roomTypeId": "<deluxe-uuid>", "adults": 2, "children": 0, "addOns": [] },
    { "roomTypeId": "<deluxe-uuid>", "adults": 1, "children": 1, "addOns": [] },
    { "roomTypeId": "<suite-uuid>", "adults": 2, "children": 0, "addOns": [] }
  ]
}
```

## Quy tắc giao dịch

- Khi tạo đơn, khóa tất cả loại phòng theo thứ tự UUID, kiểm tra tổng nhu cầu từng loại rồi tạo đơn và booking trong cùng một transaction. Một phòng không hợp lệ thì rollback toàn bộ.
- Khi thanh toán, hết hạn hoặc hủy: khóa các loại phòng theo UUID, khóa đơn, khóa tất cả booking theo UUID, sau đó xử lý payment. Các khóa tồn kho này thống nhất với luồng tạo đơn. Scheduler booking cũ bỏ qua booking có `order_id`; scheduler đơn xử lý toàn bộ phòng trong một transaction của đơn.
- Tất cả phòng có cùng hạn giữ phòng, lấy từ cấu hình thời gian thanh toán hiện có. Việc chọn phòng trên giao diện chưa giữ tồn kho.
- Webhook SePay dùng cơ chế chống lặp hiện có. Có thể nhận nhiều khoản chuyển thiếu, nhưng giữ trạng thái `PENDING` cho đến khi đủ tiền. Kiểm tra lại thời hạn sau khi lấy được các khóa tồn kho; tiền đến hoặc chờ khóa quá hạn không xác nhận lại phòng đã giải phóng.
- Hủy/hết hạn có khoản chuyển thiếu phải tạo yêu cầu hoàn số tiền đã nhận. Tiền thừa, chuyển lặp bằng một giao dịch ngân hàng khác hoặc tiền đến muộn được đối soát bằng yêu cầu hoàn riêng. Yêu cầu thiếu tài khoản ngân hàng để trống đồng bộ bốn trường theo ràng buộc database, rồi khách bổ sung qua trang đơn.
- Khi hủy đơn đã xác nhận, tính chính sách trên từng booking rồi cộng tiền hoàn. Nếu một phòng đã nhận/trả phòng hoặc không còn đủ điều kiện, từ chối hủy toàn đơn.

## Kiểm tra thủ công

Hướng dẫn từng bước cho các lỗi đã sửa, dữ liệu mẫu và checklist happy/unhappy: [Kiểm tra hồi quy đặt nhiều phòng](testing/multi-room-booking-regression.md).

1. Chọn 2 phòng cùng loại và 1 phòng loại khác; kiểm tra tiền phòng, dịch vụ và số khách của từng phòng.
2. Hai khách cùng đặt phòng cuối cùng: chỉ một yêu cầu thành công.
3. Một phòng sai sức chứa hoặc thiếu tồn kho: không có đơn hoặc booking mồ côi.
4. Thử lại sau lỗi mạng: giữ cùng mã đơn, không giữ thêm phòng.
5. Tạo QR hai lần: trả về cùng payment, số tiền bằng tổng đơn.
6. Chuyển thiếu: chưa phòng nào được xác nhận; chuyển đủ phần còn lại: tất cả được xác nhận.
7. Gửi lại cùng webhook: số tiền và số yêu cầu hoàn không tăng.
8. Chuyển thừa hoặc chuyển sau hạn: không tăng số tiền đã phân bổ vượt giá phòng, có yêu cầu hoàn và chỗ nhập tài khoản nhận.
9. Hủy đơn đang giữ: tất cả booking được hủy và phòng được mở bán lại. Hủy đơn đã trả tiền: tổng tiền hoàn đúng chính sách và chỉ tạo một lần.
10. Lễ tân nhận một phòng: những phòng khác giữ trạng thái riêng; không cho hủy toàn đơn sau khi một phòng đã nhận.
11. Tài khoản khác hoặc token sai không xem/thanh toán/hủy/bổ sung tài khoản hoàn của đơn.
12. Xem lịch sử trên desktop/mobile: hiển thị một đơn chung; các booking cũ vẫn xem và thanh toán theo luồng cũ.
