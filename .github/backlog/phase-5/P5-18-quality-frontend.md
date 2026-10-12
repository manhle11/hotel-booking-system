# [P5-18] Add Playwright critical-flow E2E and accessibility checks

## Hiện trạng

Frontend baseline có 49 test files/231 tests và Playwright trong devDependencies,
nhưng chưa có Playwright config, E2E directory hoặc npm E2E script. Vitest component
tests không chứng minh full-stack critical flow, browser routing, responsive layout
hoặc accessibility ở trình duyệt thật.

## Thay đổi dự kiến

Thiết lập Playwright suite tối thiểu, deterministic test data/fixtures và CI job
cho các critical journeys. Thêm accessibility checks có scope; tách smoke suite
nhanh khỏi full suite để giữ CI ổn định.

## Acceptance criteria

- [ ] Có Playwright config, `test:e2e` và `test:e2e:smoke` scripts, base URL/env validation và artifact policy.
- [ ] Smoke flows bao phủ login/role guard, room search → booking handoff, booking status/payment result và một operations/dashboard path.
- [ ] Tests dùng API/seed fixture của team hiện tại, không phụ thuộc thứ tự, dữ liệu thật hoặc credential nhóm cũ.
- [ ] Desktop và mobile project chạy ít nhất các critical customer flow; không dùng fixed sleep.
- [ ] Accessibility scan/keyboard assertion có allowlist được giải thích, không bỏ qua violation mới im lặng.
- [ ] CI lưu trace/screenshot/video khi fail, retry có giới hạn và local instructions tái lập được.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Có thể dựng harness ngay; full deterministic suite phụ thuộc demo/test data P5-19
và stable contracts từ các epic. Không block unit/component tests hiện có.

## Test evidence

- Before: `playwright` đã cài nhưng không có config/E2E test; Vitest 231 tests pass.
- Required: `cd client && npm run test:run && npm run lint && npm run build && npm run test:e2e:smoke`; đính kèm CI trace/report link.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
