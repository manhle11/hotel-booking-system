# Vận hành khách đến muộn và không đến — 12/11/2026

Bổ sung chính sách V2 và thu phí còn thiếu: xem [luồng kiểm tra giao diện](booking-policy-ui-walkthrough.md)
và [các điều kiện kiểm thử V2](unified-booking-policy.md). P là giá đêm đầu **gốc**, giữ nguyên
sau đổi lịch; các con số kiểm thử ghi ngày bên dưới là kết quả của đợt triển khai trước.

## Chính sách được triển khai

- Đến muộn được nhận: phụ thu 0 VND, giữ tiền kỳ ở gốc và ngày trả.
- Sau cutoff mặc định 06:00 hôm sau: lễ tân đề nghị, quản lý duyệt giữ có hạn
  trong kỳ ở gốc. Hết hạn thì cần xử lý lại, không tự thu thêm hoặc tự đóng.
- Không đến: phí 100% tiền phòng đêm đầu đã chốt; hoàn các đêm còn lại và dịch vụ
  chưa dùng. Quản lý có thể giảm đến 0 VND, bắt buộc ghi lý do.
- Chỉ CONFIRMED chưa nhận mới có thể đóng NO_SHOW. Nhân viên xác nhận thủ công;
  không có tác vụ đánh no-show theo đồng hồ. Booking cũ chưa chấp thuận chính
  sách cần quản lý duyệt.

## Thao tác giao diện

Giao diện mới có ba nhóm và các form ngắn; xem [hướng dẫn theo dõi khách đến](arrival-workflow.md).

1. Mở **Khách đến hôm nay → Cần xử lý → Xử lý khách**.
   Có thể tra cứu bằng mã booking ngay trên trang, kể cả booking đã đóng.
2. Đối chiếu khách, giờ khách hẹn, hạn tiếp nhận, ngày trả và liên hệ gần nhất.
3. Chọn **Ghi liên hệ** để lưu kênh/kết quả; chọn **Cập nhật giờ đến** để ghi giờ hẹn.
   **Xin nhận sau hạn** gửi đề nghị quản lý; duyệt/từ chối là bước riêng.
4. Muốn đóng: mở **Xử lý không đến**, nhập lý do, xem phí và khoản hoàn,
   bấm **Xác nhận không đến**. Quản lý chọn phí chuẩn, giảm phí hoặc miễn phí; chỉ giảm mới cần nhập số tiền.
   Cảnh báo booking cũ thiếu chấp thuận chính sách chỉ hiện trong phần này.
5. Nếu mất phản hồi, giao diện tra cứu kết quả. Khi chưa xác minh được, các
   thao tác bị khóa và phải bấm **Tra cứu lại kết quả** trước khi tiếp tục.
6. Yêu cầu hoàn xuất hiện ở màn quản lý hoàn tiền. Khách có thể nhập tài khoản
   từ chi tiết booking/lịch sử hoặc kết quả thanh toán; đơn nhiều phòng dùng
   phần bổ sung tài khoản của đơn. Quản lý cũng có thể bổ sung sau xác minh.
7. Quản lý chuyển tiền ngoài hệ thống, đối soát rồi xác nhận mã tham chiếu ngân
   hàng. Không thể xác nhận hoàn no-show khi chưa có tài khoản nhận hoàn.
8. Muốn đặt kỳ mới: quản lý tra cứu booking đã NO_SHOW, chọn **Đặt kỳ ở mới cho
   khách**, chọn ngày và hoàn tất đặt/thanh toán. Booking mới liên kết booking
   gốc và giữ người sở hữu booking gốc; không tự bù trừ tiền đang chờ hoàn.

Khách chủ động báo hủy hoặc xin đổi lịch sử dụng hai nút riêng, không chuyển thành no-show.
Xem [hướng dẫn hủy / đổi lịch](booking-change-requests.md) về mức phí, hàng đợi quản lý duyệt,
đổi lịch toàn đơn, thanh toán chênh lệch và kiểm tra booking cũ. Yêu cầu PENDING ngăn đóng no-show.

## API

Tiền tố `/api/v1/bookings/{bookingCode}`:

