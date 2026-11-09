# Kiểm tra các điểm nghiệp vụ phòng bổ sung

Khởi động lại backend sau khi cập nhật code/Flyway đến `202611071800`, chạy frontend như bình thường. Database hiện tại được nâng cấp bổ sung; không cần chạy reset.

| Luồng | Thao tác trên giao diện | Kết quả |
| --- | --- | --- |
| 2 người lớn + 2 trẻ em | `/rooms/search`, chọn kỳ tương lai và số khách 2/2, tìm kiếm | Chỉ Family Double Queen phù hợp; có 4 phòng khi chưa có booking |
| Khai báo tuổi | Bấm **Chọn phòng**, nhập tuổi 0 và 11 cho hai trẻ em | Cho tiếp tục khi đủ thông tin; trẻ 0 tuổi vẫn tính vào giới hạn trẻ em/tổng khách |
| Trẻ từ 12 tuổi | Nhập tuổi 12 vào ô tuổi trẻ em | Không cho tiếp tục; cần chuyển khách này sang số người lớn và điều chỉnh lại số trẻ em |
| Giới hạn tổng | Family chọn 4 người lớn + 2 trẻ em | Báo vượt sức chứa, không giữ phòng |
| Suất ăn cả kỳ | Kỳ 3 đêm, mở Dịch vụ bổ sung của một phòng, Buffet sáng → nhập 2 người × 3 ngày | Tổng số 6 suất; chi phí 1.500.000 VND; không tự nhân thêm 3 đêm lần nữa |
| Tự cập nhật | Sửa số người/ngày trong bộ tính | Số suất và tiền cập nhật ngay, không cần bấm thêm; ô tổng suất chỉ đọc |
| Đơn vị bán | Quản lý → Dịch vụ bổ sung, mở sửa | Có lựa chọn đơn vị suất/lượt/gói/đơn vị; giá là giá một đơn vị |
| Lọc đặc điểm | `/rooms/search`, 1 người lớn, chọn hướng biển + có ban công + tầng cao | Deluxe King, Deluxe Twin, Family Double Queen, Suite King; tổng 4 loại khi còn phòng |
| Điều kiện không có | Chọn không có ban công + không ở tầng cao, bỏ hướng nhìn | Standard Queen, Superior Queen, Superior Twin; không coi dữ liệu chưa xác minh là không có |
| Bảo vệ sức chứa | Tạo booking giữ chỗ còn hạn/đã xác nhận 2 người lớn + 2 trẻ em cho Family, vào Quản lý loại phòng giảm trẻ em về 1 | HTTP 409, hiện thông báo xung đột; mở lại vẫn giới hạn 2 trẻ em |
| Điều chỉnh an toàn | Đợi giữ chỗ hết hạn hoặc hủy booking thử, sau đó chỉnh giới hạn phù hợp | Cho lưu nếu không còn booking đang ở/sắp tới vượt giới hạn; giường của loại đã có lịch sử vẫn khóa |
| Khởi động lại | Ghi số lượng phòng rồi khởi động backend 2 lần | Giữ nguyên danh mục và tồn; không tự thêm/bù phòng hoặc quay lại Ocean Suite |

Các booking tạo khi kiểm tra là dữ liệu mới thực sự. Chỉ dùng liên hệ thử nghiệm của bạn; có thể hủy khi kiểm tra xong. Không thanh toán thật để kiểm tra các luồng này.

Test PostgreSQL bao gồm bootstrap mới, không sửa dữ liệu đã tùy chỉnh/phát sinh giao dịch, rollback reset lỗi, chặn sức chứa và snapshot tuổi/đơn vị dịch vụ. Chi tiết danh mục và cơ chế nâng cấp: [room-type-catalog.md](../data/room-type-catalog.md).

## Kết quả kiểm tra ngày 07/11/2026

