# [P5-06] Improve responsive room discovery and admin UI

## Hiện trạng

Frontend đã có room-type catalog, availability search, detail components, price
summary và price-rule management với unit/component/API tests. Coverage hiện tập
trung vào API và kết quả tìm kiếm; room detail/admin journeys, URL-persisted
filters, responsive layout và price-conflict recovery chưa có regression coverage
đầy đủ.

## Thay đổi dự kiến

Cải thiện catalog/search/detail và màn hình quản trị pricing/room theo contract
hiện có. Bổ sung responsive/accessibility, query-state persistence, price
breakdown và tests; không tạo API giả để hoàn thiện UI.

## Acceptance criteria

- [ ] Search dates/occupancy/filter được phản ánh vào URL và khôi phục đúng khi back/forward hoặc reload.
- [ ] Catalog, results và detail dùng được ở 360px, 768px và desktop mà không che CTA hoặc tràn ngang.
- [ ] Loading, no-availability, validation, stale-price/conflict và retry states có thông điệp/action cụ thể.
- [ ] Giá hiển thị đúng currency/locale và breakdown từ P5-05; frontend không tự tính lại business price.
- [ ] Admin pricing/room controls chỉ hiện theo role và xử lý 403/409 mà không mất dữ liệu form.
- [ ] Component/API tests bao phủ detail/admin critical paths và keyboard labels cho filter/date controls.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Phụ thuộc price breakdown, validation và conflict contract P5-05. Booking CTA
phải giữ query contract mà P5-08 sử dụng.

## Test evidence

- Before: room API/result tests và price-rule management tests hiện có.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; đính kèm screenshots viewport và tests URL/409 recovery.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
