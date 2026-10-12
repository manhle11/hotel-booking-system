# [P5-08] Improve booking flow, history and status recovery

## Hiện trạng

Frontend đã có create-booking flow, guest information, stay-price query, booking
list/history, status polling và cancellation UI. Test hiện có bao phủ create flow,
API, price selection và polling utilities nhưng chưa bao phủ đầy đủ list/history,
cancel/extend races, reload/deep-link và mobile flow end to end.

## Thay đổi dự kiến

Củng cố booking flow và history/status tracking theo state contract P5-07. Thêm
responsive/accessibility, stale-state recovery, double-submit protection và tests
cho các transition thực tế.

## Acceptance criteria

- [ ] Create flow giữ dữ liệu hợp lệ khi chuyển bước/back và không gửi hai booking do double click/retry.
- [ ] Reload/deep-link ở bước hợp lệ khôi phục state an toàn; state thiếu/hết hạn dẫn về bước phục hồi rõ ràng.
- [ ] History/list hiển thị đúng action theo status; cancel/extend conflict làm mới server state trước khi cho retry.
- [ ] Payment/expiration polling dừng khi terminal, component unmount hoặc tab flow kết thúc.
- [ ] Flow dùng được bằng bàn phím và ở 360px; validation focus vào lỗi đầu tiên, status được announce phù hợp.
- [ ] Tests bao phủ history, cancellation, extension, 401/403/409 và stale-price/availability cases.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Blocked bởi transition/error/idempotency contract P5-07 và price contract P5-05.
Payment handoff phải tương thích P5-10.

## Test evidence

- Before: 10 nhóm booking frontend test; chưa có test riêng cho booking-list page.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; thêm component/integration evidence và E2E hook cho P5-18.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
