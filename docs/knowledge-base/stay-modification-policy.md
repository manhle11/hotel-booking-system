---
documentId: HANBAY-STAY-MODIFICATION-POLICY
title: Chính sách trả phòng sớm và gia hạn lưu trú
category: STAY_MODIFICATION
source: docs/knowledge-base/stay-modification-policy.md
version: 1.4
hotel: HANBAY HOTEL
language: vi
---

# HANBAY HOTEL — Chính sách trả phòng sớm và gia hạn lưu trú

## 1. Mục đích

Mô tả điều kiện trả phòng sớm, cách tính hoàn tiền cho đêm chưa sử dụng và quy tắc giữ
phòng vật lý khi gia hạn lưu trú.

## 2. Phạm vi

- Trả phòng sớm áp dụng cho booking đang `CHECKED_IN`.
- Gia hạn lưu trú áp dụng cho booking `CONFIRMED` hoặc `CHECKED_IN`.
- Các business command sử dụng `bookingId`; `bookingCode` chỉ dùng để tìm kiếm và hiển thị
  cho người dùng.

## 3. Trả phòng sớm

### 3.1 Ngày trả phòng sớm

`actualCheckOutDate` không được trước ngày check-in và phải trước ngày checkout gốc. Nếu
ngày thực tế bằng ngày trả phòng gốc thì đó là trả phòng thông thường, không phải trả phòng sớm.

Khoảng lưu trú tuân theo `[checkInDate, checkOutDate)`: ngày checkout không phải một đêm
lưu trú. Các đêm chưa sử dụng là booking night có `stayDate >= actualCheckOutDate`.

### 3.2 Số tiền hoàn

Backend tính giá trị đêm chưa dùng từ price snapshot; client không quyết định số tiền hoàn:

- `Unused Amount = tổng giá trị các đêm chưa sử dụng`.
- `Penalty Amount = Unused Amount × 20%`.
- `Khoản giảm trước bù trừ = Unused Amount × 80%`.
- `D = max(totalAmount - paidAmount, 0) + additionalChargeAmount`.
- `Refund Amount thực tế = max(Khoản giảm trước bù trừ - D, 0)`.
- `Số tiền cần thu thêm = max(D - Khoản giảm trước bù trừ, 0)`.

Ví dụ: unused amount 2.000.000 VND thì penalty là 400.000 VND và khoản giảm trước bù trừ
là 1.600.000 VND. Nếu còn nợ 300.000 VND và phí phát sinh 200.000 VND, thực hoàn 1.100.000 VND.
Nếu không còn nợ/phí thì thực hoàn 1.600.000 VND. Giá add-on không tự nằm trong unused nights.

Nhân viên có thể nhập phí phát sinh kèm lý do và tiền mặt thực thu; phải thu đúng số tiền
cần thu sau bù trừ. Quote tính giá trị đêm chưa dùng; lệnh trả sớm tính lại và bù công nợ/phí.

Quy tắc 20%/80% chỉ dành cho trả phòng sớm, không áp dụng cho hủy đặt phòng.

### 3.3 Refund và trạng thái phòng

Khi số tiền thực hoàn lớn hơn 0, refund dùng `BANK_TRANSFER` và cần tên ngân hàng, tên chủ
tài khoản, số tài khoản. Không cần thông tin nhận hoàn khi không có khoản hoàn. Hệ thống
không yêu cầu mật khẩu ngân hàng, OTP, CVV hoặc PIN. Refund bắt đầu ở `PENDING`; chưa phải
tiền đã về tài khoản. `MANAGER` xác nhận hoàn sau đối soát bằng mã tham chiếu ngân hàng;
không có lệnh chuyển tiền ra tự động hoặc callback refund của provider trong flow hiện tại.

Trả phòng sớm thành công chuyển booking sang `CHECKED_OUT` và phòng từ `OCCUPIED` sang
`DIRTY`, giống normal checkout. Booking và phòng được khóa để retry hoặc request đồng thời
không tạo checkout/refund trùng.

## 4. Gia hạn lưu trú

### 4.1 Ngày và giá gia hạn

`newCheckOutDate` phải sau checkout date hiện tại. Backend kiểm tra lại availability, tính
giá các đêm bổ sung theo pricing hiện hành và lưu price snapshot; client không cung cấp
additional amount.

