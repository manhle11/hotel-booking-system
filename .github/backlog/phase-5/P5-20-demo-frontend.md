# [P5-20] Build repeatable demo flow and final UI polish checklist

## Hiện trạng

Frontend đã có các màn hình chính cho guest và staff, nhưng chưa có demo runbook
hai thành viên dựa trên dataset ổn định, preflight checklist, fallback khi external
AI/payment không có credential và danh sách visual/responsive defects phải đóng
trước trình bày.

## Thay đổi dự kiến

Xây demo flow dùng dữ liệu P5-19, polish những lỗi UI thật được phát hiện trong
rehearsal, bổ sung demo-safe fallback/fixtures và evidence. Không tạo mock UI để
trình bày như tích hợp production đã hoạt động.

## Acceptance criteria

- [ ] Runbook chia rõ phần backend/AI của Mạnh và frontend flow của Nguyệt, có thời lượng và expected state sau mỗi bước.
- [ ] Preflight kiểm tra services, seed version, viewport, roles và external integration availability trước demo.
- [ ] Flow tối thiểu: guest room search/booking/payment status, staff operations, manager dashboard và AI recommendation/policy answer.
- [ ] Khi AI/payment external không có sandbox credential, UI nói rõ demo fixture/unavailable state và không giả success từ production.
- [ ] Rehearsal ở desktop/mobile không có broken navigation, text overflow, console error hoặc loading vô hạn trong critical path.
- [ ] Có link E2E/smoke evidence, screenshots/video và issue/PR cho mọi polish change đáng kể.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Blocked bởi stable dataset P5-19 và các critical UI contracts P5-12/P5-14/P5-16.
P5-18 cung cấp smoke automation, nhưng rehearsal thủ công vẫn cần cho presentation.

## Test evidence

- Before: module-level tests/build pass; chưa có repeatable two-member demo runbook/E2E suite.
- Required: `cd client && npm run test:run && npm run lint && npm run build && npm run test:e2e:smoke`; đính kèm rehearsal checklist/video.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
