# [P5-10] Improve payment result and refund recovery UI

## Hiện trạng

Frontend có payment result/status polling và refund-request management UI. Test
hiện chủ yếu bao phủ payment-result page, polling utility và refund page; các tình
huống reload, timeout/network recovery, event đến muộn, duplicate action và dữ
liệu tài khoản nhạy cảm chưa có regression coverage đầy đủ.

## Thay đổi dự kiến

Cải thiện payment/result/refund UI theo terminal-state contract P5-09, bổ sung
recovery states, privacy masking, responsive/accessibility và tests. Frontend
không tự suy luận payment success từ redirect query nếu backend chưa xác nhận.

## Acceptance criteria

- [ ] Result page chỉ hiển thị success/refund thành công từ backend state có thẩm quyền.
- [ ] Polling có timeout/backoff hợp lý, dừng ở terminal/unmount và cho phép manual retry sau network error.
- [ ] Reload hoặc callback lặp không tạo payment/refund command trùng.
- [ ] Refund action yêu cầu confirm, khóa khi submit và map đúng 401/403/409/terminal conflict.
- [ ] Số tài khoản được mask ở list/notification; clipboard/log/client error không lộ dữ liệu ngoài nhu cầu.
- [ ] Payment/refund flow dùng được ở mobile và bằng bàn phím; status thay đổi có accessible announcement.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Blocked bởi state/error/polling contract P5-09 và booking handoff P5-08.

## Test evidence

- Before: `payment-result-page.test.tsx`, `payment-status-polling.test.ts`, `refund-requests-page.test.tsx`.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; thêm reload/timeout/duplicate/privacy cases.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
