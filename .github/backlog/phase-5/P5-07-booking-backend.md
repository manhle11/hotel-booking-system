# [P5-07] Add PostgreSQL race tests for booking transitions

## Hiện trạng

Booking đã có create/history/cancel/extend/check-in/check-out, expiration scheduler,
state policies và nhiều pessimistic locks. Unit/integration tests hiện có nhưng
chưa tạo một race matrix chạy trên PostgreSQL thật cho create vs create, cancel vs
payment, extend vs competing booking và expiration vs confirmation.

## Thay đổi dự kiến

Rà state machine, transaction boundary và lock order; thêm deterministic
Testcontainers race tests; sửa invariant/locking chỉ khi test tái hiện lỗi. Không
đổi lại toàn bộ aggregate hoặc API nếu không cần.

## Acceptance criteria

- [ ] State-transition table cho `PENDING/CONFIRMED/CHECKED_IN/CHECKED_OUT/CANCELLED/EXPIRED` khớp code và docs.
- [ ] Hai booking cạnh tranh cùng room capacity/date range không gây oversell; kết quả thành công/thất bại xác định được.
- [ ] Cancel vs confirm/payment và expiration vs confirm không tạo state/payment mâu thuẫn.
- [ ] Extend vs create/extend khác giữ availability và price snapshot đúng, không deadlock theo lock order đã ghi.
- [ ] Retry/idempotency behavior của command lặp được xác định và test.
- [ ] Test race dùng latch/barrier thay vì `sleep`, chạy thật với PostgreSQL và fail nếu bị skip.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

P5-08 cần transition/error contract. P5-09 và P5-11 phụ thuộc invariant Booking để
test payment, check-in/out và room state.

## Test evidence

- Before: 20 nhóm booking test, gồm service/API/security/scheduler; baseline full suite 608 tests.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify` với Docker; liệt kê từng race case và số lần stress-repeat hợp lý.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
