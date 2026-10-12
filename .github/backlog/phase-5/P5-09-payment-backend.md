# [P5-09] Harden webhook idempotency and payment/refund states

## Hiện trạng

Payment đã có create/status, SePay webhook API-key comparison, persisted webhook
events, transaction matching, monitoring và refund states/tests. Cần chứng minh
idempotency dưới concurrent delivery, xử lý duplicate có payload xung đột,
out-of-order event và race giữa webhook, booking expiration/cancellation và refund.

## Thay đổi dự kiến

Audit webhook trust boundary, persistence constraints và payment/refund state
machine; bổ sung PostgreSQL integration/concurrency/security tests; redact dữ liệu
nhạy cảm trong log/monitoring. Chỉ thêm signature/timestamp scheme nếu provider
contract thực sự hỗ trợ.

## Acceptance criteria

- [ ] Cùng provider event/transaction gửi lặp hoặc đồng thời chỉ tạo một financial effect.
- [ ] Duplicate ID có payload xung đột bị đánh dấu/từ chối để điều tra, không silently overwrite event gốc.
- [ ] Event đến muộn/out-of-order không hồi sinh payment/booking terminal hoặc làm số tiền âm/vượt số đã nhận.
- [ ] Webhook vs expiration/cancel/refund race kết thúc ở tổ hợp state hợp lệ theo bảng transition.
- [ ] Authorization secret, account number đầy đủ và raw sensitive payload không xuất hiện trong application log/error response.
- [ ] Monitoring vẫn có correlation ID/trạng thái/lý do đủ để vận hành và test security/RBAC đạt.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

Phụ thuộc Booking invariant P5-07. P5-10 cần terminal states, polling interval,
error codes và refund contract ổn định.

## Test evidence

- Before: 12 nhóm payment test gồm webhook/service/repository/security/monitoring.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify` với Docker; đính kèm concurrent duplicate/out-of-order/race evidence.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