- Backend: `./mvnw -o spotless:apply verify` — 831 test đạt, 0 lỗi, 0 bỏ qua; đóng gói/Spotless đạt.
- Frontend: `npm run test:run` — 310 test đạt trên 61 file; `npm run build`, `npm run lint` và Prettier đạt.
- Chrome với API local thật xác nhận ngưỡng tuổi 6/12, Family 2+2 hợp lệ/4+2 không hợp lệ, bộ lọc 4 loại hướng biển và 3 loại không ban công/tầng thấp; tuổi 12 chặn tiếp tục, tuổi 0/11 hợp lệ; 2 người × 3 ngày = 6 suất, 1.500.000 VND. Form gửi đúng tuổi và số suất. POST tạo đơn được chặn/giả lập trong kiểm tra Chrome để không sinh booking local; luồng ghi backend được kiểm tra bằng PostgreSQL Testcontainers.
- Chrome mobile 390px không tràn ngang, không có lỗi JavaScript.
- DB local đã áp dụng Flyway 1700/1800, vẫn 6 nhóm/8 loại/35 phòng. Không reset lần này. Checksum 31 bảng khác giữ nguyên; chỉ đổi metadata đơn vị dịch vụ, capacity trẻ em Family và Flyway history.
- Backup trước nâng cấp: `.local-backups/before-room-business-fixes-20261107-165115/hotelbooking_db.dump`. `migration-report.json` và fingerprint trước/sau nằm cùng thư mục.

Backend kiểm tra chạy tạm ở 8081, không thay tiến trình backend chính. Khởi động lại backend chính để giao diện đang dùng nhận API mới. Mọi thay đổi còn trong working tree của `feat/room-category-bed-configuration`, không commit/push.

## Bổ sung sau review ngày 08/11/2026

| Trường hợp | Kết quả mong đợi |
| --- | --- |
| Xóa trống ô trong bộ tính suất ăn | Xóa số người: bỏ chọn dịch vụ, số suất về 0; xóa ngày khi đã chọn người: báo lỗi và chặn tiếp tục, không dùng giá trị 1 hay số suất cũ |
| API sửa booking đơn đang PENDING: đã có 1 trẻ tuổi 5, đổi thành 0 trẻ với `childAges: []` | Lưu thành công, không lỗi ràng buộc database |
| Client cũ sửa liên hệ, giữ số trẻ/ngày nhận và bỏ trường tuổi | Tuổi đã biết được giữ; nếu đổi ngày nhận/số trẻ nhưng không cung cấp tuổi thì để chưa xác định |
| Gửi lại request của đơn nhiều phòng được tạo trước khi có trường tuổi, dùng cùng key/token | Trả lại đúng đơn cũ, không tạo thêm booking hay giữ thêm phòng; thay payload/token vẫn bị từ chối |
| Khởi động local từ cấu hình source, bỏ cấu hình riêng của demo | Bộ seed nguyên trạng/chưa giao dịch thành đúng 8 loại/35 phòng; `prod` không chạy initializer demo |
| Dịch vụ đã được người quản lý sửa/xóa rồi khởi động lại | Giữ dữ liệu tùy chỉnh, không tự tạo lại danh sách mặc định từ initializer thứ hai |

Các trường hợp ghi backend được kiểm tra trên PostgreSQL container riêng; Chrome dùng API local thật cho đọc dữ liệu và chặn POST thử để giữ DB local nguyên trạng. Kết quả: **838 backend, 311 frontend đạt**, build/lint/Spotless đạt. Chi tiết lỗi và cách dọn code: [báo cáo review](../reviews/room-catalog-review-2026-11-08.md).


## Cập nhật tự tính suất ăn — 08/11/2026

Đã bỏ nút Áp dụng số suất theo yêu cầu mới. Các kết quả kiểm tra nút này ở các lượt cũ chỉ mô tả phiên bản trước.