| API                                | Quyền                         | Nội dung                                                                          |
| ---------------------------------- | ----------------------------- | --------------------------------------------------------------------------------- |
| GET `/arrival-handling`            | RECEPTIONIST, MANAGER         | Xem trước quyết toán, hạn giữ, lịch sử và token kiểm tra dữ liệu                  |
| POST `/arrival-contact`            | RECEPTIONIST, MANAGER         | `{note}`: lưu kênh và kết quả liên hệ                                             |
| POST `/arrival-hold`               | RECEPTIONIST, MANAGER         | `{until, note}`: UTC ISO instant; vượt cutoff chỉ quản lý mới duyệt               |
| POST `/arrival-hold/release`       | MANAGER                       | `{note}`: kết thúc giữ, ghi lý do/thông báo khách                                 |
| POST `/no-show`                    | RECEPTIONIST, MANAGER         | `{previewToken, feeAmount, reason, contactNote}`; giảm phí/booking cũ cần MANAGER |
| GET `/arrival-settlement`          | Chủ booking hoặc access token | Quyết toán và khoản hoàn, không trả ghi chú nội bộ                                |
| POST `/arrival-refund-information` | Chủ booking hoặc access token | Bổ sung tài khoản cho yêu cầu hoàn còn thiếu                                      |

- `POST /payments/requests` nhận thêm `arrivalPolicyVersion` khi khách đồng ý
  điều khoản thanh toán ban đầu. `POST /booking-orders/{id}/payments` nhận cùng
  trường này trong body; phiên bản lưu trên từng booking. Tích hợp cũ không gửi
  được giữ tương thích và thuộc nhóm quản lý xem xét khi xử lý no-show.
- `POST /bookings` nhận tùy chọn `replacementForBookingCode`, chỉ MANAGER được
  dùng và booking gốc phải NO_SHOW. Giá/tồn được kiểm tra bằng luồng đặt mới.
- `POST /payment-monitoring/refunds/{refundId}/destination`: MANAGER bổ sung
  thông tin tài khoản đã xác minh cho khoản hoàn chưa có đích nhận.

## Giao dịch và dữ liệu

- Thêm NO_SHOW vào PostgreSQL enum trong migration riêng trước migration cột.
- Lưu tổng tiền gốc, phí được duyệt, thời điểm đóng và phiên bản chính sách
  quyết toán. `totalAmount` sau đóng bằng phí; snapshot từng đêm/dịch vụ vẫn còn.
- Khóa booking rồi payment; với booking thuộc đơn, dùng thứ tự khóa loại phòng,
  đơn, các booking và payment hiện có. Check-in/webhook và quyết toán không ghi
  đè nhau. Kiểm tra token xem trước sau khóa, không tin giá/tiền từ frontend.
- Tiền thực thu lấy từ giao dịch thành công. Khấu trừ khoản đã hoàn/đang dành
  cho hoàn; đơn chung dùng phân bổ từng phòng và liên kết khoản hoàn tới phòng.
- Gửi lại no-show đã hoàn tất trả kết quả đã lưu, không tạo thêm phí/hoàn.
- Dashboard có số booking không đến và phí no-show theo kỳ tạo booking hiện có;
  không tính no-show vào số đêm ở. Luồng hoàn và báo cáo dòng tiền giữ quy tắc
  ghi nhận theo giao dịch thực tế.

## Kiểm tra

- `ArrivalHandlingIntegrationTest`: PostgreSQL Testcontainers riêng; cutoff,
  phí đêm đầu, giải phóng tồn, nhận muộn, hạn giữ, quyền miễn/giảm, booking cũ,
  dữ liệu xem trước thay đổi, thông tin nhận hoàn, đơn chung và gửi đồng thời.
- `ArrivalHandlingSecurityIntegrationTest`: quyền HTTP, body không hợp lệ và
  đường dẫn khách dùng token để tra cứu qua kiểm tra quyền sở hữu.
- `arrival-handling-modal.test.tsx`: xem phí, lý do/liên hệ bắt buộc, miễn phí,
  booking cũ, giữ phòng còn hạn và tra cứu sau mất phản hồi.

Kết quả kiểm tra ngày 12/11/2026 sau bổ sung hủy / đổi lịch: **182 test backend đạt**,
gồm 25 test tích hợp nghiệp vụ đến muộn/no-show/hủy/đổi lịch, 12 test HTTP no-show và
12 test HTTP yêu cầu hủy/đổi lịch; **169 test frontend đạt** trong 28 file.
Backend gồm kiểm thử PostgreSQL Testcontainers riêng, không bỏ qua test.
TypeScript/Vite build, ESLint các file thay đổi, Prettier và Spotless đạt.

## Nạp phiên bản mới

Khởi động backend bằng bản build mới để Flyway áp dụng hai migration:
`V202611121100__add_no_show_status.sql` và `V202611121101__add_arrival_handling.sql`.
Luồng hủy / đổi lịch bổ sung migration `V202611121800__add_booking_change_requests.sql`.
Frontend cần build/nạp lại tương ứng. Nếu bật RAG, build lại backend và nạp corpus
để dùng `no-show-policy.md` mới. Các kiểm thử dùng database riêng; không reset
hoặc xóa dữ liệu ứng dụng local.
