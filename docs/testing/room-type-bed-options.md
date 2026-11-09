# Kiểm tra lựa chọn loại giường

Backend phải chạy migration `202611071800`. Bộ dữ liệu hiện hành: [6 nhóm, 8 loại, 35 phòng](../data/room-type-catalog.md). Đăng nhập bằng tài khoản MANAGER, vào **Quản lý loại phòng** (`/management/room-types`).

| Trường hợp | Thao tác | Kết quả mong đợi |
| --- | --- | --- |
| Bộ dữ liệu chuẩn | Mở danh sách loại phòng và quản lý phòng sau reset | 8 loại, 6 nhóm và 35 phòng; Triple Mixed là Queen + Single, Family Double Queen là 2 Queen |
| Đặc điểm phòng | Mở Deluxe King | Hướng biển, ban công riêng, tầng cao ở các trường riêng; không nằm trong tiện nghi |
| Dịch vụ mua thêm | Mở danh mục dịch vụ | 5 dịch vụ đón/tiễn, trang trí và bữa ăn; không có Wi-Fi, TV hoặc giường phụ |
| Tạo mới | Bấm **Thêm loại phòng**, nhập mã/tên, chọn **Nhóm phòng**, chọn KING, số lượng 1; bấm **Thêm loại giường**, chọn SINGLE, số lượng 2; lưu | Lưu thành công. Mở lại hiển thị KING × 1 và SINGLE × 2. Không cần gõ tên giường |
| Không chọn giường | Tạo mới, nhập các thông tin bắt buộc nhưng bỏ trống lựa chọn giường, lưu | Báo cần chọn loại giường, không gửi request tạo mới |
| Xóa hết dòng | Tạo mới, bỏ toàn bộ dòng giường rồi lưu | Báo cần ít nhất một loại giường |
| Chọn trùng | Chọn KING ở dòng 1, thêm dòng 2 và mở danh sách | KING bị vô hiệu hóa ở dòng 2; có SINGLE, DOUBLE, QUEEN để chọn |
| Nhiều cấu hình | Chọn lần lượt bốn loại, mỗi loại có số lượng dương | Có tối đa bốn dòng; nút thêm bị vô hiệu hóa khi đã có đủ bốn dòng |
| Giữ sức chứa | Ghi lại tối đa khách/người lớn/trẻ em, thay cấu hình giường trên loại chưa có booking | Ba giá trị sức chứa giữ nguyên cho đến khi người quản lý tự chỉnh |
| Dữ liệu cũ | Mở một loại có `beds: []` | Hiện thông tin giường cũ và thông báo chưa xác minh; không tự chọn King/Twin từ mô tả |
| Sửa thông tin loại cũ | Chỉ sửa mô tả, giữ danh sách giường trống rồi lưu | Lưu thành công, giữ nguyên mô tả giường cũ; request không gửi danh sách giường rỗng |
| Lịch sử booking | Thử thêm/đổi loại hoặc số lượng giường trên Room Type đã có booking | Backend trả 409 `ROOM_TYPE_BEDS_LOCKED`; form vẫn mở và hiện thông báo, dữ liệu cũ không thay đổi |
| Cấu hình giống cũ | Mở loại đã có cấu hình, chỉ sửa mô tả rồi lưu | Gửi lại cấu hình đúng loại/số lượng, không gửi mô tả giường cũ để ghi đè |
| Tải nhóm lỗi | Khi API nhóm phòng lỗi, mở form | Có thông báo và nút thử lại; tạo mới vẫn yêu cầu nhóm phòng |
| Ngừng kinh doanh | Bấm **Ngừng kinh doanh**, xác nhận | Dòng vẫn còn với trạng thái ngừng kinh doanh; giữ phòng vật lý và booking |

Các test form tự động dùng React Query thật và Ant Design thật, chỉ giả lập API. Test backend/migration/reset chạy trên PostgreSQL Testcontainers. Lượt Chrome cuối dùng API thật trên database local sau reset, chỉ gửi GET để kiểm tra danh mục, giường, diện tích, đặc điểm, tiện nghi và tồn phòng.

Kết quả ngày 07/11/2026: 831 test backend đạt, không lỗi và không bỏ qua; 310 test frontend đạt trên 61 file. Đóng gói backend, Spotless, build TypeScript/Vite và Prettier đạt. Chrome desktop 1440px/mobile 390px xác nhận 8 loại, tồn 35 phòng, 5 dịch vụ và phân tách đặc điểm/tiện nghi.
