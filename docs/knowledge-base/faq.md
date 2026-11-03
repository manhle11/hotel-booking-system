---
documentId: HANBAY-POLICY-FAQ
title: Policy FAQ
category: FAQ
source: docs/knowledge-base/faq.md
version: 1.3
---

# HANBAY HOTEL — FAQ chính sách nội bộ

> Chỉ các câu trả lời có source trong repository mới được coi là knowledge.

## Đặt phòng

### Q: Booking PENDING được giữ trong bao lâu?

A: Mặc định15 phút, cấu hình được. Đúng expiredAt không còn giữ tồn/cho xác nhận; scheduler ghi EXPIRED sau đó. Sửa pending không tăng hạn.

### Q: Khi nào booking được CONFIRMED?

A: Booking `PENDING` còn hạn được xác nhận khi payment flow ghi nhận đủ số tiền yêu cầu.

### Q: Trẻ em có tính vào capacity không?

A: Có. Room type kiểm tra riêng số người lớn, số trẻ em và tổng số khách. API nhận số lượng,
không tự phân loại từ tuổi/ngày sinh từng khách; không suy diễn ngưỡng tuổi hoặc miễn phí.

### Q: Availability được tính như thế nào?

A: Tính từng đêm [nhận,trả), lấy tồn thấp nhất cả kỳ; trừ active, hold còn hạn và khách ở quá hạn; loại phòng bảo trì/ngừng phục vụ. Không trừ gộp mọi booking giao khoảng. availableRooms khác readyRooms hiện tại.

## Thanh toán và hủy

### Q: Chuyển thiếu tiền thì booking có được xác nhận không?

A: Không. Booking chỉ được xác nhận khi tổng giao dịch hợp lệ đạt payment amount trong lúc
booking/payment còn đủ điều kiện.

### Q: Chuyển thừa tiền được xử lý thế nào?

A: Phần tiền vượt payment amount tạo refund `PENDING`.

### Q: Hủy booking đã thanh toán được hoàn bao nhiêu?

A: Booking `CONFIRMED` hủy còn ít nhất 48 giờ trước check-in được hoàn 100%; còn từ 24 đến
dưới 48 giờ được hoàn 50%; dưới 24 giờ không được hủy. Mốc mặc định tính từ 14:00 theo
`Asia/Ho_Chi_Minh`.

## Check-in, check-out và phòng

### Q: Tôi có thể check-in trước ngày check-in không?

A: Không nhận trước ngày đặt. Cho nhận trong ngày đặt hoặc booking hôm qua còn kỳ ở trước 06:00 hôm nay; đúng 06:00 không còn ngoại lệ. Trước14:00 trong ngày đặt cần lý do lễ tân.

### Q: Ai thực hiện check-in?

A: Endpoint check-in chỉ dành cho `RECEPTIONIST`.

### Q: Khi nào check-out bị từ chối?

A: Check-out bị từ chối nếu booking không `CHECKED_IN`, không có assigned room, phòng không
`OCCUPIED`, hoặc công nợ sau cộng phí và thu tại quầy còn lớn hơn 0. Checkout thường chỉ
dành cho ngày trả dự kiến hoặc muộn hơn; trả sớm dùng nghiệp vụ riêng.

### Q: Phòng chuyển trạng thái gì sau check-out?

A: Booking chuyển `CHECKED_OUT` và phòng chuyển từ `OCCUPIED` sang `DIRTY`.

### Q: Housekeeping đưa phòng về AVAILABLE như thế nào?

A: Housekeeper phải thực hiện đúng `DIRTY -> CLEANING -> AVAILABLE`; không có direct
`DIRTY -> AVAILABLE`.

## Add-on

### Q: Add-on có giới hạn quantity không?

A: Có. Quantity mỗi add-on phải từ 1 đến 20.

### Q: Giá add-on có đổi theo catalog sau khi booking được tạo không?

A: Không. Booking lưu snapshot unit price và total price tại thời điểm chọn.

## Chưa được định nghĩa

### Q: Khách sạn có hồ bơi, thú cưng, buffet sáng hoặc airport transfer không?

A: Thông tin này chưa được định nghĩa trong chính sách HANBAY HOTEL.

### Q: Late check-out hoặc early check-in có mất phí không?

A: Thông tin này chưa được định nghĩa trong chính sách HANBAY HOTEL.
## Early checkout và gia hạn lưu trú

### Q: Trả phòng sớm được hoàn bao nhiêu?

A: Backend lấy 80% giá snapshot đêm chưa dùng sau phạt 20%, rồi bù công nợ và phí phát sinh.
Chỉ phần còn dư mới tạo refund `PENDING`; nếu còn thiếu thì thu đúng chênh lệch tại quầy.
Rule này không áp dụng cho cancellation.

### Q: Gia hạn có phải đổi phòng không?

A: Không. Nếu booking đã có assigned room, hệ thống giữ chính phòng đó hoặc từ chối gia
hạn nếu phòng không khả dụng. Booking chưa assigned room vẫn được kiểm tra ở room-type level
và không được gán phòng trước check-in.

### Q: Booking còn nợ có phải luôn PENDING và không check-in được không?

A: Không. Booking mới chưa thanh toán đủ còn `PENDING` và không được check-in. Booking đã
xác nhận rồi gia hạn có thể còn nợ nhưng vẫn `CONFIRMED`/`CHECKED_IN`. Check-in kiểm tra
CONFIRMED, cửa sổ ngày/giờ hợp lệ, phòng và quyền; không kiểm tra lại công nợ bằng0.

### Q: Tôi có thể đặt chưa trả tiền rồi ở xong thanh toán toàn bộ không?

A: Luồng này chưa được hỗ trợ. Thu ở checkout giải quyết công nợ của booking đã nhận phòng,
như gia hạn/phí phát sinh; không cho booking mới `PENDING` bỏ qua xác nhận.

### Q: Thanh toán phần gia hạn bằng cách nào?

A: Tạo payment cho số dư của booking `CONFIRMED`/`CHECKED_IN` và chuyển khoản, hoặc quyết
toán phần còn thiếu tại checkout. Booking không quay về `PENDING`.

### Q: Có được check-in trước 14:00 cùng ngày không?

A: Có thể nhận sớm trong ngày nếu lễ tân ghi lý do≤500 ký tự, phòng AVAILABLE đúng hạng và không xung đột. Không có công thức tự thu phí nhận sớm. Đúng/từ14:00 không cần lý do sớm.

### Q: Đã19:55 ngày 03/11, sao vẫn đặt nhận03/11 được?

A: Đúng policy:14:00 là bắt đầu nhận, chưa phải đóng đặt. Đặt cùng ngày được trước cutoff 23:59:59
nếu còn tồn.03→05/11 là2 đêm; hạn thanh toán expiredAt riêng, không chờ đến cutoff mới trả tiền.

### Q: Đúng 12:00 có bị coi trả muộn? Qua giờ có tự trả phòng không?

A: Đúng 12:00 chưa muộn; sau 12:00 cần ghi chú xử lý. Không tự trả/nhả tồn/cộng đêm. Trả xong DIRTY.

### Q: Có được thực hiện trả sớm với ngày hôm qua hoặc ngày mai không?

A: Không. Execute chỉ ngày khách sạn hôm nay; quote chưa phải đã checkout.

### Q: Tài liệu và cách thử phần thời gian mới ở đâu?

A: [Quy tắc đầy đủ](../23-stay-time-and-inventory.md) và [16 luồng FE](../testing/stay-time-fe-checklist.md).
