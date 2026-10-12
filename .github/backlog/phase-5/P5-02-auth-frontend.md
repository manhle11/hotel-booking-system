# [P5-02] Harden auth session, route guards and recovery UX

## Hiện trạng

Frontend đã có login, password recovery, guest/protected/role routes, refresh
single-flight trong một tab và test cho storage/route guards. Access/refresh token
đang lưu trong localStorage hoặc sessionStorage; single-flight là biến in-memory
nên chưa phối hợp nhiều tab. Chưa có regression suite cho logout/refresh failure
giữa nhiều tab, deep-link sau login và các trạng thái hết phiên khi đang submit.

## Thay đổi dự kiến

Audit vòng đời session và route authorization, sửa các trạng thái không nhất quán,
bổ sung đồng bộ logout/expiry giữa tab, recovery UX và test. Nếu P5-01 chọn cookie
HttpOnly hoặc đổi token contract, tích hợp theo contract đã thống nhất.

## Acceptance criteria

- [ ] Refresh đồng thời trong một tab vẫn chỉ phát một request và request gốc chỉ retry một lần.
- [ ] Logout hoặc refresh thất bại ở một tab làm các tab khác về trạng thái signed-out mà không tạo redirect loop.
- [ ] Deep-link hợp lệ được khôi phục sau login; URL ngoài ứng dụng không được dùng làm post-login redirect.
- [ ] Role guard không chỉ ẩn menu mà còn chặn navigation trực tiếp với trang lỗi/redirect rõ ràng.
- [ ] Login, forgot/reset password và session-expired state dùng được bằng bàn phím, có focus/error announcement phù hợp.
- [ ] Unit/integration tests bao phủ remember-me, local/session storage migration và refresh error contract.

## Người phụ trách

Hoàng Thị Ánh Nguyệt — Frontend.

## Backend/frontend dependency

Phụ thuộc error code, rate-limit và refresh-token contract của P5-01. Backend vẫn
là nơi quyết định authorization; route guard chỉ cải thiện UX.

## Test evidence

- Before: `axios.test.ts`, `storage.test.ts`, route guard tests, login/password-recovery tests.
- Required: `cd client && npm run test:run && npm run lint && npm run build`; đính kèm test đa-tab/expiry và ảnh keyboard flow nếu UI đổi.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
