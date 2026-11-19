---
documentId: HANBAY-PAYMENT-POLICY
title: Payment Policy
category: PAYMENT
source: docs/knowledge-base/payment-policy.md
version: 1.6
hotel: HANBAY HOTEL
language: vi
---

# HANBAY HOTEL — Chính sách thanh toán

## 1. Mục đích

Mô tả cách payment được tạo, xác nhận và đối soát trong hệ thống demo HANBAY HOTEL.

## 2. Phạm vi

Áp dụng cho payment gắn với booking và webhook chuyển khoản SePay đã triển khai.

## 3. Quy định

### 3.1 Payment request

Payment request phục vụ booking `PENDING` còn hạn hoặc booking `CONFIRMED`/`CHECKED_IN`
còn công nợ. Amount bằng `max(totalAmount - paidAmount, 0)`, currency `VND`, payment mới
ở trạng thái `PENDING`. Booking `NO_SHOW` hoặc `CANCELLED` đã chốt quyết toán tại quầy
cũng có thể tạo QR để thu phần phí còn thiếu; số dư lấy từ giao dịch thực thu trừ các khoản
đã hoàn/đang dành hoàn, không suy ra từ tiền trả gộp ban đầu. Booking cũ đang chờ quản lý
quyết toán chưa được thu phí. Không tạo payment khi công nợ bằng 0.
Payment chờ còn hạn và cùng amount được tái sử dụng; hết hạn tạo mã mới theo số dư hiện tại.

Thời hạn payment theo cấu hình; với booking còn hạn giữ chỗ, hạn payment không vượt hạn
booking. Payment `PENDING` không có nghĩa booking cũng `PENDING`: payment bổ sung có thể
thuộc booking đã xác nhận hoặc đang lưu trú.

### 3.1.1 Thanh toán chênh lệch toàn đơn nhiều phòng

Đơn đã CONFIRMED có thể thanh toán một lần cho toàn bộ khoản còn thiếu của các phòng
CONFIRMED/CHECKED_IN qua nút **Thanh toán toàn bộ** trên trang chi tiết đơn. Số tiền QR
là tổng `max(totalAmount - paidAmount, 0)` từng phòng; không thu lại tiền đã trả và không
tự lấy tiền của phòng khác đang dành hoàn để bù công nợ. Phòng đã đóng còn nợ cần quyết toán riêng.

Một mã QR được phân bổ theo khoản còn thiếu của từng phòng. Chuyển từng phần được ghi nhận
vào từng phần phân bổ, QR lần sau dùng số còn thiếu. Mở lại khi mã còn hạn và số dư không
đổi sẽ dùng lại mã cũ; mã hết hạn hoặc số dư thay đổi tạo mã mới. Hạn QR theo cấu hình
payment, độc lập với hạn giữ chỗ ban đầu của đơn đã xác nhận.

Khi chuyển giữa thanh toán toàn đơn và thanh toán riêng, mã chờ trước đó được đóng; khoản
đã nhận vẫn được giữ trong sổ tiền của phòng. Tiền đến mã đã đóng/hết hạn hoặc vượt công
nợ thực tế được tạo hoàn PENDING; không ghi tăng tiền đã trả hai lần. Đổi/hủy kỳ ở đóng
mã toàn đơn không còn phù hợp. Thanh toán chênh lệch không xác nhận đơn thêm lần nữa.

### 3.2 Xác nhận thanh toán

Webhook phải vượt xác thực API key, là giao dịch tiền vào đúng tài khoản cấu hình và chứa mã
payment hợp lệ. Event/provider transaction được chống xử lý trùng.

Các giao dịch tiền vào hợp lệ của cùng payment được cộng dồn. Với thanh toán ban đầu, khi
số tiền nhận đạt payment amount, payment vẫn `PENDING` và booking vẫn `PENDING` còn hạn
tại lúc xử lý webhook, payment thành `SUCCESS` và booking thành `CONFIRMED`.

