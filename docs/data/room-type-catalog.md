# HANBAY: 6 nhóm, 8 Room Type, 36 phòng vật lý

Danh mục hiện hành theo cấu trúc người dùng cung cấp. Giá, diện tích và sức chứa bên dưới là thông số mẫu được khai báo riêng để demo; số lượng phòng và cấu hình giường tuân thủ chính xác yêu cầu. Ảnh stock chỉ minh họa không gian.

| Nhóm | Room Type / mã | Giường cố định | Phòng vật lý | Số lượng | m² | Người lớn / trẻ em / tổng tối đa | Giá VND/phòng/đêm |
| --- | --- | --- | --- | ---: | ---: | --- | ---: |
| Standard | Standard Queen / STANDARD_QUEEN | 1 QUEEN | 201–206 | 6 | 22 | 2 / 0 / 2 | 1.100.000 |
| Superior | Superior Queen / SUPERIOR_QUEEN | 1 QUEEN | 301–305 | 5 | 30 | 2 / 1 / 3 | 1.650.000 |
| Superior | Superior Twin / SUPERIOR_TWIN | 2 SINGLE | 306–310 | 5 | 30 | 2 / 1 / 3 | 1.700.000 |
| Deluxe | Deluxe King / DELUXE_KING | 1 KING | 401–405 | 5 | 36 | 2 / 1 / 3 | 2.450.000 |
| Deluxe | Deluxe Twin / DELUXE_TWIN | 2 SINGLE | 406–410 | 5 | 36 | 2 / 1 / 3 | 2.500.000 |
| Triple | Triple Mixed / TRIPLE_MIXED | 1 QUEEN + 1 SINGLE | 501–504 | 4 | 38 | 3 / 0 / 3 | 2.800.000 |
| Family | Family Double Queen / FAMILY_DOUBLE_QUEEN | 2 QUEEN | 601–604 | 4 | 48 | 4 / 2 / 4 | 3.600.000 |
| Suite | Suite King / SUITE_KING | 1 KING | 701–702 | 2 | 60 | 2 / 1 / 3 | 5.200.000 |
| **Tổng** | **8 loại** | **9 dòng cấu hình giường** | | **36** | | | |

Mỗi phòng vật lý thuộc đúng một Room Type, khởi tạo trạng thái AVAILABLE. Mỗi Room Type có 3 ảnh, ảnh đại diện, mô tả ngắn/chi tiết và danh sách tiện nghi. Giường không tự quyết định sức chứa và không tạo thêm đơn vị tồn phòng.

Ngày 11/11/2026, theo yêu cầu bổ sung tầng 5 thành 4 phòng, đã thêm **504** cùng
hạng Triple Mixed, trạng thái AVAILABLE; giữ nguyên 35 phòng cũ và mọi thông số
hạng. Danh mục hiện hành là 36 phòng. Bộ seed Flyway gốc vẫn là 35 phòng để giữ
checksum và hành vi bootstrap lịch sử; dùng [script bổ sung 504](../../scripts/add-triple-room-504.sql)
sau khi khởi tạo/reset bộ seed gốc để tái tạo danh mục hiện hành. Script chỉ thêm
phòng thiếu, không thay loại/tầng/trạng thái của 504 đã có và có thể chạy lại.

## Tách thông tin đúng nghiệp vụ

| Nhóm thông tin | Nguồn dữ liệu |
| --- | --- |
| Nhóm hiển thị | room_categories: Standard, Superior, Deluxe, Triple, Family, Suite |
| Lựa chọn khách đặt | room_types: 8 loại ở bảng trên |
| Giường và số lượng | room_type_beds; chỉ dùng SINGLE, DOUBLE, QUEEN, KING; không có giường phụ |
| Sức chứa, diện tích, giá cơ bản | max_guests, capacity_adults, capacity_children, area_m2, base_price trên Room Type |
| Hướng nhìn, ban công, tầng cao | view_type, has_balcony, high_floor trên Room Type |
| Tiện nghi có sẵn | amenities và room_type_amenities: Wi-Fi, TV, điều hòa, tủ lạnh, minibar, két an toàn, đồ dùng phòng tắm… |
| Dịch vụ mua thêm | add_on_services: đón/tiễn sân bay, trang trí phòng, buffet sáng, bữa ăn thêm |
| Phòng cụ thể khi vận hành | rooms: số phòng, tầng, Room Type và trạng thái |

Standard hướng sân trong; Superior hướng thành phố; Deluxe hướng biển, có ban công và thuộc tầng cao; Triple hướng thành phố ở tầng cao; Family và Suite hướng biển, có ban công và thuộc tầng cao. Đây là thuộc tính được khai báo, không suy ra tự động từ số tầng hay mô tả.

