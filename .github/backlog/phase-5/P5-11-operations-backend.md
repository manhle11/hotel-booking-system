# [P5-11] Verify atomic check-in/out and room-operation transitions

## Hiện trạng

Operations đã có today arrivals/departures, check-in/out settlement, housekeeping,
maintenance và add-on APIs với unit/integration/security tests. Housekeeping có
concurrency test riêng, nhưng chưa có cross-module race suite chứng minh atomicity
giữa booking, room, settlement, housekeeping và maintenance transitions.

## Thay đổi dự kiến

Audit transaction/lock order và service boundaries cho check-in/out,
housekeeping/maintenance; thêm PostgreSQL race/integration tests; sửa rollback,
authorization hoặc invariant nếu test tái hiện lỗi.

## Acceptance criteria

- [ ] Check-in chỉ gán room AVAILABLE đúng room type và cập nhật booking/room atomically.
- [ ] Check-out, settlement transaction và room-to-dirty transition cùng thành công hoặc rollback cùng nhau.
- [ ] Housekeeping clean vs maintenance open/close race không đưa room về trạng thái không hợp lệ.
- [ ] Hai nhân viên thao tác cùng booking/room nhận kết quả xác định, không lost update hoặc duplicate charge.
- [ ] Lock order giữa booking, room, payment/add-on và maintenance được ghi và có regression test không deadlock.
- [ ] RBAC và audit identity cho receptionist/housekeeping/manager/admin được test ở endpoint quan trọng.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

Phụ thuộc Booking P5-07 và Payment P5-09. P5-12 dùng conflict/error/state contract
từ issue này.

## Test evidence

- Before: check-in/out, frontdesk, housekeeping, maintenance, add-on service/API/security tests hiện có.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify` với Docker; đính kèm cross-module race và rollback evidence.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
