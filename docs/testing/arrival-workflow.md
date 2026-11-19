# Theo dõi khách đến và quyết toán không đến

## Nạp thay đổi

Khởi động lại backend để Flyway chạy `V202611121900__separate_arrival_estimates_and_contacts.sql`,
sau đó tải lại frontend. Các migration no-show/hủy/đổi lịch của bản trước vẫn cần được áp dụng.
Không suy ra giờ khách hẹn từ hạn giữ phòng cũ; booking cũ hiển thị chưa có giờ hẹn cho đến khi nhân viên cập nhật.

Mở menu **Khách đến hôm nay**, đường dẫn `/front-desk/arrivals/today`. Trang hiển thị **Theo dõi khách đến**.

## Ba nhóm công việc

| Nhóm           | Booking xuất hiện                                                                                | Thao tác tiếp theo                                     |
| -------------- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------ |
| Chờ nhận phòng | Đã xác nhận, đang trong cửa sổ tiếp nhận; gồm ngoại lệ đã duyệt và chưa có việc cần xử lý        | Chờ khách, cập nhật giờ đến hoặc nhận phòng            |
| Cần xử lý      | Quá giờ hẹn, giờ hẹn vượt hạn, đề nghị nhận sau hạn, yêu cầu hủy/đổi lịch hoặc hết hạn tiếp nhận | Liên hệ, duyệt/từ chối yêu cầu hoặc quyết toán phù hợp |
| Đã xử lý       | Nhận phòng, đóng không đến hoặc hủy trong khoảng ngày xử lý được chọn                            | Xem kết quả và lịch sử                                 |

Nhóm chờ/cần xử lý gồm ngày đến hôm nay và booking tồn chưa đến từ ngày trước.
Yêu cầu chờ duyệt của booking ngày tương lai cũng xuất hiện để quản lý xử lý sớm.
Nhóm đã xử lý mặc định theo ngày hiện tại của khách sạn. Chọn **Từ ngày xử lý – Đến ngày xử lý**
để xem booking cũ, hoặc chọn nhanh **Hôm nay**, **Hôm qua**, **7 ngày qua**, **Tháng này**.
Khoảng ngày gồm trọn cả ngày bắt đầu và ngày kết thúc theo múi giờ khách sạn.
Ngày xử lý là thời điểm nhận phòng, đóng không đến hoặc hủy booking, không phải ngày tạo/ngày lưu trú.
Booking đã trả phòng vẫn được tra theo thời điểm nhận phòng trong luồng khách đến.
Cột **Thời điểm xử lý** hiển thị mốc tương ứng; danh sách xếp mới nhất trước, lọc trước khi phân trang.
Đổi khoảng ngày đưa về trang đầu; chuyển sang nhóm khác không áp dụng bộ lọc lịch sử.
Vẫn có thể tra cứu trực tiếp mã để mở booking cũ.
Một booking có thể có nhiều lý do cần xử lý nhưng chỉ có một dòng. Bộ lọc lý do và phân trang thực hiện trên máy chủ.
Trong nhóm cần xử lý, booking hết hạn tiếp nhận đứng trước; tiếp đến là các yêu cầu chờ duyệt.
Danh sách tự làm mới mỗi 5 giây và khi quay lại tab/kết nối lại; ngày/giờ theo cấu hình máy chủ.

## Phạm vi theo phòng và đơn

Xem [quy tắc phạm vi đã chốt](../31-arrival-handling-scope.md).
Đơn nhiều phòng có trạng thái nhận riêng; giờ hẹn/liên hệ/ngoại lệ nhận muộn có thể cập nhật cho các phòng cùng đơn được chọn rõ.
Form yêu cầu chọn phạm vi, hiển thị mã phòng tác động và loại phòng đã nhận/kết thúc khỏi lựa chọn.
Giờ hẹn chung không cấp quyền nhận muộn chung. Nhận phòng/no-show theo từng phòng;
hủy/đổi ngày trong bản hiện tại vẫn toàn đơn với bước xác nhận riêng.
API thêm tùy chọn `bookingCodes`; không gửi vẫn tương thích hành vi một phòng.
Không cần migration dữ liệu mới cho thay đổi phạm vi này; khởi động lại backend để nạp API mới.

## Giờ hẹn và hạn tiếp nhận

- **Giờ khách hẹn đến:** thông tin khách dự kiến đến để lễ tân liên hệ. Qua giờ này chỉ cảnh báo, không thu phí hoặc tự đóng booking.
- **Hạn tiếp nhận:** mặc định trước 06:00 hôm sau; có thể kéo dài khi quản lý chấp thuận, tối đa giờ trả phòng đã đặt. Đúng hạn là hết hiệu lực.
- Cập nhật giờ hẹn không kéo dài hạn tiếp nhận, giá hoặc ngày trả.
- Đến muộn được tiếp nhận vẫn phụ thu **0đ**; tiền kỳ ở gốc giữ nguyên.

