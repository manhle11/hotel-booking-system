# Review thay đổi nghiệp vụ phòng — 08/11/2026

Nhánh: `feat/room-category-bed-configuration`. Phạm vi là toàn bộ thay đổi đang có trong working tree, gồm file đã theo dõi và file mới. Không commit/push. Lần review này không reset hay sửa dữ liệu database local.

## Kết luận

Mô hình hiện tại phù hợp khoảng **95/100 điểm** với nghiệp vụ phòng đã thống nhất cho HANBAY. Đây là đánh giá nội bộ theo bảng tiêu chí dưới đây; không phải tỷ lệ test coverage hay chứng nhận tiêu chuẩn khách sạn. Các thông số phòng vẫn là dữ liệu demo cần đối chiếu thực tế trước khi dùng để bán phòng.

Đã tìm và sửa **5 lỗi**, trong đó các lỗi sửa booking/đơn cũ có test PostgreSQL tái hiện trước khi sửa. Bộ test trước đó chưa bao phủ các trường hợp này. Chi tiết kết quả chạy lại được ghi ở cuối tài liệu.

## Toàn bộ thay đổi đã làm gì?

| Nhóm | Thay đổi và ý nghĩa |
| --- | --- |
| Kiến trúc dữ liệu | Tái sử dụng `room_types`, `rooms`, `amenities`, quan hệ tiện nghi và ảnh. Thêm `room_categories` và `room_type_beds`; mỗi phòng vật lý thuộc một Room Type. |
| Giường | SINGLE/DOUBLE/QUEEN/KING + số lượng nguyên dương, khóa duy nhất theo Room Type/loại giường, API sắp xếp thống nhất. Không tạo giường phụ, không suy sức chứa từ giường. |
| Lịch sử | Khóa cấu hình giường nếu có bất kỳ lịch sử booking, gồm đã hủy/hết hạn/hoàn tất. Thay cấu hình bằng Room Type mới; ngừng kinh doanh giữ phòng và booking. |
| Sức chứa | Dùng lại giới hạn người lớn/trẻ em/tổng khách. Chặn giảm giới hạn khi ảnh hưởng khách đang ở, kể cả quá hạn trả, booking xác nhận sắp tới hoặc giữ chỗ còn hiệu lực. Dùng cùng khóa Room Type với tạo booking. |
| Trẻ em | Tuổi tại ngày nhận phòng: dưới 6, từ 6 đến dưới 12, từ 12 tính người lớn. Lưu tuổi được khai báo; khách dưới 12 vẫn chiếm suất trẻ em và tổng khách, dùng giường có sẵn. Family 4 người lớn/2 trẻ em nhưng tổng tối đa 4. |
| Dịch vụ | Đơn vị UNIT/SERVING/TRIP/PACKAGE; số lượng là tổng cả kỳ. Snapshot đơn vị/giá/số lượng/thành tiền; bộ tính người × ngày chỉ áp dụng khi người dùng bấm nút. Không tự miễn phí hay phụ thu theo tuổi. |
| Đặc điểm và tìm kiếm | Hướng nhìn, ban công, tầng cao lưu riêng, tách khỏi tiện nghi. Lọc trước phân trang; `false` được giữ nguyên, `NULL` không được coi là `false`. |
| Giao diện | Chọn nhóm/loại giường/số lượng, hiển thị giường và đặc điểm, nhập tuổi trẻ em, chọn đơn vị dịch vụ, bộ tính suất ăn, bộ lọc phòng. |
| Danh mục và bootstrap | Nguồn seed chuẩn dùng chung: 6 nhóm, 8 loại, 9 dòng giường, 35 phòng, 5 dịch vụ. Chỉ bootstrap local trên bộ demo nguyên trạng/chưa phát sinh nghiệp vụ; dữ liệu tùy chỉnh được giữ lại. Reset thủ công có transaction và danh sách bảng rõ ràng. |
| Kiểm tra | Test model/service/HTTP/quyền truy cập, PostgreSQL migration/reset, khóa đồng thời, tương thích dữ liệu cũ, UI Ant Design/React Query và kiểm tra dữ liệu local chỉ đọc. |

Không đổi thuật toán tính tồn phòng, engine giá phòng, thanh toán, hoàn tiền hoặc check-in. Booking được mở rộng để lưu tuổi và đơn vị dịch vụ, đồng thời sửa các lỗi tương thích phát sinh từ phần mở rộng này.

## Lỗi phát hiện và đã sửa