1. Chọn kỳ 3 đêm, mở Buffet sáng: chưa nhập người thì số suất = 0, không tự thêm tiền.
2. Nhập 2 người: ngày mặc định theo số đêm, tổng thành 6 suất và thêm 1.500.000đ với đơn giá 250.000đ.
3. Đổi thành 2 ngày: tổng lập tức thành 4 suất/1.000.000đ, ô tổng suất chỉ đọc.
4. Xóa số người: bỏ chọn dịch vụ và tiền dịch vụ về 0; không tự thay bằng 1.
5. Khi đã nhập người, xóa ngày hoặc nhập ngày lớn hơn số đêm + 1: báo giới hạn rõ ràng, tổng suất về 0 và chặn giữ phòng. Sửa hợp lệ thì tổng tự tính lại.
6. Thêm/bớt phòng hoặc đổi thứ tự nhóm phòng: số người/ngày và suất đã chọn vẫn gắn đúng phòng, phòng khác không tự có suất ăn.
7. Đặt phòng: API vẫn chỉ nhận mã dịch vụ và tổng số suất; dữ liệu ô người/ngày chỉ dùng trên giao diện, không đổi cách tính giá backend.

Kết quả lượt tự tính: **314 test frontend đạt trên 61 file**, TypeScript/Vite build, ESLint và Prettier đạt. Chrome dùng API local thật xác nhận chuỗi đổi 6 → 4 → 0 → 6 suất, tiền cập nhật ngay, không còn nút Áp dụng, tổng suất chỉ đọc, ngày sai chặn tiếp tục và payload gửi đúng 6 suất. Mobile 390px không tràn ngang, không có lỗi JavaScript. POST kiểm tra được chặn để không tạo booking local. Backend/API tính giá không thay đổi trong lượt này.

## Kiểm tra ba bản sửa ngày 09/11/2026

Khởi động lại backend và frontend để dùng code mới. Không cần migration hoặc reset dữ liệu cho ba bản sửa này.

| Luồng tự thử | Kết quả mong đợi |
| --- | --- |
| Quản lý phòng: chọn một phòng AVAILABLE đã từng được gán cho booking, xác nhận Xóa | Thông báo không thể xóa phòng có lịch sử; API trả 409 `ROOM_HAS_BOOKING_HISTORY`; phòng vẫn còn, đơn cũ vẫn giữ số phòng |
| Quản lý phòng: xóa một phòng chưa từng được booking tham chiếu, loại phòng không còn booking có hiệu lực | Xóa được; phòng khác cùng loại và số phòng trên booking lịch sử không bị thay đổi |
| Mở danh mục/checkout để tạo cache; trong cùng phiên ứng dụng, vào quản lý đổi giá hoặc ngừng bán một Room Type rồi quay lại ngay | Danh mục/checkout tải lại dữ liệu; giá mới hiện đúng, loại ngừng bán biến mất khỏi lựa chọn mới |
| Chi tiết/Sửa loại INACTIVE → chọn Đang kinh doanh → Lưu; quay lại danh mục/checkout | Loại được mở bán xuất hiện lại khi còn phòng phù hợp |
| Chỉ còn booking PENDING đã hết hạn của loại phòng, phòng vật lý chưa từng được gán và đang AVAILABLE; thử đổi loại hoặc xóa | Không bị chặn chỉ vì trạng thái vẫn là PENDING; không cần chờ scheduler. Các điều kiện hợp lệ khác vẫn áp dụng |
| Loại phòng còn pending chưa hết hạn, confirmed hiện tại/sắp tới hoặc khách đang ở quá hạn; thử giảm nguồn phòng | Thao tác tiếp tục bị chặn để bảo vệ nghĩa vụ booking |

Cache chỉ được cập nhật chủ động trong cùng QueryClient của phiên ứng dụng. Tab hoặc thiết bị khác cần tải lại/refetch; backend luôn kiểm tra trạng thái và tồn khi tạo booking. Các ca đúng mốc hết hạn và lệch ngày UTC/giờ khách sạn được kiểm tra tự động bằng clock cố định và PostgreSQL Testcontainers.

Kết quả sau ba bản sửa: **855 test backend, 317 test frontend đạt**; backend clean verify/Spotless, frontend build/lint/format và `git diff --check` đạt. Không có migration mới, không reset/sửa database local, không commit/push. Chi tiết kiểm thử và thông báo teardown JVM được ghi trong [báo cáo trước push](../reviews/room-prepush-check-2026-11-09.md).