Phần tiền bổ sung làm tăng total và outstanding balance; giá các đêm cũ được giữ nguyên.
Booking vẫn `CONFIRMED` hoặc `CHECKED_IN`, không quay lại `PENDING` khi chưa thanh toán phần thêm.

Booking `CONFIRMED`/`CHECKED_IN` còn nợ có thể tạo payment request theo số dư còn lại và thanh
toán chuyển khoản. Khoản chưa trả có thể được thu tại checkout; checkout thường chỉ hoàn tất
khi công nợ sau cộng phí và thu tiền bằng 0. Trả sớm còn áp dụng bù trừ giá trị đêm chưa dùng.
Thu bổ sung tại checkout không cho phép booking mới `PENDING` bỏ qua xác nhận/check-in.

### 4.2 Booking đã có phòng vật lý

Hệ thống phải giữ nguyên assigned room. Nếu chính phòng đó không thể phục vụ toàn bộ khoảng
gia hạn, request bị từ chối với `ROOM_NOT_AVAILABLE_FOR_EXTENSION`. Hệ thống không tự động
chuyển khách sang phòng khác và không có room-move workflow.

### 4.3 Booking chưa có phòng vật lý

Availability được kiểm tra ở room-type level. Gia hạn không gán phòng sớm; assigned room
vẫn để trống cho đến check-in.

## 5. Phân quyền và an toàn

- `RECEPTIONIST`, `MANAGER` hoặc `ADMIN` thực hiện trả phòng sớm.
- Guest chỉ gia hạn booking thuộc chính tài khoản của mình.
- Backend khóa pessimistic và kiểm tra availability trong transaction.
- Hoàn tiền trả phòng sớm dùng idempotency key riêng theo booking.

## 6. Source of Truth

- `docs/02-business-requirements.md`
- `docs/06-api-specification.md`
- `docs/07-business-workflows.md`
- `docs/08-concurrency-design.md`
- `StayModificationController.java`, `StayModificationServiceImpl.java`
- `Booking.java`, `BookingRepository.java`, `PaymentRefund.java`
- `PaymentServiceImpl.java`, `PaymentWebhookServiceImpl.java`, `CounterPaymentService.java`
- `RefundManagementServiceImpl.java`, `PaymentMonitoringController.java`

### Ngày thực tế và đoạn gia hạn

Quote trả sớm không thay đổi trạng thái. Execute chỉ nhận actualCheckOutDate = ngày hiện tại
của khách sạn; không cho lùi/tới ngày khác. Khi hoàn tất lưu checked_out_at thực tế UTC.
Gia hạn kiểm tồn hạng cho [ngày trả cũ,ngày trả mới), loại booking đang sửa; cả khi đã gán
phòng vẫn phải kiểm tồn hạng và lịch phòng. Không đổi snapshot đêm cũ hoặc tự chuyển phòng.
Nguồn: StayModificationServiceImpl; [tài liệu23](../23-stay-time-and-inventory.md).


## Đổi ngày trước khi nhận phòng theo gói tiêu chuẩn V2

Đây là đổi lịch trước lưu trú, không phải trả sớm. Booking đã chấp thuận V2 áp bảng
0/50%/100% P theo mốc 48/24 giờ và giới hạn F/P của [chính sách hủy/đổi](cancellation-policy.md).
Đổi chuẩn cùng hạng, cùng số đêm, toàn booking/đơn, một lần trong 90 ngày từ ngày nhận gốc.
Khách chấp thuận báo giá; quản lý kiểm tra lại tồn, dịch vụ và tuổi trẻ em rồi áp dụng
trong một giao dịch. Thiếu tiền giữ CONFIRMED với hạn thu đã thỏa thuận. Yêu cầu đang chờ
không đổi ngày, không ghi phí và không kéo dài cutoff. Tăng/giảm số đêm được báo giá trước;
quản lý cần ghi lý do xét ngoại lệ và khách chấp thuận báo giá mới trước khi áp dụng.
Giữ ngày nhận thì giữ giá snapshot các đêm còn lại: thêm đêm tính giá hiện hành và không thêm
phí đổi; bớt đêm áp hủy một phần trên giá đêm đầu bị bỏ, cùng mốc 48/24 giờ và trần P gốc.
Dời ngày nhận thì báo giá lại toàn kỳ, cộng một mức phí đổi theo P gốc; không cộng thêm phí
hủy phần đêm bị bỏ cho cùng lần đổi. Quy tắc 20% trả sớm vẫn chỉ dành cho khách đã CHECKED_IN.