Với payment công nợ bổ sung do hệ thống tạo cho booking `CONFIRMED`/`CHECKED_IN`, webhook
ghi nhận tiền và hoàn tất payment khi đủ amount; không đổi booking về `PENDING` và không
thực hiện check-in thay lễ tân.

QR thu phí hủy/no-show chỉ làm giảm công nợ phí; không khôi phục booking hoặc giữ chỗ.
Chuyển từng phần được cộng dồn. Phần vượt số phí còn phải thu được dành hoàn;
tiền vào QR đã đóng/hết hạn đi đối soát/hoàn, không ghi thu lần nữa.

### 3.3 Thiếu tiền

Với booking mới `PENDING`, chuyển thiếu tiền không đủ để xác nhận hoặc check-in; tiền đã
nhận được ghi nhận nhưng payment chưa hoàn thành. Không có luồng cho booking mới bỏ qua
thanh toán ban đầu rồi trả toàn bộ ở checkout.

Với booking đã `CONFIRMED`/`CHECKED_IN`, chuyển thiếu khoản bổ sung chỉ làm giảm công nợ,
không đưa booking quay lại `PENDING`. Công nợ còn lại phải được quyết toán ở checkout.

### 3.4 Thừa tiền và payment đến trễ

Phần tiền vượt payment amount tạo refund `PENDING`. Giao dịch đến khi booking/payment không
còn đủ điều kiện xác nhận cũng tạo refund `PENDING` theo event; hệ thống không dùng giao dịch
đến trễ để khôi phục booking hết hiệu lực.

### 3.5 Giới hạn phương thức

Hệ thống hỗ trợ chuyển khoản theo QR/payment request, ghi nhận tiền mặt thực thu tại
checkout và thu phí hủy/no-show đã quyết toán. Khoản thu tại quầy giải quyết công nợ còn lại, ví dụ tiền gia hạn hoặc phí phát
sinh; không phải lựa chọn bỏ qua thanh toán ban đầu cho booking `PENDING`.

Nhân viên ghi số thực thu sau trả tiền thừa; không nhập lại khoản đã được webhook ghi nhận.
Checkout thường yêu cầu `max(totalAmount + additionalChargeAmount - paidAmount - collectedAmount, 0)`
bằng 0 và không cho thu vượt công nợ. Khoản thu được lưu chứng từ `CASH-…`, giao dịch
`PAYMENT / SUCCESS`, người thu và thời điểm thu trong transaction checkout.
Trả sớm còn bù trừ giá trị đêm chưa dùng theo chính sách trả phòng sớm trước khi thu/hoàn.
Chưa có tích hợp máy POS hoặc tự động thu tiền mặt.

Phí còn thiếu của booking đã đóng được lễ tân/quản lý thu ở màn Xử lý khách: nhập số thực nhận,
ghi căn cứ và xác nhận đã nhận tiền. Không thu vượt công nợ. Gửi lại cùng xác nhận trả cùng
kết quả, không tạo phiếu thu thứ hai. Thu tiền không thay đổi trạng thái CANCELLED/NO_SHOW.

### 3.6 Refund destination

Cancellation và early checkout có refund sử dụng `BANK_TRANSFER`. Thông tin tối thiểu gồm
tên ngân hàng, tên chủ tài khoản và số tài khoản. Hệ thống không thu thập OTP, mật khẩu ngân
hàng, CVV hoặc PIN. Refund mới ở `PENDING`; trạng thái này không có nghĩa provider đã hoàn
tiền thành công. Role `MANAGER` xác nhận khoản hoàn đang `PENDING` bằng mã tham chiếu
ngân hàng sau khi đối soát; hệ thống ghi nhận refund và giao dịch hoàn `SUCCESS`.
Luồng này chưa tự chuyển tiền ra ngân hàng hoặc nhận callback hoàn tiền của provider.

