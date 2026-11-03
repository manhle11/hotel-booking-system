# Báo cáo chuẩn hóa thời gian lưu trú — 03/11/2026

Triển khai trực tiếp trên nhánh `main`. Ngày lưu trú được tách khỏi giờ nhận/trả thực tế
và thời hạn giữ chỗ thanh toán. Thay đổi trước đó về liên kết tên hạng phòng, cập nhật
tồn phòng và bảo vệ dữ liệu phòng đã thiết lập được giữ lại.

## Quy tắc và cấu hình

| Quy tắc | Mặc định | Biến môi trường |
|---|---|---|
| Múi giờ | Asia/Ho_Chi_Minh | HOTEL_TIME_ZONE |
| Giờ nhận phòng | 14:00 | BOOKING_CHECK_IN_TIME |
| Giờ trả phòng | 12:00 | BOOKING_CHECK_OUT_TIME |
| Chốt đặt phòng hôm nay | 23:59:59 | SAME_DAY_BOOKING_CUTOFF |
| Khách đến muộn hôm sau | Trước 06:00 | LATE_ARRIVAL_CUTOFF |
| Giữ chỗ thanh toán | 15 phút | PAYMENT_EXPIRATION_MINUTES |

Giờ chốt đặt phòng độc lập với giờ nhận phòng. Có thể đặt hôm nay sau 14:00 nếu còn
phòng và chưa qua giờ chốt. API công khai `GET /api/v1/stay-policy` cung cấp cấu hình,
ngày tại khách sạn và giờ máy chủ cho frontend; backend kiểm tra lại mọi điều kiện.

## Tồn phòng và số đêm

- Khoảng lưu trú là `[ngày nhận, ngày trả)`. Nhận 05/11, trả 13/11 tương ứng 8 đêm.
- Mỗi đêm trừ booking xác nhận, đang lưu trú và chờ thanh toán còn hạn giữ chỗ.
- Số phòng cho cả kỳ là tồn thấp nhất trong từng đêm, áp dụng cho tạo mới, sửa và gia hạn.
- Ví dụ có 2 phòng: booking A ở 05–06/11 và B ở 06–07/11 thì vẫn còn 1 phòng cho 05–07/11.
- Loại phòng bảo trì/ngừng phục vụ khỏi nguồn cung trong khi trạng thái đó còn hiệu lực.
- Booking còn CHECKED_IN sau giờ trả tiếp tục chặn tồn kho; không tự giải phóng phòng.
- Chọn phòng vật lý khi nhận phải đúng hạng, AVAILABLE và không được gán trùng lịch.
- Giao diện dùng chung phép đếm ngày lịch và nhãn ngày nhận/trả. Kết quả tìm kiếm ưu tiên
  số đêm do backend trả về; không tính số đêm bằng tổng số giờ chia 24.

## Nhận/trả và giữ chỗ

- Nhận sớm trong ngày: yêu cầu lễ tân nhập lý do, kiểm tra phòng đã sẵn sàng, ghi lịch sử.
- Đến sau nửa đêm: tiếp nhận booking ngày trước trong cửa sổ cấu hình; giữ nguyên ngày lưu trú.
  Danh sách khách đến bao gồm các booking này khi cửa sổ còn mở.
- Trả muộn/quá hạn: cảnh báo trong danh sách và modal, bắt buộc ghi chú của lễ tân.
  Phí phát sinh nhập theo chính sách tại quầy; không tự áp đặt mức phí hay tự cộng đêm.
- Trả sớm: ngày thực tế phải là hôm nay tại khách sạn; luồng báo giá vẫn cho xem trước.
- Khi trả xong phòng chuyển DIRTY; chỉ AVAILABLE sau khi hoàn tất dọn.
- Đúng thời điểm hết hạn giữ chỗ: không được xác nhận booking, không còn chiếm tồn kho.
  Đồng bộ cùng cấu hình thời hạn giữa booking và yêu cầu thanh toán.
- Giờ nhận/trả thực tế lưu `checked_in_at`, `checked_out_at` dạng TIMESTAMPTZ.

## Migration và dữ liệu