| Mức độ | Lỗi trước review | Cách sửa và bằng chứng |
| --- | --- | --- |
| Cao | Sửa booking có `child_ages=[5]` từ 1 trẻ em về 0 khiến `child_count` được flush trước danh sách tuổi, vi phạm `ck_booking_child_ages_array`. | Cập nhật số khách và tuổi cùng nhau trước flush. Test `updatesChildCountAndAgesTogetherBeforeFlushingPendingBooking` đã tái hiện lỗi DB rồi chạy đạt sau sửa. |
| Vừa | Client cũ không gửi `childAges` khi sửa contact có thể xóa tuổi đã biết dù số trẻ em/ngày đến không đổi. | Giữ snapshot nếu số trẻ và ngày nhận không đổi. Nếu đổi ngày đến/số trẻ mà không cung cấp tuổi, để chưa xác định, không đoán. Có test giữ tuổi và không suy tuổi cho ngày nhận mới. |
| Cao | Bật bootstrap demo chỉ được khai báo trong `application-local.yml` đang bị Git ignore; checkout source ở môi trường khác có thể không tạo đúng 8 loại/35 phòng. | Đưa cấu hình vào `application.yaml` được theo dõi trong Git. Test khởi tạo với chính file cấu hình này, kiểm tra local bật, có thể tắt và prod không chạy demo initializer. |
| Vừa | Xóa trống ô số người/ngày của bộ tính bữa ăn tự thay thành 1, vẫn có thể áp dụng số suất ngoài ý muốn. | Giữ trạng thái trống và vô hiệu hóa nút Áp dụng; test UI tái hiện trước sửa và đạt sau sửa. |
| Cao | Thêm `childAges` làm đổi JSON dùng tính chữ ký idempotency: gửi lại đơn đã tạo bởi phiên bản cũ có thể trả 409 dù request cũ không thay đổi. | Chấp nhận chữ ký cũ chỉ khi mọi phòng đều không gửi tuổi; chỉ bỏ khóa `childAges` khi so sánh tương thích. Vẫn kiểm tra token, toàn bộ trường khác và từ chối payload có tuổi thay đổi. Test không tạo thêm booking khi gửi lại đơn cũ. |

Các vị trí sửa chính:

- `Booking.updatePendingDetails`, `BookingServiceImpl`: đồng bộ tuổi/số trẻ, giữ tuổi cũ khi phù hợp.
- `BookingOrderService.matchesRequestHash`: tương thích chữ ký trước khi có tuổi trẻ em.
- `application.yaml`, `RoomCatalogInitializerConfigurationTest`: cấu hình bootstrap nằm trong source.
- `meal-quantity-calculator.tsx`: không tự thay giá trị trống.

## Dư thừa đã dọn và phần cần giữ

- Xóa `AddOnDataInitializer` và test chỉ phục vụ initializer này. Danh sách 5 dịch vụ đã có trong seed chuẩn; không cần một nguồn nữa tự tạo lại dịch vụ khi khởi động, kể cả ngoài local.
- Xóa truy vấn `findAllByCodeIn` chỉ còn được initializer đã xóa sử dụng.
- Bỏ dependency `Clock` không được dùng trong tìm phòng và nhánh gọi service trùng lặp ở controller.
- Tách việc đọc chính sách khỏi đồng hồ chạy mỗi 30 giây. Các ghi chú/ô tuổi không tạo timer riêng cho mỗi phòng; nhãn tuổi đọc cùng cấu hình backend.
- Dọn các tên class ghi đầy đủ lặp lại thành import thông thường.
- Các initializer phòng/tiện nghi cũ đã được bỏ trong đợt triển khai trước; review xác nhận không quay lại cơ chế tự sinh 5 phòng cho mỗi loại.

Giữ các phần có tác dụng tương thích: `bed_type` cũ, trạng thái UNCONFIGURED, constructor/overload đang có nơi sử dụng, API booking đơn trước đây và các migration đã áp dụng. `CreateBookingPage` cũ hiện không phải route public chính nhưng vẫn được export/test; không xóa dây chuyền luồng có trước chỉ để giảm số file. Route public hiện dùng `MultiRoomBookingPage`.

Đặc biệt, không xóa/sửa checksum migration `202611071000`–`202611071800`, kể cả migration lịch sử có bộ 14 loại cũ. Bộ danh mục hiện hành có 8 loại. Các lần nâng cấp tiếp theo phải dùng migration mới nếu cần đổi database.

## Cách chấm mức độ phù hợp

| Tiêu chí | Điểm |
| --- | ---: |
| Phân cấp Category/Type/Room, tái sử dụng dữ liệu và tồn phòng vật lý | 25/25 |
| Giường có cấu trúc, không trùng, bảo toàn lịch sử và ngừng bán an toàn | 20/20 |
| Sức chứa, trẻ em, phân biệt tiện nghi/dịch vụ và số lượng cả kỳ | 20/20 |
| Migration, legacy, bootstrap, API và kiểm thử tương thích | 20/20 |
| Mức sẵn sàng của danh mục để vận hành khách sạn thực tế | 10/15 |
| **Tổng đánh giá** | **95/100** |

5 điểm chưa chấm đủ thuộc dữ liệu vận hành: giá/diện tích/sức chứa/ảnh hiện là mẫu, chưa được xác nhận trực tiếp với từng phòng của khách sạn. Cần kiểm tra thực tế các phòng trong cùng Room Type có đúng cấu hình giường/đặc điểm đã công bố. Không thể suy xác nhận này từ test code.

Các giới hạn được giữ theo phạm vi hiện tại:

- Chưa có trường sức chứa tiêu chuẩn riêng; giá vẫn theo phòng/đêm và các quy tắc giá đã có. Không thêm chính sách mới từ số giường.
- Room Type cũ chưa xác minh giường vẫn đọc được; muốn đổi giường trên loại đã có booking phải tạo loại mới. Cần kế hoạch chuyển phòng khi không còn nghĩa vụ booking của loại cũ.
- Bootstrap tự động chỉ dành cho local. Production giữ dữ liệu đã có và cần cấu hình danh mục thực tế; không tự nạp 35 phòng demo.
- Đơn vị bán vẫn là tổng số suất/lượt/gói của kỳ, chưa có lịch thực hiện từng bữa/chuyến hoặc bảng giá theo tuổi. Không tự phát sinh các nghiệp vụ đó.
- Mở rộng loại giường cần cập nhật enum/options trong code. Cột VARCHAR giúp mở rộng schema thuận tiện; đây chưa phải danh mục loại giường tùy chỉnh động.

Mô hình Room Type theo đặc điểm + giường, giới hạn tổng/người lớn/trẻ em riêng và đặc điểm để tìm phòng có thể đối chiếu với [Oracle OPERA Cloud — Configuring Room Types](https://docs.oracle.com/en/industries/hospitality/opera-cloud/25.5/ocsuh/t_rooms_managing_room_types.htm). Việc tính dịch vụ cần có đơn vị/quy tắc rõ ràng được tham khảo từ [Oracle — Package Posting Attributes](https://docs.oracle.com/en/industries/hospitality/opera-cloud/22.2/ocsuh/t_configuring_package_code_post_attributes.htm). Đây là nguồn tham khảo thiết kế; các chính sách HANBAY như ngưỡng tuổi 6/12 và ngừng bán khi vẫn có booking dùng quy tắc đã thống nhất trong dự án.

## Dữ liệu local đã kiểm tra chỉ đọc

- 6 Room Category, 8 Room Type, 9 dòng cấu hình giường, 35 phòng vật lý.
- 0 phòng mất quan hệ Room Type, 0 cấu hình trùng `(room_type_id, bed_type)`.
- Family: 4 người lớn / 2 trẻ em / tổng tối đa 4.
- Flyway hiện tại `202611071800`, thành công. Không cần migration mới cho các bản sửa runtime trong review này.

## Kết quả kiểm tra

| Kiểm tra ngày 08/11/2026 | Kết quả |
| --- | --- |
| `./mvnw -o spotless:apply verify` | **838 test backend đạt**, 0 lỗi, 0 bỏ qua; đóng gói và Spotless đạt |
| `npm run test:run` | **311 test frontend đạt**, 61 file |
| `npm run build` | TypeScript/Vite đạt; còn cảnh báo kích thước bundle, không chặn build |
| `npm run lint` | Đạt |
| Prettier trên các file frontend thay đổi, `git diff --check` | Đạt |
| Chrome desktop/mobile với API local thật | Family 2+2 hợp lệ, 4+2 bị từ chối; tuổi 0/11 hợp lệ, 12 bị chặn; 2 người × 3 ngày = 6 suất/1.500.000đ; xóa trống ô người dùng bữa vô hiệu hóa nút Áp dụng; bộ lọc đúng 4 loại hướng biển và 3 loại không ban công/tầng thấp |
| JavaScript/mobile 390px | 0 lỗi JavaScript, không tràn ngang |
| Kiểm tra artifact backend | Chỉ còn `RoomCatalogInitializer`; ba initializer phòng/tiện nghi/dịch vụ cũ không nằm trong JAR |

Chrome chỉ đọc API thật. POST tạo đơn được chặn/giả lập để kiểm tra payload và giữ DB local nguyên trạng; các luồng ghi và tương thích đơn cũ được kiểm tra trong PostgreSQL Testcontainers. Không suy kết quả này thành cam kết đã kiểm thử thanh toán thật hoặc kiểm tra phòng khách sạn thực tế.

Không phát hiện thêm lỗi nghiêm trọng trong phạm vi đã rà soát. Các test chạy đạt không thay thế việc xác nhận dữ liệu/phòng thực tế và UAT của người vận hành.

Còn cảnh báo frontend không chặn chạy: Vite báo chunk lớn; Chrome ghi cảnh báo Ant Design `Card.bordered` đã deprecated và router chưa có `HydrateFallback`. Không có exception JavaScript trong các luồng đã kiểm tra. Chưa thay router hay nâng cấp toàn bộ API giao diện ngoài phạm vi phòng chỉ để xử lý các cảnh báo này.

Các luồng tự kiểm tra được bổ sung trong [room-business-fixes.md](../testing/room-business-fixes.md). Danh mục và chính sách: [room-type-catalog.md](../data/room-type-catalog.md). Danh sách file thay đổi: [room-catalog-changed-files.txt](room-catalog-changed-files.txt).


## Thay đổi sau review: tự tính suất ăn

Theo yêu cầu mới ngày 08/11/2026, đã bỏ nút Áp dụng và cập nhật số suất/tiền ngay khi nhập người/ngày hợp lệ. Mô tả kiểm tra nút Áp dụng trong lượt review trước phản ánh phiên bản khi đó. Kết quả hiện hành và cách tự thử xem phần cập nhật cuối [room-business-fixes.md](../testing/room-business-fixes.md).
