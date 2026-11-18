---
documentId: HANBAY-BOOKING-POLICY
title: Booking Policy
category: BOOKING
source: docs/knowledge-base/booking-policy.md
version: 1.6
hotel: HANBAY HOTEL
language: vi
---

# HANBAY HOTEL — Chính sách đặt phòng

## 1. Mục đích

Mô tả quy tắc tạo booking, giữ inventory, sức chứa và xác nhận booking trong hệ thống demo.

## 2. Phạm vi

Áp dụng cho booking phòng được tạo qua HANBAY HOTEL. Không mô tả đặt phòng ngoài hệ thống.

## 3. Quy định

### 3.1 Khoảng lưu trú

Khoảng lưu trú là `[checkInDate, checkOutDate)`: ngày check-in được tính, ngày check-out
không được tính. Check-out phải sau check-in và check-in không được ở quá khứ.

### 3.2 Sức chứa

Booking phải có ít nhất một người lớn. Số trẻ em không được âm. Room type chỉ phù hợp khi
số người lớn không vượt `capacityAdults`, số trẻ em không vượt `capacityChildren`, và
tổng số khách không vượt `maxGuests`.

Chính sách mẫu được chủ dự án duyệt: tuổi tại ngày nhận phòng dưới 6 là trẻ nhỏ, từ 6 đến
dưới 12 là trẻ em, từ 12 là người lớn. Trẻ nhỏ và trẻ em đều tính vào giới hạn trẻ em và tổng
khách, dùng giường có sẵn. Checkout yêu cầu tuổi từng trẻ em; backend kiểm tra và lưu
`childAges` nếu được gửi. Booking/API cũ chưa có tuổi giữ trạng thái chưa xác định, không đoán.
Family Double Queen cho tối đa 4 người lớn, 2 trẻ em nhưng tổng không quá 4 khách.
Không tự miễn phí, thêm phụ thu hoặc suất ăn dựa trên tuổi; dịch vụ theo số lượng đã chọn.

### 3.3 Availability

Tính từng đêm [ngày nhận,ngày trả), lấy tồn thấp nhất cả kỳ. Nguồn phòng của đúng hạng
loại MAINTENANCE/OUT_OF_SERVICE; trừ CONFIRMED, CHECKED_IN và PENDING có expiredAt > now.
Booking đang ở đã quá hạn tiếp tục chặn tồn, không tự giải phóng theo ngày trả cũ.
Không trừ gộp mọi booking chạm cả khoảng. `availableRooms` cho kỳ khác `readyRooms` hiện tại.

### 3.4 Booking PENDING

Booking mới ở trạng thái `PENDING` và giữ inventory theo cấu hình PAYMENT_EXPIRATION_MINUTES, mặc định 15 phút. Sau hạn, scheduler có
thể chuyển booking chưa được xác nhận sang `EXPIRED`.

### 3.5 Xác nhận booking

Booking `PENDING` còn hạn được chuyển sang `CONFIRMED` khi hệ thống ghi nhận đủ tiền qua
payment flow. Booking hết hạn không được xác nhận bằng payment đến trễ.

### 3.6 Trạng thái booking và công nợ

Trạng thái booking mô tả vòng đời đặt/lưu trú, không thay thế trạng thái thanh toán.
`PENDING` là booking mới chờ xác nhận, chưa được check-in và không phải chế độ trả sau.
Luồng đặt hiện tại yêu cầu thanh toán đủ khoản ban đầu trong thời hạn để xác nhận booking;
chưa có lựa chọn đặt chưa trả tiền ban đầu rồi nhận phòng và trả toàn bộ khi checkout.

Sau khi đã xác nhận, gia hạn có thể làm tăng tổng tiền và tạo công nợ nhưng booking vẫn
`CONFIRMED` hoặc `CHECKED_IN`, không quay về `PENDING`. Ví dụ đã trả 2.000.000 VND,
gia hạn thêm 500.000 VND thì công nợ là 500.000 VND. Khoản này có thể thanh toán bổ sung
hoặc quyết toán tại checkout. Booking `CONFIRMED` vẫn phải đáp ứng ngày/phòng/quyền khi check-in.

### 3.7 Sửa booking đang chờ

Chỉ sửa booking `PENDING` còn hạn với quyền chủ booking hoặc booking access token hợp lệ.
Hệ thống kiểm tra lại ngày, sức chứa, tồn kho và tính lại snapshot giá/phòng/dịch vụ.
Sửa booking không cộng thêm 15 phút; hạn giữ chỗ ban đầu được giữ nguyên.

