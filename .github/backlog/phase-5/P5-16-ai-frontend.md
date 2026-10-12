# [P5-16] Test assistant streaming, citations and recommendation actions

## Hiện trạng

Frontend đã có AI assistant widget và API test, nhưng chưa có component test suite
cho streaming lifecycle, abort/retry, citations, confirmation actions, route
changes hoặc mobile/accessibility. UI có thể gọi recommendation/action flow nhưng
chưa có regression evidence cho no-evidence và unauthorized tool results.

## Thay đổi dự kiến

Củng cố assistant/recommendation UI theo contract P5-15; bổ sung source/citation
presentation, confirmation boundary, streaming recovery, privacy, responsive/
accessibility và component/integration tests.

## Acceptance criteria

- [ ] Streaming message hiển thị incremental state, có stop/abort và không update state sau unmount/route change.
- [ ] Network/model timeout cho phép retry an toàn mà không nhân đôi user message hoặc tool action.
- [ ] Citation/source mở được, phân biệt rõ sourced answer với no-evidence/fallback response.
- [ ] Mutation/tool action luôn có preview/confirm; unauthorized/expired session không bị trình bày như thành công.
- [ ] Widget dùng được bằng bàn phím/screen reader và ở 360px; focus được quản lý khi mở, đóng và có lỗi.
- [ ] Component tests bao phủ stream chunks, malformed event, abort, retry, citations và confirmation cancel/success.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Blocked bởi streaming/citation/tool/error contract P5-15. Route/session behavior phải
tương thích P5-02; booking recommendation handoff tương thích P5-08.

## Test evidence

- Before: `ai-assistant-api.test.ts`; chưa có widget component test.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; đính kèm fake-stream cases và keyboard/mobile evidence.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
