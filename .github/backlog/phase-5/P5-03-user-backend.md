# [P5-03] Tighten profile authorization and validation tests

## Hiện trạng

User/Profile đã có controller, service, DTO validation, security integration test
và update-user tests. Cần audit lại ranh giới self-service với quản trị user để
đảm bảo client không thể sửa role/status/identity field ngoài contract, đồng thời
chuẩn hóa xử lý email/phone trùng và user không ACTIVE.

## Thay đổi dự kiến

Rà DTO mapping, service-level authorization và validation; sửa mass-assignment
hoặc enumeration risk nếu có; bổ sung API integration tests trên PostgreSQL cho
ownership, uniqueness và concurrent updates.

## Acceptance criteria

- [ ] User thường chỉ đọc/sửa profile của chính mình; truy cập ID người khác trả contract không làm lộ dữ liệu.
- [ ] Field role, status, password metadata và audit field không thể thay đổi qua profile payload.
- [ ] Email/phone được normalize trước uniqueness check và lỗi trùng có code ổn định.
- [ ] Hai update cạnh tranh không làm mất dữ liệu im lặng; strategy optimistic/pessimistic conflict được test và ghi rõ.
- [ ] Admin/user-management authorization được test ở cả controller và service boundary liên quan.
- [ ] OpenAPI/API docs phản ánh validation/error response cuối cùng.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

P5-04 cần field constraints, error codes và quyền quản trị từ issue này. Không đổi
response shape mà không cập nhật frontend types/tests.

## Test evidence

- Before: `UserControllerTest`, `UserSecurityIntegrationTest`, `UpdateUserRequestTest`, `UserServiceImplTest`.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify`; đính kèm API integration cases mới và migration evidence nếu thêm version field/index.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
