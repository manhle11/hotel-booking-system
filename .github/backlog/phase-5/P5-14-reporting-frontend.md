# [P5-14] Improve dashboard filters, charts and accessibility

## Hiện trạng

Frontend đã có dashboard page, dashboard API và trend chart tests. Cần audit
filter/date-range persistence, large/sparse dataset rendering, metric explanation,
chart accessibility và responsive behavior; hiện chưa có E2E evidence cho manager
dashboard flow.

## Thay đổi dự kiến

Cải thiện dashboard theo metric/date contract P5-13, thêm URL filter state,
accessible chart/table alternative, responsive/empty/error states và tests.

## Acceptance criteria

- [ ] Date range/filter được validate, phản ánh trong URL và giữ khi reload/back-forward.
- [ ] Loading, partial/empty data, invalid range, 403 và network retry có trạng thái riêng, không hiển thị số 0 gây hiểu nhầm.
- [ ] Chart có text/table alternative, legend/tooltip dùng được bằng bàn phím và không chỉ dựa vào màu.
- [ ] Currency, percentage và timezone hiển thị nhất quán với định nghĩa P5-13.
- [ ] Dashboard dùng được ở 360px/768px/desktop và không cắt label/tooltip/action.
- [ ] Tests bao phủ filter contract, sparse data, accessibility roles và manager/non-manager access.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Blocked bởi metric, range validation và response contract P5-13. E2E manager flow
được nối vào P5-18.

## Test evidence

- Before: `dashboard-api.test.ts`, `trend-chart.test.tsx`, `dashboard-page.test.tsx`.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; đính kèm chart/table keyboard evidence và responsive screenshots.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