Ví dụ booking nhận 12/11, trả 13/11 lúc 12:00, khách hẹn 19:00 ngày 12/11:
19:01 chưa đến → cần xử lý vì quá giờ hẹn; vẫn được nhận trước 06:00 ngày 13/11.
Khách đổi hẹn 08:00 ngày 13/11 → cần xin nhận sau hạn. Cập nhật giờ hẹn đơn thuần chưa cho phép nhận lúc 08:00.
Quản lý duyệt hạn 09:00 → được nhận trước 09:00; đúng 09:00 ngoại lệ hết hiệu lực. Ngày trả vẫn 13/11 lúc 12:00.

## Kiểm tra các form ngắn

### Xem thông tin khách

Mở **Xử lý khách**: thấy khách, giờ hẹn, hạn tiếp nhận, giờ trả, tiền đã thanh toán, việc tiếp theo và các nút phù hợp.
Lịch sử được thu gọn. Không có ô nhập phí hoặc cảnh báo điều khoản no-show ở màn này.
Lễ tân có **Nhận phòng** khi còn trong cửa sổ tiếp nhận; trang nhận phòng tiếp tục kiểm tra phòng và hồ sơ.
Quản lý xem/duyệt nghiệp vụ; thao tác nhận phòng theo phân quyền lễ tân hiện tại.

### Cập nhật giờ đến

Chọn **Cập nhật giờ đến** → ngày/giờ khách dự kiến đến, ghi chú tùy chọn → **Lưu giờ đến**.
Giờ phải từ hiện tại, trong kỳ ở và trước giờ trả phòng. Có thể ghi giờ sau hạn để xin ngoại lệ,
nhưng hạn tiếp nhận không đổi. Kiểm tra cột giờ hẹn và nhóm công việc sau khi lưu.

### Ghi liên hệ

Chọn **Ghi liên hệ** → kênh (điện thoại/email/tin nhắn/trực tiếp/khác), kết quả và ghi chú tùy chọn.
Các kết quả: Khách vẫn đến, Không nghe máy/chưa phản hồi, Khách báo hủy, Khách xin đổi lịch, Chưa xác định.
Kết quả thông thường lưu ngay; không bắt nhập một đoạn văn.
Chọn khách báo hủy/xin đổi lịch → **Tiếp tục yêu cầu hủy/đổi lịch** → kiểm tra phạm vi toàn đơn.
Khách xác nhận hủy thì giải phóng ngay; đổi lịch giữ ngày cũ khi chờ quản lý áp dụng báo giá.
Thông tin liên hệ được ghi cùng yêu cầu khi gửi thành công; đóng form chưa gửi không tạo yêu cầu hay tự đánh no-show.
Kiểm tra cột liên hệ gần nhất hiển thị thời điểm, kênh, kết quả và ghi chú.

### Xin nhận sau hạn

Chọn **Xin nhận sau hạn** → giờ khách dự kiến đến, hạn tiếp nhận đề nghị và lý do.
Hạn đề nghị phải sau giờ khách hẹn, kéo dài hạn hiện tại, không vượt giờ trả phòng gốc.
Lễ tân gửi đề nghị chờ quản lý. Quản lý gửi trực tiếp có thể chấp thuận ngay.
Trong khi chờ duyệt, quyền nhận phòng vẫn theo hạn cũ; chưa được đóng no-show cho đến khi đề nghị được xử lý.

### Duyệt / từ chối nhận muộn

Quản lý mở booking có đề nghị → **Duyệt / từ chối nhận muộn**.
Đối chiếu giờ khách hẹn, hạn đề nghị, giờ trả gốc; nhập lý do rồi chọn quyết định.
Duyệt kéo dài hạn và vẫn phụ thu 0đ. Từ chối xóa đề nghị, giữ nguyên hạn đã được duyệt trước đó hoặc hạn mặc định.
Đề nghị hết hạn không được duyệt nhưng vẫn có thể từ chối. Đề nghị đã thay đổi cần xem lại trước khi quyết định.
Lễ tân không có nút quyết định. Quản lý có thể kết thúc ngoại lệ với lý do/thông báo khách; thao tác không tự đóng no-show.

### Quyết toán không đến

Nút **Xử lý không đến** chỉ hiện khi đã qua cutoff, chưa nhận phòng, không còn hạn nhận muộn có hiệu lực
và không có yêu cầu hủy/đổi lịch/nhận sau hạn đang chờ. Chưa đủ điều kiện thì màn thông tin hiện lý do.

Mở bước này mới thấy tiền đã nhận, tiền còn bù trừ, phí chuẩn một đêm đầu, phí thực tế, phải hoàn và còn phải thu.
Lễ tân dùng phí chuẩn. Quản lý chọn:

- **Áp dụng phí chuẩn:** không phải nhập lại tiền; dùng chi tiết liên hệ làm lý do xác nhận.
- **Giảm phí:** nhập số tiền sau giảm, lớn hơn 0 và nhỏ hơn phí chuẩn; bắt buộc lý do.
- **Miễn phí:** phí 0đ, không mở ô nhập tiền; bắt buộc lý do.