### 3.7 Quyết toán no-show

Phí chuẩn bằng giá phòng đêm đầu đã chốt, quản lý có thể miễn/giảm. Tính tiền
phải hoàn từ giao dịch thực thu và phân bổ từng booking, trừ tiền đã hoàn/đang
dành cho hoàn khác. Không cộng phí lên toàn bộ tổng kỳ ở chưa sử dụng. Có thể
đóng booking và tạo yêu cầu hoàn trước khi có tài khoản ngân hàng; khách hoặc
quản lý bổ sung sau xác minh. Hoàn no-show phải có tài khoản nhận trước khi
quản lý xác nhận đã chuyển tiền. QR phí mới thu đúng số còn thiếu; chuyển vào QR cũ đã đóng
được đối soát/hoàn. Tiền đến sau khi đóng không khôi phục booking.

## 4. Quy trình

1. Tạo payment request cho booking `PENDING` còn hạn hoặc `CONFIRMED`/`CHECKED_IN` còn nợ.
2. Guest chuyển khoản với đúng payment code.
3. Webhook được xác thực và deduplicate.
4. Backend cộng dồn giao dịch hợp lệ.
5. Đủ tiền cho booking ban đầu còn hạn: payment `SUCCESS`, booking `CONFIRMED`.
6. Thừa tiền hoặc đến trễ: tạo refund `PENDING` tương ứng.
7. Khoản bổ sung cho booking đã xác nhận/đang ở được ghi nhận mà không đổi trạng thái lưu trú.
8. Công nợ còn lại và phí phát sinh có thể quyết toán tại checkout theo điều kiện thu tại quầy.

## 5. Các trường hợp không hợp lệ

- Booking `PENDING` đã hết hạn, booking không thuộc trạng thái được thanh toán hoặc không còn nợ.
- Người gọi không phải chủ booking và không có booking access token hợp lệ.
- Webhook sai API key, giao dịch tiền ra, sai tài khoản hoặc không có payment code.
- Payment code không tồn tại.
- Số tiền nhận chưa đủ để xác nhận booking.

## 6. Ví dụ

Booking tổng 1.000.000 VND nhận 600.000 VND thì chưa được xác nhận. Nếu sau đó nhận thêm
400.000 VND trong thời hạn, tổng tiền đủ và booking có thể thành `CONFIRMED`.

Booking đã `CHECKED_IN`, tổng mới sau gia hạn 2.500.000 VND, đã trả 2.000.000 VND:
có thể tạo payment 500.000 VND hoặc thu đúng khoản còn thiếu khi checkout. Booking không
trở về `PENDING` chỉ vì còn 500.000 VND công nợ.

## 7. Source of Truth

- `docs/04-system-architecture.md`
- `docs/06-api-specification.md`
- `docs/07-business-workflows.md`
- `PaymentServiceImpl.java`, `PaymentWebhookServiceImpl.java`
- `Payment.java`, `PaymentStatus.java`, `PaymentProperties.java`
- `CounterPaymentService.java`, `CheckOutServiceImpl.java`, `StayModificationServiceImpl.java`
- `RefundManagementServiceImpl.java`, `PaymentMonitoringController.java`

### Hạn giữ chỗ không phải giờ check-in

14:00 là bắt đầu nhận, cutoff 23:59:59 là hạn nhận đặt cùng ngày, expiredAt là hạn thanh toán
riêng của hold. Mặc định hold15 phút theo PAYMENT_EXPIRATION_MINUTES; payment không vượt hạn
booking. Đúng expiredAt không xác nhận pending dù scheduler chưa ghi EXPIRED. Sửa booking hoặc
lấy lại QR không gia hạn hold. Tiền đến muộn đi đối soát/hoàn theo policy, không hồi sinh booking.
Nguồn: BookingFactory, Booking.confirm, PaymentServiceImpl; [tài liệu23](../23-stay-time-and-inventory.md).
