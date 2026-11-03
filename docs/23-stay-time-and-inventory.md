# 23. Thời gian lưu trú, tồn phòng và nhận/trả phòng

Đối chiếu implementation trong working tree `main` ngày **03/11/2026**. Đây là tài liệu
nghiệp vụ hiện tại cho các thay đổi liên kết hạng phòng, ngày/số đêm, tồn phòng và mốc
nhận/trả; không phải biên bản xác nhận mọi ca kiểm thử đã đạt.

## 1. Bốn mốc độc lập

| Mốc | Mặc định | Ý nghĩa / biên chính xác |
|---|---|---|
| Bắt đầu nhận phòng | 14:00 | Từ giờ này nhận phòng chuẩn trong ngày đặt; trước giờ này cần lý do lễ tân duyệt nhận sớm. Đây **không phải hạn cuối đặt phòng**. |
| Trả phòng chuẩn | 12:00 | Đúng 12:00 chưa quá hạn; sau 12:00 cần ghi chú xử lý trả muộn. |
| Ngừng nhận đặt cùng ngày | 23:59:59 | Chỉ tạo/sửa lựa chọn nhận hôm nay khi giờ khách sạn **nhỏ hơn** cutoff; đúng cutoff bị từ chối. Ngày tương lai không bị đóng bởi cutoff hôm nay. |
| Hạn giữ chỗ của booking | `expiredAt` | Mặc định 15 phút từ lúc tạo; phải thanh toán/xác nhận **trước** hạn. Đúng hạn không còn giữ tồn hoặc cho xác nhận. |

Múi giờ mặc định là `Asia/Ho_Chi_Minh`. Cửa sổ tiếp nhận khách của booking hôm qua
kéo dài tới **trước 06:00 hôm nay**; đúng 06:00 không được nhận theo ngoại lệ này.

### Ví dụ 19:55 ngày 03/11/2026

Khách vẫn có thể đặt **03/11 → 05/11**, nếu đủ tồn và sức chứa, vì 19:55 chưa tới
23:59:59. Kỳ ở có **2 đêm: 03 và 04/11**, trả chuẩn lúc 12:00 ngày 05/11.
Nhận sau 14:00 không tự giảm tiền đêm 03/11 hoặc dời ngày nhận sang 04/11.
Nếu trang ghi hạn giữ 20:09 ngày 03/11 thì phải thanh toán trước **20:09** cho hold đó;
không được hiểu rằng có thể chờ đến cutoff 23:59:59 mới thanh toán.

Cutoff cùng ngày kiểm soát yêu cầu đặt mới/lựa chọn ngày, không thay `expiredAt` của
hold đã tạo. Hạn payment không vượt hạn hold; việc mở lại QR hoặc sửa pending không
cộng thêm thời gian giữ. Một hold qua nửa đêm vẫn phải được kiểm theo hạn và trạng thái
thực tế; không tự hủy booking CONFIRMED chỉ vì qua ngày.

## 2. Cấu hình và đồng hồ

| Biến môi trường | Giá trị mặc định |
|---|---|
| `HOTEL_TIME_ZONE` | `Asia/Ho_Chi_Minh` |
| `BOOKING_CHECK_IN_TIME` | `14:00` |
| `BOOKING_CHECK_OUT_TIME` | `12:00` |
| `SAME_DAY_BOOKING_CUTOFF` | `23:59:59` |
| `LATE_ARRIVAL_CUTOFF` | `06:00` |
| `PAYMENT_EXPIRATION_MINUTES` | `15` |

`GET /api/v1/stay-policy` là API công khai trả `zone`, `checkInTime`, `checkOutTime`,
`sameDayBookingCutoff`, `lateArrivalCutoff`, `paymentHoldMinutes`, `hotelToday`,
`serverNow`. Cancellation dùng chung giờ nhận/múi giờ này, không có giờ 14:00 độc lập.

Frontend đọc policy mỗi 60 giây/khi focus, bù chênh lệch đồng hồ máy khách và cập nhật
giờ hiển thị mỗi 30 giây. Backend kiểm lại điều kiện khi thao tác; giao diện sát mốc
có thể chưa refresh ngay. Nếu API policy chưa tải được, frontend dùng mặc định;
mặc định trên tài liệu không chứng minh cấu hình đang chạy là đúng giá trị đó.

## 3. Ngày lưu trú và số đêm

- Khoảng ở là `[checkInDate, checkOutDate)`, ngày trả không chiếm thêm đêm.
- Ngày trả phải sau ngày nhận; tối thiểu một đêm. Không đặt mới ngày nhận quá khứ.
- 05/11 → 13/11 là **8 đêm**, kể cả nhận lúc 14:00 và trả lúc 12:00.
- Không tính số đêm bằng cách lấy tổng số giờ chia 24 rồi làm tròn xuống.
- Kết quả tìm kiếm dùng `totalNights` backend; các bước khác dùng helper ngày lịch
  thống nhất. Ngày, tổng và từng đêm phải tương ứng cùng lần tìm đã gửi.

## 4. Tồn có thể đặt và phòng sẵn sàng

`availableRooms` trên nhãn **Còn … phòng** là tồn cho toàn kỳ khách chọn.
`readyRooms` là số phòng vật lý đang `AVAILABLE` lúc truy vấn, chưa trừ lịch tương lai;
đây là chỉ số vận hành riêng, không dùng thay số trên thẻ.

```text
sellable = số phòng của đúng hạng có trạng thái AVAILABLE/OCCUPIED/DIRTY/CLEANING
demand(night) = CONFIRMED + CHECKED_IN + PENDING có expiredAt > now tại đêm đó
availableRooms = max(0, sellable - max(demand(night)))
```

