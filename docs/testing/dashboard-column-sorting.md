# Kiểm thử sắp xếp cột dashboard

Phạm vi: 8 bảng dưới đây. Dùng Manager cho các bảng quản lý, Housekeeper cho bảng buồng phòng; tài khoản khác cần có quyền truy cập trang tương ứng. Dùng dữ liệu có nhiều giá trị khác nhau, ít nhất hai trang cho kiểm tra phân trang.

## Cách hoạt động

- Tiêu đề cột dữ liệu có hai mũi tên. Click lần 1: tăng dần; lần 2: giảm dần; lần 3: bỏ sắp xếp, trở lại thứ tự mặc định.
- Mỗi bảng sắp một cột tại một thời điểm. Click cột mới sẽ bỏ chọn cột cũ và trở về trang 1.
- Chuyển trang giữ cột/chiều sắp xếp. Đổi chiều hoặc bỏ sắp xếp trở về trang 1. Bộ lọc hiện tại được giữ lại.
- Sắp xếp áp dụng trên toàn bộ danh sách đã lọc. Dữ liệu có giá trị bằng nhau giữ thứ tự ổn định theo ID; dữ liệu trống (`NULL`) ở cuối cho cả hai chiều.
- Ảnh và cột chỉ chứa Thao tác không có mũi tên. Cột gộp “Trạng thái / thao tác” của buồng phòng sắp theo trạng thái. Các nút thêm/sửa/xóa/xử lý nghiệp vụ giữ cách hoạt động hiện tại.
- Tải lại toàn trang đưa bảng về trạng thái mặc định. Hai tab Thanh toán/Webhook giữ lựa chọn sắp xếp riêng khi chuyển tab trong cùng trang; bộ lọc vẫn được reset khi đổi tab như trước.

## Các cột cần thử

| Trang | Cột sắp xếp |
| --- | --- |
| `/management/room-types` | Mã, Tên loại phòng, Giá / đêm, Số khách, Trạng thái |
| `/management/rooms` | Số phòng, Loại phòng, Tầng, Trạng thái, Ghi chú |
| `/housekeeping` | Phòng, Hạng phòng, Tầng, Ghi chú vận hành, Trạng thái / thao tác |
| `/maintenance` | Phòng, Lý do, Trạng thái, Ngày tạo |
| `/front-desk/departures/today` | Mã đặt phòng, Khách chính, Hạng phòng, Lưu trú, Số khách, Trạng thái, Tổng tiền, Đã thanh toán, Còn phải thu |
| `/payment-monitoring/payments` | Booking, Mã thanh toán, Số tiền, Trạng thái, Khởi tạo lúc, Thanh toán lúc |
| `/payment-monitoring/webhook-events` | Event ID, Ngân hàng, Mã thanh toán, Số tiền, Trạng thái, Số lần thử, Tiếp nhận lúc |
| `/payment-monitoring/refunds` | Mã đặt phòng, Mã thanh toán, Số tiền, Tài khoản nhận hoàn, Lý do, Yêu cầu lúc, Trạng thái |

## Checklist chạy thủ công

Với từng bảng, chạy lần lượt:

1. Mở trang, kiểm tra bố cục/màu sắc/nút như trước và mũi tên nằm cạnh tên cột.
2. Click từng cột 3 lần, kiểm tra chiều tăng → giảm → mặc định. Chỉ cột đang chọn có mũi tên được tô màu.
3. Sắp tăng, chuyển trang 2. Giá trị đầu trang 2 phải không nhỏ hơn giá trị cuối trang 1; thử lại chiều giảm với quan hệ ngược lại. Dòng có giá trị trống nằm cuối danh sách.
4. Khi ở trang 2, đổi chiều hoặc chọn cột khác. Bảng quay về trang 1, tổng số bản ghi không đổi, cột cũ hết được chọn.
5. Dùng Tab đến tiêu đề cột, nhấn Enter để sắp xếp. Không kích hoạt nút thao tác trong dòng.
6. Nếu danh sách rỗng hoặc chỉ có một dòng: không lỗi, không xuất hiện dòng giả. Bảng buồng phòng giữ thông báo rỗng hiện tại và ẩn bảng khi không còn phòng cần dọn; các bảng còn lại vẫn cho click tiêu đề khi rỗng.
7. Mô phỏng mất mạng rồi click sắp xếp: thấy lỗi tải và nút Thử lại hiện có. Kết nối lại, nhấn Thử lại: đúng cột/chiều đang chọn.

Kiểm tra riêng theo dữ liệu:

| Tình huống | Kết quả mong đợi |
| --- | --- |
| Số phòng `2`, `10`, `100` | Tăng dần: `2` → `10` → `100` |
| Giá/số tiền `100.000`, `900.000`, `2.450.000` | Tăng/giảm theo số tiền, không theo chuỗi đã định dạng |
| Tên có dấu và hoa/thường | Sắp theo chữ đã bỏ dấu và chuyển thường; `Alpha` → `Đào` → `Zeta` |
| Ngày `31/10`, `02/11`, `15/11` cùng năm | Tăng dần theo thời gian thực, không theo ngày hiển thị |
| Thanh toán lúc chưa có (`—`) | Luôn ở cuối danh sách cả tăng và giảm |
| Thanh toán/Webhook có tìm kiếm + lọc trạng thái | Click tiêu đề và chuyển trang vẫn chỉ trả dữ liệu khớp bộ lọc |
| Đổi tab Thanh toán → Webhook → Thanh toán | Hai tab có cột/chiều sắp xếp riêng; không gửi tên cột của tab này sang API tab kia |
| Lưu trú | Sắp theo **ngày nhận phòng**, dòng đầu trong ô |
| Số khách | Sắp theo **tổng người lớn + trẻ em** |
| Trạng thái khách đang lưu trú | Tăng dần: Đang lưu trú → Quá hạn trả phòng; dùng giờ/policy của khách sạn |
| Tài khoản nhận hoàn | Sắp theo ngân hàng, rồi số tài khoản, rồi tên chủ tài khoản; số 0 đầu tài khoản vẫn giữ nguyên |
| Bảng hoàn tiền chưa có yêu cầu | Vẫn hiện thông báo rỗng và mũi tên; click không tạo/xác nhận hoàn tiền |
| Sắp trạng thái bảng hoàn tiền | Tất cả đang chờ hoàn, thứ tự ổn định theo ID |
| Trạng thái buồng phòng | Tăng dần: Chờ dọn (`DIRTY`) → Đang dọn (`CLEANING`); chiều giảm ngược lại |
| Danh sách buồng phòng có sắp xếp | Chỉ có `DIRTY`/`CLEANING`; không thêm phòng sẵn sàng, có khách hoặc bảo trì |
| Bắt đầu/hoàn tất dọn khi đang sắp xếp | Cập nhật đúng phòng; giữ cột/chiều sắp xếp sau tải lại. Phòng hoàn tất biến mất, tổng giảm; trang cuối rỗng tự về trang còn dữ liệu |
| Menu tài khoản chỉ có role Housekeeper | Sidebar có “Phòng cần dọn”, không có “Tạo đặt phòng” |
| Menu Receptionist/Manager/Admin, kể cả khi có thêm role Housekeeper | Vẫn có “Tạo đặt phòng” theo quyền vai trò tương ứng |

## Đối chiếu kỹ thuật

Mở DevTools → Network: click tăng gửi `sort=<key>,asc`, giảm gửi `sort=<key>,desc`; bỏ chọn không gửi `sort`. Đổi chiều/cột gửi `page=0`. Khóa API tương ứng được liệt kê trong [đặc tả API](../06-api-specification.md#sắp-xếp-các-bảng-dashboard).

Các kiểm thử tự động bổ sung:

- `client/src/features/inventory/dashboard-sorting.test.tsx`: click tiêu đề thật của cả 8 bảng, hai mũi tên, chiều sắp xếp, phân trang, reset trang, giữ bộ lọc, tách trạng thái hai tab và không gọi API ghi.
- `DashboardLayout` role menu: Housekeeper không có mục tạo đặt phòng; các vai trò được phép và tài khoản nhiều vai trò vẫn thấy mục này.
- `DashboardTableQueryTest`: từ chối tên cột/hướng không hợp lệ và chuỗi chèn SQL.
- `DashboardSortingIntegrationTest`: PostgreSQL Testcontainers riêng, cả 48 cột theo hai chiều; số phòng, dấu tiếng Việt, giá, JSON webhook không hợp lệ, giá trị trống, phân trang, lọc, trạng thái quá hạn và request HTTP cho cả 8 endpoint. Buồng phòng còn kiểm tra quyền truy cập, lọc trạng thái và cập nhật danh sách sau khi bắt đầu/hoàn tất dọn.

Checklist thủ công ở trên cần được chạy trên dữ liệu FE của bạn; kết quả kiểm thử tự động không thay thế xác nhận giao diện và dữ liệu thực tế.
