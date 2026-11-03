---
documentId: HANBAY-CHECK-IN-POLICY
title: Check-in Policy
category: CHECK_IN
source: docs/knowledge-base/check-in-policy.md
version: 1.3
hotel: HANBAY HOTEL
language: vi
---

# HANBAY HOTEL — Chính sách check-in

## 1. Mục đích

Mô tả điều kiện gán phòng vật lý và check-in.

## 2. Phạm vi

Áp dụng cho thao tác nghiệp vụ của `RECEPTIONIST` trên booking đã xác nhận.

## 3. Quy định

### 3.1 Giờ nhận phòng

Giờ nhận phòng mặc định là **14:00** theo `Asia/Ho_Chi_Minh`, lấy từ cấu hình dùng
chung với chính sách hủy. Nhận phòng sớm trong ngày phải có lý do xác nhận của lễ tân;
phòng phải `AVAILABLE` và không có booking khác được gán trùng khoảng lưu trú.
Không tự áp đặt phí nhận sớm.

### 3.2 Điều kiện booking

Chỉ booking `CONFIRMED` được check-in. Cho nhận trong ngày dự kiến hoặc đến muộn sau
nửa đêm đến trước **06:00 hôm sau** (cấu hình được), miễn booking còn trong thời gian
lưu trú. Trường hợp đến muộn giữ nguyên ngày nhận/trả đã đặt. Ngoài khung này lễ tân
phải điều chỉnh booking; hệ thống không tự đổi ngày hoặc tự hủy booking đã xác nhận.
Giờ nhận phòng thực tế được lưu tại `bookings.checked_in_at` bằng timestamp có múi giờ.

Booking `PENDING` chưa được xác nhận nên không được check-in, kể cả khách muốn trả tiền
lúc checkout. Booking đã xác nhận rồi gia hạn có thể còn công nợ nhưng vẫn `CONFIRMED`;
service check-in không kiểm tra lại `paidAmount` hoặc yêu cầu công nợ bằng 0. Booking đó
có thể check-in nếu trong cửa sổ nhận hợp lệ, đúng phòng và đúng quyền; khoản nợ còn lại phải được quyết toán.

### 3.3 Điều kiện phòng vật lý

Phòng được chọn phải tồn tại, đúng room type, AVAILABLE và không có booking CONFIRMED/CHECKED_IN khác được gán xung đột từ hôm nay tới ngày trả. API tìm phòng không giữ chỗ; khi nhận khóa và kiểm lại.

### 3.4 Phân quyền và trạng thái sau check-in

Chỉ `RECEPTIONIST` được gọi check-in endpoint. Thành công sẽ gán phòng, chuyển booking sang
`CHECKED_IN` và chuyển phòng từ `AVAILABLE` sang `OCCUPIED`.

Tài khoản chỉ có `MANAGER` hoặc `ADMIN` không được check-in nếu không có role `RECEPTIONIST`.
Những vai trò này có thể xem danh sách khách đến. Gửi lại check-in đã thành công bị từ chối
với `BOOKING_ALREADY_CHECKED_IN`, không gán thêm một phòng.

## 4. Quy trình

1. Receptionist chọn booking CONFIRMED trong ngày nhận hoặc cửa sổ đến muộn hợp lệ.
2. Hệ thống liệt kê phòng AVAILABLE đúng hạng, không có lịch gán xung đột; nhận sớm cần lý do.
3. Backend khóa booking và phòng, rồi kiểm tra lại mọi điều kiện.
4. Booking và phòng được cập nhật trong cùng transaction.

## 5. Các trường hợp không hợp lệ

- Booking không CONFIRMED, đã check-in hoặc ngoài cửa sổ tiếp nhận theo policy.
- Phòng khác room type, không tồn tại hoặc không còn `AVAILABLE`.
- Người gọi không có role `RECEPTIONIST`.

## 6. Ví dụ

Booking Deluxe `CONFIRMED` hôm nay chỉ có thể được gán một phòng Deluxe đang
`AVAILABLE`; không thể gán phòng Suite hoặc phòng `MAINTENANCE`.

## 7. Source of Truth

- `docs/03-software-requirements.md`
- `docs/06-api-specification.md`
- `docs/07-business-workflows.md`
- `CheckInController.java`, `CheckInServiceImpl.java`
- `Booking.java`, `Room.java`

### Phân biệt giờ bắt đầu nhận và hạn đặt

14:00 không phải giờ đóng đặt phòng:19:55 ngày 03/11 vẫn đặt03→05/11 được trước cutoff 23:59:59,
nếu còn phòng. Thanh toán đủ trong hạn mới confirmed; sau đó lễ tân nhận khi phòng sẵn sàng.
Trước14:00 cùng ngày cần earlyArrivalReason có nội dung≤500 ký tự. Đúng 14 không cần lý do sớm.
Ngoại lệ hôm qua: checkInDate = hotelToday-1, checkOutDate >= hotelToday và giờ <06:00;
đúng 06:00 bị chặn. Ngoài cửa sổ cần xử lý booking theo nghiệp vụ, không tự đổi ngày/hủy.
Nguồn: HotelStayPolicy; [tài liệu23](../23-stay-time-and-inventory.md).