Phòng MAINTENANCE/OUT_OF_SERVICE bị loại khỏi nguồn bán trong khi trạng thái còn hiệu lực.
Mỗi booking thường chiếm đêm khi `checkInDate <= night < checkOutDate`. Booking đang
CHECKED_IN đã quá hạn vẫn chặn tồn về sau tới khi nhân viên xử lý; native query dùng
`overdueThrough` của policy để xác định nhóm quá hạn. Đúng 12:00 ngày trả chưa được
coi quá hạn, sau 12:00 mới tính quá hạn ngày đó. Không tự đổi trạng thái hoặc tự trả
phòng khi đồng hồ vượt deadline.

Ví dụ 2 phòng, A ở 05–06/11, B ở 06–07/11: còn **1 phòng** cho 05–07/11, không phải0.
Sửa booking phải loại chính nó khỏi phép đếm; gia hạn chỉ kiểm đoạn thêm từ ngày trả
cũ tới ngày trả mới, đồng thời kiểm lịch phòng đang gán.

Frontend tìm lại mỗi 30 giây, khi focus/reconnect và khi inventory quản trị bị invalidate.
Kết quả vẫn giữ ngày/số khách đã submit nếu form đang sửa nhưng chưa bấm tìm lại.
Backend khóa và kiểm lại khi giữ phòng; số đã thấy trên thẻ không bảo đảm phòng vẫn còn.

## 5. Nhận phòng

Chỉ RECEPTIONIST thực hiện, booking phải CONFIRMED. Nhận trong ngày dự kiến hoặc
ngoại lệ hôm qua khi `checkInDate = hotelToday - 1`, `checkOutDate >= hotelToday`
và giờ hiện tại `< lateArrivalCutoff`. Đến muộn không sửa ngày/giá snapshot gốc.
Danh sách khách đến thêm nhóm hôm qua này khi cửa sổ còn mở.

Trước giờ nhận trong chính ngày đặt, cần `earlyArrivalReason` có nội dung, tối đa 500
ký tự. Đúng/từ14:00 không cần lý do nhận sớm. Phòng phải đúng hạng, AVAILABLE, không
có booking CONFIRMED/CHECKED_IN khác được gán xung đột từ hôm nay tới ngày trả.
Danh sách phòng chỉ là snapshot; API nhận khóa và kiểm lại.

Thành công: gán phòng, booking CHECKED_IN, phòng OCCUPIED, ghi `checked_in_at` và lịch sử.
Không cho PENDING chưa được xác nhận bỏ qua thanh toán ban đầu để nhận phòng.

## 6. Trả phòng và gia hạn

Checkout thường dành cho ngày trả dự kiến hoặc muộn hơn, với RECEPTIONIST/MANAGER/ADMIN.
Sau12:00 ngày trả phải có `note`; phí phát sinh do nhân viên ghi nhận và có lý do,
không có công thức tự cộng một đêm. Thu đúng số còn thiếu, không thu lại tiền QR đã nhận.
Thành công ghi `checked_out_at`, chuyển CHECKED_OUT và phòng DIRTY; dọn theo
DIRTY→CLEANING→AVAILABLE trước khi bàn giao tiếp.

Trả sớm có quote riêng, nhưng execute chỉ nhận `actualCheckOutDate = hotelToday`,
không ghi lùi/ghi trước ngày thực tế. Công thức credit/refund và thu bù không thay đổi;
xem [stay modification policy](knowledge-base/stay-modification-policy.md).

Gia hạn do chủ booking thực hiện, chỉ CONFIRMED/CHECKED_IN; ngày trả mới phải tiến lên.
Kiểm cả tồn hạng cho đoạn thêm và lịch phòng đã gán. Giữ phòng cũ hoặc từ chối,
không tự chuyển phòng; giữ snapshot đêm cũ và tính giá hiện tại cho đêm thêm.

## 7. DB và dữ liệu demo

Migration `V202611031800__record_actual_stay_times.sql` thêm `checked_in_at`,
`checked_out_at` kiểu TIMESTAMPTZ; chuyển `expired_at` từ dữ liệu legacy GMT+7 sang
TIMESTAMPTZ và tạo index active booking nights. Ngày ở vẫn là DATE; thời điểm thực tế
là instant UTC. Bản ghi cũ không rõ giờ để null, không tự điền14:00/12:00.

Initializer Ocean Suite chỉ seed phòng demo khi hạng chưa có phòng. Khởi động lại
không được tự bù thêm phòng demo vào inventory đã thiết lập. Tài liệu số phòng trong
báo cáo cũ là snapshot tại lần đối soát, không phải số bất biến của mọi ngày.

## 8. Tài liệu và kiểm thử liên quan

- [16 luồng FE chỉ cho phần vừa thay đổi](testing/stay-time-fe-checklist.md).
- [Hướng dẫn UAT toàn web](testing/web-uat-guide.md), [334 ca](testing/web-test-cases.md).
- [API specification](06-api-specification.md), [OpenAPI stay policy và tìm phòng](openapi/room/stay-policy-and-availability.yaml).
- [Báo cáo triển khai đã ghi nhận](reports/2026-11-03-stay-time-implementation.md).

Các ca cutoff, 06:00, 12:00, 14:00, đúng expiredAt cần Clock backend kiểm soát để
kiểm biên chính xác. Đổi giờ browser không kiểm được rule backend. Bộ334 ca và
checklist FE là kịch bản; trạng thái mặc định Chưa chạy, không phải kết quả thực thi.