### 3.8 Khách không đến

Nhân viên được xác nhận `CONFIRMED → NO_SHOW` sau cutoff nếu chưa nhận phòng và
không có thỏa thuận giữ còn hiệu lực. Phí chuẩn bằng tiền phòng đêm đầu đã chốt;
quản lý có quyền miễn/giảm kèm lý do. Tổng nghĩa vụ sau quyết toán bằng phí được
duyệt, lưu riêng tổng tiền gốc và các snapshot giá. Booking giải phóng giữ chỗ
còn lại, khoản hoàn được theo dõi riêng. Đơn nhiều phòng xử lý từng booking.
Xem [chính sách đến muộn và không đến](no-show-policy.md).

### 3.9 Khách chủ động hủy hoặc đổi lịch trước khi nhận phòng

Khách xác nhận hủy thì CANCELLED và giải phóng ngay. Booking cũ thiếu căn cứ phí chỉ chờ
quản lý quyết toán, không chờ duyệt việc giải phóng. Đổi lịch giữ ngày cũ khi đang chờ;
không kéo dài cutoff và không tự bảo đảm phòng kỳ mới. Booking V2 đã chấp thuận áp phí
0/50%/100% giá đêm đầu gốc theo mốc 48/24 giờ; tổng phí toàn chuỗi không vượt giá đó.
Đổi cùng hạng/cùng số đêm một lần trong 90 ngày, báo giá lại và khách chấp thuận trước
quản lý áp dụng. Dịch vụ và tuổi trẻ em xác nhận lại. Đơn nhiều phòng hủy/đổi toàn đơn,
no-show từng phòng. Booking cũ giữ điều khoản cũ. Xem [chính sách hủy/đổi](cancellation-policy.md).

## 4. Quy trình

1. Guest chọn ngày, occupancy và room type còn capacity/inventory.
2. Backend kiểm tra lại availability trong transaction và tạo booking `PENDING`.
3. Booking giữ inventory tới trước expiredAt (mặc định 15 phút).
4. Thanh toán đủ khi booking còn hiệu lực chuyển booking sang `CONFIRMED`.
5. Booking chưa xác nhận khi hết hạn chuyển sang `EXPIRED`.

## 5. Các trường hợp không hợp lệ

- Check-in ở quá khứ hoặc check-out không sau check-in.
- Không có người lớn, số trẻ em âm hoặc vượt capacity của room type.
- Inventory không còn đủ do booking overlap.
- Cố xác nhận booking không còn `PENDING` hoặc đã hết hạn.

## 6. Ví dụ

Booking tạo lúc 10:00 ở trạng thái `PENDING` có hạn giữ đến 10:15. Nếu chưa được xác nhận
khi hết hạn, booking có thể chuyển `EXPIRED` và không tiếp tục giữ inventory.

## 7. Source of Truth

- `docs/02-business-requirements.md`
- `docs/03-software-requirements.md`
- `docs/04-system-architecture.md`
- `docs/06-api-specification.md`
- `docs/07-business-workflows.md`
- `BookingFactory.java`, `BookingRepository.java`, `Booking.java`
- `BookingServiceImpl.java`, `RoomType.java`

### Mốc đặt cùng ngày và giờ nhận phòng

Theo Asia/Ho_Chi_Minh mặc định,14:00 là bắt đầu nhận phòng chuẩn, không phải hạn đặt.
Vẫn được đặt nhận hôm nay lúc 19:55 nếu còn phòng, chưa tới cutoff 23:59:59 và đủ điều kiện.
Đúng cutoff không nhận đặt mới hôm nay; không đặt ngày nhận quá khứ.
03→05/11 tính2 đêm03 và04, trả12:00 ngày05; nhận tối không tự giảm tiền hoặc dời ngày.
Hạn thanh toán riêng là expiredAt: đúng hạn không còn được xác nhận hoặc giữ tồn,
không chờ scheduler đổi nhãn; sửa pending không kéo dài hạn.
API `/api/v1/stay-policy` là nguồn cấu hình/ngày/giờ máy chủ.
Nguồn: HotelStayPolicy, BookingFactory/BookingServiceImpl, RoomAvailabilityCalculator,
BookingRepository và [tài liệu23](../23-stay-time-and-inventory.md).
