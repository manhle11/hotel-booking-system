# Quản lý tài khoản dành cho ADMIN

ADMIN chỉ quản lý tài khoản và phân quyền. Sau khi đăng nhập, ADMIN được chuyển
đến `/admin/accounts`; thanh điều hướng có **Quản lý tài khoản** và
**Quản lý vai trò** (`/admin/roles`).

## Chức năng

- Xem danh sách tài khoản có phân trang; tìm theo họ tên, email hoặc số điện thoại.
- Lọc theo trạng thái và vai trò, bao gồm khách hàng không có role nhân viên.
- Tạo tài khoản với mật khẩu ban đầu và một vai trò duy nhất.
- Sửa họ tên và số điện thoại; email được giữ nguyên.
- Chặn tài khoản (`LOCKED`) hoặc kích hoạt lại (`ACTIVE`).
- Xem các vai trò hệ thống và gán/thu hồi vai trò của từng tài khoản.

Vai trò hệ thống hiện là `ADMIN`, `MANAGER`, `RECEPTIONIST`, `HOUSEKEEPER`.
Khách hàng có danh sách role rỗng (`[]`). Các mã role tương ứng với quyền cố định
trong ứng dụng; màn hình vai trò dùng để xem phạm vi và phân quyền tài khoản.
Không tạo hoặc xóa mã role tùy ý.

Mỗi tài khoản chọn đúng một vai trò trên giao diện: Khách hàng, ADMIN, MANAGER,
RECEPTIONIST hoặc HOUSEKEEPER. Khách hàng được lưu bằng danh sách role nhân viên
rỗng để giữ tương thích với luồng đặt phòng hiện tại. Tài khoản nhân viên chỉ có
một phần tử trong danh sách role. API từ chối danh sách có nhiều hơn một role;
cơ sở dữ liệu cũng có ràng buộc `UNIQUE (user_id)` trên `user_roles`.

Migration chuyển tài khoản cũ có nhiều role về một role, ưu tiên
ADMIN → MANAGER → RECEPTIONIST → HOUSEKEEPER. Refresh token của các tài khoản
được chuyển đổi bị thu hồi; JWT nhiều role cũ bị từ chối và phải đăng nhập lại.
Các chức năng vận hành khách sạn trước đây dùng chung ADMIN/MANAGER nay dành cho
MANAGER; lễ tân và buồng phòng giữ quyền nghiệp vụ tương ứng. Chi tiết webhook
thuộc phạm vi giám sát thanh toán của MANAGER.

## API

Tất cả các endpoint dưới đây yêu cầu access token với role ADMIN, dùng tiền tố
`/api/v1` và response envelope hiện có của hệ thống.

| Method | Đường dẫn | Nội dung |
|---|---|---|
| GET | `/admin/accounts` | `page` (từ 0), `size` (1–100), `search`, `status`, `role` |
| POST | `/admin/accounts` | `fullName`, `email`, `phone` (hoặc null), `password`, `roles` |
| PUT | `/admin/accounts/{id}` | `fullName`, `phone` (hoặc null) |
| PATCH | `/admin/accounts/{id}/status` | `status`: ACTIVE / LOCKED / INACTIVE |
| PUT | `/admin/accounts/{id}/roles` | `roles`: đúng một mã role nhân viên, hoặc `[]` cho Khách hàng |
| GET | `/admin/roles` | Danh sách mã, tên và mô tả các role |

`role=GUEST` dùng để lọc các tài khoản không có role nhân viên.
Response tài khoản không chứa mật khẩu hoặc token.

Chặn, kích hoạt hoặc thay đổi role sẽ thu hồi access token và refresh token hiện
tại; người dùng cần đăng nhập lại. Tài khoản bị chặn không thể đăng nhập hoặc
refresh token. ADMIN không thể tự chặn/tự thay role. Hệ thống giữ ít nhất một
ADMIN đang hoạt động và khóa cập nhật phân quyền để tránh hai thao tác đồng thời
làm mất toàn bộ ADMIN.

Flyway tự áp dụng `V202611032300__restrict_admin_to_account_management.sql` khi
backend khởi động để cập nhật mô tả quyền ADMIN.
Migration tiếp theo `V202611032310__enforce_single_account_role.sql` chuẩn hóa
role cũ và thêm ràng buộc chỉ một role nhân viên cho mỗi tài khoản.

## Kiểm tra thủ công

1. Đăng nhập ADMIN: mở đúng `/admin/accounts`, chỉ thấy hai mục quản lý mới.
2. Tạo một tài khoản khách và một tài khoản nhân viên; kiểm tra tìm kiếm và các bộ lọc.
3. Sửa thông tin, đổi role rồi đăng nhập lại tài khoản nhân viên để kiểm tra quyền mới.
4. Chặn tài khoản đang đăng nhập: request tiếp theo phải mất phiên; đăng nhập và
   refresh token cũ phải thất bại. Mở chặn rồi đăng nhập lại thành công.
5. Kiểm tra nút chặn/phân quyền của chính ADMIN bị vô hiệu hóa; gọi API trực tiếp
   cũng bị từ chối.
6. Kiểm tra form tạo và phân quyền đều chỉ chọn được một vai trò; đổi lựa chọn
   phải thay thế vai trò cũ. Gửi trực tiếp nhiều role qua API phải trả 400.
7. Dùng ADMIN mở URL hoặc gọi API quản lý phòng, đặt phòng, check-in/check-out,
   thanh toán và báo cáo: phải bị từ chối, kể cả tài khoản ADMIN có role cũ bổ sung.
8. Đăng nhập MANAGER, RECEPTIONIST và HOUSEKEEPER: kiểm tra các nghiệp vụ tương ứng
   vẫn dùng được và các endpoint `/admin/**` đều trả 403.