Wi-Fi, TV và điều hòa không được bán lại như dịch vụ bổ sung. Minibar là trang thiết bị; dữ liệu không tuyên bố đồ dùng bên trong miễn phí. Không có nghiệp vụ giường phụ, không tăng capacity theo giường.

## Sức chứa và chính sách trẻ em đã thống nhất

Tuổi được tính tại ngày nhận phòng: dưới 6 là trẻ nhỏ; từ 6 đến dưới 12 là trẻ em; từ 12 là người lớn. Hai nhóm dưới 12 đều chiếm một suất trẻ em và một suất tổng khách, sử dụng giường có sẵn. Không triển khai giường phụ, miễn phí hoặc phụ thu tự động theo tuổi. Đây là chính sách mẫu được chủ dự án duyệt, không phải quy tắc chung cho mọi khách sạn.

Family Double Queen: tối đa 4 người lớn, 2 trẻ em, **tổng tối đa 4 khách**. Hợp lệ: 4+0, 3+1, 2+2; không hợp lệ: 4+2 hoặc 1+3. Những Room Type có giới hạn 0 trẻ em vẫn giữ nguyên chính sách đã khai báo.

Checkout yêu cầu nhập đủ tuổi cho từng trẻ em, lưu `bookings.child_ages`. API mới kiểm tra tuổi khi có `childAges`; API cũ không gửi tuổi vẫn được kiểm tra theo số lượng đã khai báo. `NULL` trên booking cũ nghĩa là chưa có dữ liệu tuổi, không tự suy đoán. `/api/v1/stay-policy` trả các ngưỡng cấu hình từ `hotel.stay.young-child-under-age` và `hotel.stay.adult-from-age` (mặc định 6/12).

Giảm một trong ba giới hạn sức chứa phải không làm vượt số khách của booking `CONFIRMED` sắp tới, `PENDING` còn hạn hoặc `CHECKED_IN` (kể cả quá ngày trả). Nếu xung đột, trả HTTP 409 `ROOM_TYPE_CAPACITY_CONFLICT`. Kiểm tra và ghi dùng cùng khóa Room Type với tạo booking. Booking hủy/hoàn tất/hết hạn không ngăn điều chỉnh sức chứa mới; quy tắc khóa giường của mọi booking lịch sử vẫn giữ nguyên.

## Đơn vị dịch vụ mua thêm

`quantity_unit`: `SERVING` (suất ăn), `TRIP` (lượt xe), `PACKAGE` (gói trang trí), `UNIT` (đơn vị chung cho dịch vụ cũ/chưa phân loại). Giá catalog là giá mỗi đơn vị. `quantity` là **tổng số đơn vị cho cả kỳ lưu trú**, giới hạn 1–1000, không tự nhân thêm theo số đêm/khách. Ví dụ 2 người ăn sáng trong 3 ngày = 6 suất × 250.000 = 1.500.000 VND. Bộ tính người × ngày tự cập nhật số suất và tổng tiền ngay khi nhập hợp lệ, không còn nút Áp dụng. Ô tổng suất chỉ đọc; khách tự chọn số người dùng bữa, kể cả trẻ em nếu có. Khi chưa nhập người, dịch vụ chưa được chọn và không tự phát sinh tiền. Xóa số người đưa số suất về 0; số ngày trống/không hợp lệ khi đã chọn người sẽ xóa số suất tính cũ và chặn tiếp tục cho tới khi sửa hoặc bỏ chọn dịch vụ. Số ngày dùng bữa tối đa bằng số đêm + 1, giữ giới hạn của bộ tính hiện có.

Booking lưu đơn vị cùng đơn giá/số lượng/thành tiền. Sửa giá/đơn vị trên catalog không thay snapshot cũ. Dữ liệu lịch sử chưa biết đơn vị giữ `UNIT`. Migration chỉ gán đơn vị cụ thể cho 5 dịch vụ demo có ID xác định, không đoán dịch vụ tùy chỉnh. Không thay cơ chế giá phòng, thanh toán, hoàn tiền hoặc tính tồn.

## Tìm kiếm đặc điểm phòng

`GET /api/v1/room-types/available` nhận thêm `viewType=COURTYARD|CITY|SEA`, `hasBalcony=true|false`, `highFloor=true|false`. Bỏ tham số để lấy tất cả. `false` là điều kiện thực sự; dữ liệu `NULL` chưa xác minh không được xem là `false`. Lọc trước khi tính phân trang, vẫn dùng tồn từ phòng vật lý như trước.

## Reset database local

Reset là thao tác chủ động trong `scripts/reset-room-catalog.sql`. Flyway `202611071800` định nghĩa hàm `seed_hanbay_room_demo_catalog()` làm nguồn dữ liệu duy nhất cho reset và khởi tạo local.