Migration `V202611031800__record_actual_stay_times.sql` bổ sung hai mốc thực tế,
chuẩn hóa `bookings.expired_at` sang TIMESTAMPTZ theo cấu hình JDBC GMT+7 trước đó,
và thêm chỉ mục cho booking active. Các booking cũ giữ nguyên ngày và trạng thái;
không suy đoán giờ nhận/trả thực tế khi thiếu dữ liệu.

## Phạm vi vận hành

Khách đến ngoài cửa sổ tiếp nhận và khách cần gia hạn được lễ tân xử lý qua booking.
Không tự hủy booking CONFIRMED vì qua nửa đêm. Bảo trì hiện là trạng thái đang có
hiệu lực; chưa bổ sung lịch bảo trì dự kiến theo khoảng ngày. Ghi chú và phí trả muộn
được quyết định tại quầy; chưa có luồng duyệt trước một giờ trả muộn cụ thể.

## Kiểm tra

- Build frontend và đóng gói backend thành công. Spotless và kiểm tra định dạng đạt.
- Nhóm kiểm thử backend cuối: **52/52 đạt**, gồm quy tắc giờ/múi giờ, luồng tạo/sửa
  booking, nhận sớm/đến muộn, trả muộn và SQL tồn phòng trên PostgreSQL tạm.
- Nhóm kiểm thử giao diện lễ tân cuối: **23/23 đạt**; các kiểm thử tìm phòng,
  đặt phòng, số đêm và đồng bộ giờ máy chủ cũng đã chạy đạt.
- Đã chạy mở rộng các nhóm giá, thanh toán, phân quyền và tương thích Flyway trên DB
  mới/DB đã có migration. Lỗi fixture đồng hồ giả đã được sửa và chạy lại đạt.
- Chrome headless kiểm tra trực tiếp localhost: chọn 05–13/11/2026, kết quả và tất cả
  thẻ đều **8 đêm**, ghi chú **14:00/12:00**, liên kết tên hạng phòng mở đúng chi tiết,
  không có lỗi JavaScript của trang.
- Backend cục bộ đã khởi động lại; Flyway áp dụng migration mới thành công.
- Đối chiếu API qua frontend với truy vấn độc lập theo từng đêm trong PostgreSQL: khớp.
- Sau migration vẫn có **111 phòng và 26 booking**; không xóa hoặc tự kết thúc booking cũ.

## Kết quả đối chiếu DB đang sử dụng

Kỳ 05–13/11/2026, 2 người lớn, 0 trẻ em:

| Hạng phòng | Tổng phòng | Bảo trì/ngừng phục vụ | Booking active/quá hạn chiếm tồn | Có thể đặt |
|---|---:|---:|---:|---:|
| Junior Suite | 12 | 0 | 1 | 11 |
| Deluxe | 40 | 1 | 1 | 38 |
| Suite hướng biển | 11 | 0 | 1 | 10 |
| Superior | 48 | 1 | 2 | 45 |

Năm booking trong DB vẫn đang `CHECKED_IN` dù đã qua ngày trả dự kiến. Theo quy tắc
mới, các booking này tiếp tục giữ phòng đến khi lễ tân xử lý trả phòng/gia hạn.
Vì vậy số có thể đặt giảm tương ứng so với cách chỉ xét ngày trả dự kiến trước đây.

Thay đổi đang ở working tree của nhánh `main`, chưa commit/push.

## Bổ sung giải thích và phạm vi tài liệu

14:00 là bắt đầu nhận, không phải hạn cuối đặt. Tạo pending03→05/11 lúc 19:55 ngày 03/11
là đúng policy nếu còn tồn, trước cutoff 23:59:59; kỳ ở2đêm. expiredAt hiển thị là hạn thanh toán
riêng, không chờ cutoff. Đúng cutoff không nhận đặt mới cùng ngày; đúng expiredAt không xác nhận hold.
[Quy tắc đầy đủ](../23-stay-time-and-inventory.md) và [16 luồng FE](../testing/stay-time-fe-checklist.md)
được đồng bộ với API/KB/sơ đồ. Số phòng/kết quả test phía trên là snapshot lần triển khai đã ghi nhận,
không phải truy vấn DB hoặc chạy test lại trong đợt sửa tài liệu. Bộ334 ca UAT vẫn Chưa chạy.
