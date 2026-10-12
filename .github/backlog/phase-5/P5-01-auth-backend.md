# [P5-01] Audit refresh-token rotation, RBAC and rate-limit security

## Hiện trạng

Authentication đã có JWT, refresh-token rotation, token hash, row lock
`findByTokenHashForUpdate`, access-token revocation, login/password-reset rate
limit và Spring Security RBAC. Test hiện có bao gồm unit/security tests nhưng
chưa chứng minh bằng PostgreSQL thật rằng hai request đồng thời dùng cùng refresh
token chỉ tạo được một token kế nhiệm. Ma trận endpoint/role và hành vi proxy
header của rate limit cũng chưa được ghi thành một security regression suite duy
nhất.

## Thay đổi dự kiến

Rà threat model cho login, refresh, logout, password reset và protected endpoints;
vá lỗ hổng nếu phát hiện; bổ sung integration/security tests cho rotation race,
token replay, RBAC và rate-limit identity. Không thay lại cơ chế auth nếu audit
không chỉ ra nhu cầu.

## Acceptance criteria

- [ ] Có bảng endpoint × anonymous/user/receptionist/housekeeping/manager/admin và test các quyết định quan trọng.
- [ ] Testcontainers/PostgreSQL chứng minh hai refresh request đồng thời chỉ có đúng một request thành công và không tạo hai token kế nhiệm còn hiệu lực.
- [ ] Token revoked, expired hoặc thuộc user không ACTIVE bị từ chối bằng error contract ổn định.
- [ ] Rate limit không thể bị né bằng `X-Forwarded-For` không tin cậy; response giới hạn có `Retry-After` nhất quán.
- [ ] Log và response không lộ access token, refresh token, password hoặc token hash.
- [ ] API/security docs được cập nhật nếu error code hoặc trust-proxy policy thay đổi.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

P5-02 dùng error code, `Retry-After` và session-expiry contract từ issue này. Nếu
đổi cách vận chuyển refresh token, cần thống nhất migration contract trước khi
merge frontend.

## Test evidence

- Before: `RefreshTokenServiceImplTest`, `AuthenticationRateLimitServiceTest`, `LoginRateLimitFilterTest` và security tests hiện có.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify` với Docker/Testcontainers; đính kèm tên và kết quả concurrency/security tests mới.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