Khi chạy profile `local`, `RoomCatalogInitializer` gọi `initialize_hanbay_room_demo_catalog()`. Hàm chỉ thay bộ seed lịch sử nếu checksum của toàn bộ danh mục/phòng/ảnh/giường/tiện nghi trùng nguyên bản Flyway và chưa có booking, đơn đặt, thanh toán, bảo trì hoặc quy tắc giá. Bất kỳ chỉnh sửa/dữ liệu phát sinh nào đều khiến hàm giữ nguyên dữ liệu. Database local mới khởi tạo thành 6 nhóm, 8 loại, 35 phòng; khởi động lại không bù phòng bị xóa hay tái tạo Ocean Suite. Có thể tắt bằng `HOTEL_DEMO_CATALOG_ENABLED=false`; profile khác không chạy initializer.

Đã bỏ `RoomDataInitializer` tự sinh 5 phòng cho mỗi loại và `AmenityDataInitializer` cũ. Tests nghiệp vụ dùng fixture migration lịch sử với `hotel.demo-catalog.enabled=false`; tests bootstrap riêng bật cấu hình này và xác minh dữ liệu thực tế.

1. Sao lưu database bằng `pg_dump --format=custom`; đặt bản sao trong `.local-backups/` được gitignore và giới hạn quyền đọc.
2. Áp dụng Flyway đến `202611111000`, gồm hàm danh mục chuẩn, các migration đánh giá hạng phòng và bảng sơ đồ tầng. Reset xóa cả sơ đồ, đánh giá, phản hồi và lịch sử xóa gắn với các booking bị xóa.
3. Chạy script bằng psql với `ON_ERROR_STOP=1` trên `hotelbooking_db`.
4. Chạy `scripts/add-triple-room-504.sql` để bổ sung phòng 504 sau bộ seed gốc 35 phòng.
5. Kiểm tra lại đúng 6 nhóm, 8 loại, 36 phòng, 9 dòng giường và 5 dịch vụ mua thêm.

```sh
docker exec -i hotelbooking_postgres psql -U hotelbooking_user -d hotelbooking_db -X -v ON_ERROR_STOP=1 < scripts/reset-room-catalog.sql
docker exec -i hotelbooking_postgres psql -U hotelbooking_user -d hotelbooking_db -X -v ON_ERROR_STOP=1 < scripts/add-triple-room-504.sql
```

**Dữ liệu bị xóa:** các loại/phòng cũ, cấu hình giường, ảnh và tiện nghi cũ, booking/đơn đặt phòng/khách lưu trú/đêm phòng/lịch sử booking, thanh toán/phân bổ/giao dịch/webhook/hoàn tiền, quy tắc giá, phiếu và chi phí bảo trì, danh mục dịch vụ mua thêm cũ. Các danh mục phòng, tiện nghi và dịch vụ được nạp lại theo bộ mới.

**Dữ liệu giữ nguyên:** users, roles, user_roles, refresh_tokens, password_reset_tokens, flyway_schema_history, holiday_calendar, ai_requests và dữ liệu tri thức AI. Tài khoản có thể đăng nhập như trước; danh sách booking và thanh toán ban đầu sẽ trống sau reset.

Script nằm trong một transaction, liệt kê rõ mọi bảng và không dùng TRUNCATE CASCADE. Thiếu migration, sai tên database, xung đột dữ liệu hoặc sai phân bổ phòng khiến reset thất bại; không nhận trạng thái nạp một phần.

Các migration `202611071000` và `202611071500` đã áp dụng nên được giữ nguyên để Flyway kiểm tra checksum. Bộ 14 loại trong migration lịch sử không còn là danh mục hiện hành sau reset. Test và tài liệu chỉ phục vụ bộ 14 loại đã được dọn; các cơ chế category/giường, khóa lịch sử và vô hiệu hóa vẫn phục vụ mô hình mới.

## Kiểm tra trên giao diện

1. Vào `/management/room-types`: thấy đúng 8 loại, nhóm và giường hiển thị thành cột riêng.
2. Mở Triple Mixed: giường Queen × 1 và Single × 1; mở Family Double Queen: Queen × 2.
3. Mở Deluxe King: hướng biển, có ban công, tầng cao nằm trong Đặc điểm phòng, tách khỏi Tiện nghi.
4. Vào `/management/rooms`: tổng 36, đối chiếu số phòng và loại theo bảng trên; mỗi phòng có trạng thái Sẵn sàng khi chưa phát sinh vận hành.
5. Vào `/room-types`: 8 loại có nhóm, giường, ảnh và trang chi tiết; chuyển trang để xem đủ.
6. Tìm phòng ở một khoảng ngày trong tương lai, 1 người lớn: có 8 loại, tồn lần lượt 6/5/5/5/5/4/4/2 khi chưa có booking.
7. Vào Dịch vụ bổ sung: có 5 dịch vụ mua thêm, không có Wi-Fi/TV/giường phụ.
8. Khởi động lại backend: số phòng vẫn 36 sau khi đã bổ sung 504; không tái tạo Ocean Suite hay dịch vụ giường phụ.

