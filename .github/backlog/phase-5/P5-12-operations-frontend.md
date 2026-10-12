# [P5-12] Unify front-desk, housekeeping and maintenance UX

## Hiện trạng

Frontend đã có today arrivals/departures, check-in/out, housekeeping, maintenance
và add-on management screens với API/page tests. Các module dùng pattern loading,
filter, mutation feedback và conflict recovery chưa hoàn toàn đồng nhất; mobile
tables/forms và cross-screen cache invalidation cần được audit.

## Thay đổi dự kiến

Chuẩn hóa operational screens theo state contract P5-11: shared feedback patterns,
responsive tables/cards, mutation conflict recovery, cache invalidation,
accessibility và tests. Không hợp nhất business state ở client.

## Acceptance criteria

- [ ] Check-in/out action khóa khi submit, confirm irreversible action và refresh đúng arrivals/departures/room caches.
- [ ] Housekeeping/maintenance conflict hiển thị server state mới và action tiếp theo thay vì optimistic overwrite.
- [ ] Filter/page state được giữ hợp lý khi quay lại; loading/empty/error/retry dùng shared components nhất quán.
- [ ] Operational tables chuyển thành layout dùng được ở 360px mà vẫn giữ status/action quan trọng.
- [ ] Receptionist/housekeeping/manager role thấy đúng navigation/action; direct 403 có fallback rõ ràng.
- [ ] Tests bao phủ cross-cache update, duplicate submit, 409 conflict, keyboard focus và mobile critical actions.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Blocked bởi transition/conflict contract P5-11; payment settlement state từ P5-10
và role contract P5-02 cũng phải giữ tương thích.

## Test evidence

- Before: front-desk, housekeeping, maintenance và add-on page/API tests hiện có.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; đính kèm mobile screenshots và mutation/cache cases.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
