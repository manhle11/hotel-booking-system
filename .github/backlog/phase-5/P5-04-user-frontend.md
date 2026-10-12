# [P5-04] Complete profile and user-management UI states

## Hiện trạng

Frontend đã có profile page, profile API, error mapper và ba nhóm test liên quan.
Các trạng thái dirty form, conflict/duplicate identity, inactive account và user
management theo role chưa có coverage UI đầy đủ; cần xác nhận phạm vi màn hình
quản trị dựa trên API thực tế thay vì dựng dữ liệu giả.

## Thay đổi dự kiến

Cải thiện edit profile và, khi backend contract hỗ trợ, màn hình quản lý user.
Bổ sung validation đồng nhất với backend, state recovery, responsive/accessibility
và tests. Không đưa role/status controls vào UI nếu P5-03 không cấp quyền/API.

## Acceptance criteria

- [ ] Profile form hydrate đúng session hiện tại, báo dirty state và tránh mất thay đổi khi rời trang.
- [ ] Validation email/phone/name khớp contract; lỗi uniqueness/conflict hiển thị tại field phù hợp.
- [ ] Save thành công cập nhật cache và header/session user mà không buộc reload toàn trang.
- [ ] User-management control chỉ xuất hiện cho role được backend cho phép; direct URL vẫn xử lý 403 đúng.
- [ ] Loading, empty, error, disabled/inactive và retry states có UI rõ ràng ở viewport mobile/desktop.
- [ ] Tests bao phủ success, validation, 401/403/409 và keyboard/focus behavior quan trọng.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Blocked bởi field/error/authorization contract của P5-03. Dùng query keys hiện có
và không suy luận quyền chỉ từ việc menu có hiển thị.

## Test evidence

- Before: `profile-api.test.ts`, `user-page.test.tsx`, `profile-error-mapper.test.ts`.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; đính kèm ảnh 360px và desktop cho UI đổi.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