## Kết quả áp dụng local ngày 07/11/2026

Đã reset database `hotelbooking_db`: 6 nhóm, 8 Room Type, 9 dòng giường, 35 phòng vật lý, 5 dịch vụ mua thêm; booking, thanh toán và phiếu bảo trì đều về 0. Giữ nguyên 5 tài khoản cùng các bảng được bảo vệ.

Bản sao lưu trước reset nằm tại `.local-backups/before-room-reset-20261107-152147/hotelbooking_db.dump`, quyền file 600, thư mục được gitignore. `reset-report.json` cùng thư mục ghi số lượng sau reset và kết quả đối chiếu các bảng được bảo vệ.

Đã chạy 831 test backend và 310 test frontend thành công. Chrome đã kiểm tra dữ liệu thật qua backend mới, gồm tồn 35 phòng đúng phân bổ, Triple Queen + Single, Family 2 Queen, đặc điểm tách khỏi tiện nghi và không có giường phụ. Backend kiểm thử tạm không thay thế server chính của người dùng; khởi động lại backend chính để nạp các thay đổi Java mới trước khi kiểm tra trên giao diện đang dùng.


Lượt bổ sung cùng ngày đã nâng cấp Flyway đến `202611071800`, không reset dữ liệu. Family chuyển giới hạn trẻ em từ 0 lên 2, tổng vẫn 4; 5 dịch vụ mẫu được gắn đơn vị bán, giữ nguyên giá. 31 bảng khác giữ nguyên checksum. Backup riêng trước nâng cấp: `.local-backups/before-room-business-fixes-20261107-165115/hotelbooking_db.dump`. [Luồng tự kiểm tra và kết quả đầy đủ](../testing/room-business-fixes.md).

## Review ngày 08/11/2026

Đã review toàn bộ thay đổi của nhánh, sửa 5 lỗi liên quan đến cập nhật tuổi trẻ em, tương thích client/đơn cũ, cấu hình bootstrap và ô số lượng suất ăn trống. Đã bỏ initializer dịch vụ trùng với nguồn seed chuẩn cùng các phần không dùng. Cấu hình bootstrap nằm trong `application.yaml` được theo dõi trong Git; không phụ thuộc vào file local riêng. Khi khởi động lại, hệ thống không tự tạo lại dịch vụ đã bị xóa hoặc sửa giá do người quản lý đặt.

Kết quả review 08/11: **838 test backend, 311 test frontend đạt**, build/lint/Spotless đạt; Chrome xác minh desktop/mobile. Lần review này không reset hay sửa dữ liệu local. Đánh giá nghiệp vụ và các giới hạn còn lại được giải thích trong [báo cáo review 08/11/2026](../reviews/room-catalog-review-2026-11-08.md).

## Kiểm tra trước khi push ngày 09/11/2026

Sau thay đổi tự tính suất ăn, đã chạy lại **838 test backend và 314 test frontend đạt**; clean build/Spotless, frontend build/lint/format đều đạt. Kiểm tra bổ sung 59 test backend từ bản sao source không có `.env` hay cấu hình local riêng cũng đạt. Không reset/sửa database local, không commit/push trong lượt này. Chi tiết kết quả và lưu ý đưa đủ file mới/migration vào commit: [báo cáo kiểm tra trước push](../reviews/room-prepush-check-2026-11-09.md).

Rà soát sâu tiếp theo cùng ngày đã tái hiện thêm 2 lỗi (cache catalog/checkout chưa được invalidation đầy đủ; pending hết hạn vẫn chặn thao tác phòng vật lý) và xác nhận rủi ro mất số phòng trên đơn cũ khi xóa phòng vật lý. Đã sửa cả ba theo yêu cầu: chặn xóa phòng được booking tham chiếu bằng HTTP 409, làm mới cache khi sửa/ngừng bán/mở bán lại Room Type và xét hạn giữ chỗ theo giờ khách sạn. Không đổi schema, không cần migration mới; xem kết quả hồi quy trong báo cáo trên.

Sau ba bản sửa: **855 test backend và 317 test frontend đạt**, backend clean verify/Spotless và frontend build/lint/format đạt. Các thao tác dữ liệu trong test dùng database riêng; database local được giữ nguyên.