Booking cũ chưa lưu chấp thuận hiện cảnh báo tại bước này; quản lý phải ghi căn cứ, kể cả dùng phí chuẩn.
Chọn kết quả liên hệ, nhập chi tiết làm căn cứ rồi **Xác nhận không đến**.
Thông báo trước xác nhận nêu rõ đóng quyền nhận phòng và giải phóng giữ chỗ.
Ghi phí và xử lý tiền hoàn là hai việc riêng; khoản PENDING chưa có nghĩa đã chuyển tiền.
Mất phản hồi thì tra cứu kết quả; chưa xác minh được sẽ khóa thao tác và yêu cầu **Tra cứu lại kết quả**.

## API bổ sung

- `GET /api/v1/front-desk/arrivals/work?group=WAITING|ACTION_REQUIRED|RESOLVED&reason=...&page=0&size=20`.
- Nhóm `RESOLVED` nhận thêm `resolvedFrom=YYYY-MM-DD&resolvedTo=YYYY-MM-DD`, ví dụ `resolvedFrom=2026-11-01&resolvedTo=2026-11-18`.
  Không gửi cả hai thì dùng ngày hiện tại của khách sạn; gửi thiếu một ngày, khoảng ngược hoặc dùng với nhóm khác trả HTTP 400.
  Mỗi dòng có thêm `resolvedAt` (ISO timestamp, `null` ở nhóm chưa xử lý). Không cần migration database.
- Lý do lọc: `RECEPTION_EXPIRED`, `HOLD_REQUEST`, `CHANGE_REQUEST`, `ETA_AFTER_DEADLINE`, `PAST_ETA`; không gửi reason là tất cả.
- `POST /api/v1/bookings/{code}/arrival-estimate`: `{expectedAt, note}`; thời gian ISO có timezone.
- `POST /api/v1/bookings/{code}/arrival-contact-result`: `{channel, outcome, note}` cho kết quả thông thường.
- `POST /api/v1/bookings/{code}/arrival-hold`: bổ sung `expectedAt` cùng `until, note`.
- `POST /api/v1/bookings/{code}/arrival-hold/decision`: `{requestedUntil, approve, note}`, chỉ MANAGER.
- Hủy/đổi lịch qua `change-requests`; tùy chọn `contactChannel` lưu liên hệ cùng giao dịch tạo yêu cầu.
- Endpoint today/overdue cũ giữ tương thích; giao diện mới sử dụng `/arrivals/work`.

## Kết quả kiểm tra

Ngày 12/11/2026: 210 test backend trong 19 lớp đạt, không bỏ qua test; PostgreSQL Testcontainers dùng database riêng.
177 test frontend trong 28 file đạt; TypeScript/Vite build, ESLint, Spotless và Prettier đạt.
Đã mở các màn danh sách, tóm tắt, liên hệ và quyết toán trên Chrome với API giả lập, không ghi vào dữ liệu ứng dụng.
Các kiểm thử bao gồm phân nhóm/lọc/phân trang, giờ hẹn độc lập hạn nhận, quá giờ hẹn, đúng cutoff/hạn duyệt,
đề nghị đã thay đổi/hết hạn, duyệt/từ chối, phân quyền, liên hệ chọn sẵn, hủy/đổi lịch và quyết toán không trùng.


### Kiểm tra phạm vi bổ sung ngày 13/11/2026

- 75 kiểm thử backend đạt trong `ArrivalHandlingIntegrationTest`, `ArrivalHandlingSecurityIntegrationTest`,
  `HotelStayPolicyTest`, gồm PostgreSQL Testcontainers; không bỏ qua ca nào.
- 65 kiểm thử frontend trong 8 file front-desk đạt; có chọn bốn phòng, một phòng, nhóm hai phòng,
  loại phòng đã check-in, truyền danh sách qua API và chuyển giờ hẹn sang đề nghị nhận sau hạn.
- Đã kiểm tra Chrome với API giả lập: đủ bốn checkbox được chọn, nút lưu mở đúng lúc, cảnh báo
  08:50 vượt cutoff 06:00, hộp thoại có vùng cuộn và nút đóng/quay lại dùng được. Không ghi vào dữ liệu thật.

### Tra cứu lịch sử theo ngày xử lý — 18/11/2026

- 102 test backend đạt, không bỏ qua ca nào, gồm PostgreSQL Testcontainers và phân quyền API.
  Kiểm tra trọn ngày theo giờ khách sạn, booking đúng đầu ngày/cuối khoảng, thứ tự mới nhất trước,
  phân trang, ngày xử lý khác ngày lưu trú và kết quả không đổi khi múi giờ kết nối database thay đổi.
- 22 test frontend liên quan đạt; TypeScript/Vite build, ESLint, Spotless và Prettier đạt.
- Đã kiểm tra Chromium với API giả lập trên desktop và điện thoại: mở **Đã xử lý**, chọn **7 ngày qua**,
  nhập khoảng **01/10/2026–31/10/2026**, xác nhận request gửi đúng hai ngày và quay về trang đầu.
  Booking cũ có cột **Thời điểm xử lý** và nút **Xem kết quả**.
- Khởi động lại backend và tải lại frontend để nạp thay đổi. Không cần migration database.
