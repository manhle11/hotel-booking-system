# [P5-05] Audit price calculation and concurrent rule updates

## Hiện trạng

Room/Pricing đã có catalog/search, stay-price calculation, price-rule/holiday
management, JPA locks và unit/integration/security tests. Chưa có bằng chứng rõ
ràng cho cập nhật rule cạnh tranh trên PostgreSQL, boundary ngày/giờ khách sạn,
overlap rule và tính bất biến của booking price snapshot khi rule thay đổi.

## Thay đổi dự kiến

Audit query/JPA lock order và price policy; sửa lỗi tính giá hoặc lost update nếu
có; bổ sung Testcontainers concurrency/contract tests. Giữ tiền tệ bằng kiểu số
nguyên/decimal hiện dùng và không tính giá bằng floating point.

## Acceptance criteria

- [ ] Bảng precedence cho base/weekend/holiday/discount/surcharge được ghi và test bằng table-driven cases.
- [ ] Check-in/check-out boundary dùng `Asia/Ho_Chi_Minh` nhất quán, không lệch một ngày do UTC conversion.
- [ ] Hai thao tác tạo/cập nhật rule trùng phạm vi không cùng thành công khi policy cấm overlap.
- [ ] Price snapshot của booking đã tạo không đổi khi rule hoặc room type price được cập nhật sau đó.
- [ ] Lock order giữa room type, room và price rule được ghi rõ; concurrency test không deadlock/lost update.
- [ ] API trả validation/conflict code ổn định để frontend map được.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

P5-06 cần price breakdown, validation và conflict contract. P5-07 dùng price
snapshot/availability invariants khi tạo hoặc gia hạn booking.

## Test evidence

- Before: pricing service/controller/repository tests và room availability tests hiện có.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify` với Docker; đính kèm test precedence, timezone và concurrent rule update.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
