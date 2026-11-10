# Đánh giá hạng phòng

Dữ liệu thử thủ công: [tài khoản mẫu, script SQL và các trường hợp kiểm tra](testing/room-type-reviews-demo.md).

## Quyền đánh giá

- Chỉ tài khoản đang đăng nhập và sở hữu đặt phòng từ lúc đặt (`bookedBy`) được đọc quyền/gửi đánh giá. Với đơn nhiều phòng, lấy chủ sở hữu từ `booking_orders.booked_by`; với booking cũ đơn lẻ, lấy `bookings.booked_by`.
- Khách đặt vãng lai (`bookedBy=null`) không được đánh giá, kể cả đăng nhập sau đó hoặc email tài khoản trùng email liên hệ của đơn. Không tự nhận quyền sở hữu qua email.
- Chức năng đánh giá không còn sử dụng xác thực email, OTP, token đặt phòng hoặc token đánh giá. Các token đặt phòng vẫn phục vụ thao tác booking/payment hiện có, không cấp quyền đánh giá.
- Chưa đăng nhập trả `401`; đã đăng nhập nhưng không sở hữu hoặc đơn vãng lai trả `404`, tránh tiết lộ dữ liệu đặt phòng khác.

## Quy tắc nghiệp vụ

- Chấm số sao nguyên 1–5, bắt buộc; nhận xét không bắt buộc, tối đa 1.000 ký tự, hiển thị như văn bản.
- Đánh giá gắn `RoomType` của booking đã `CHECKED_OUT`, không gắn số phòng vật lý hay `RoomCategory`.
- Gửi trong 30 ngày từ `checkedOutAt` thực tế, bao gồm trả phòng sớm. Booking chưa có thời điểm trả thực tế không đủ điều kiện.
- Booking cũ: một đánh giá/booking. Đơn nhiều phòng: một đánh giá/hạng phòng/đơn; booking dùng gửi phải đã trả. Hạn của hạng trong đơn tính từ lần trả thực tế gần nhất của các booking đã trả cùng hạng.
- Đơn lưu trú khác được đánh giá tiếp. Khóa đơn trước booking và ràng buộc unique ở database bảo đảm yêu cầu đồng thời chỉ tạo một đánh giá.
- Điểm trung bình/số lượt chỉ tính đánh giá chưa xóa; làm tròn một chữ số thập phân. Không có đánh giá: `averageRating=null`, `totalReviews=0`.
- Khách xem lại đánh giá đã gửi; chưa hỗ trợ chỉnh sửa/gửi lại. Tên công khai được rút gọn, không trả email, số điện thoại, mã booking hay user ID.

## Giao diện khách hàng

- Nút đánh giá nằm trong lịch sử booking hoặc phần **Đánh giá sau lưu trú** của chi tiết đơn. Chi tiết đơn trả thêm `reviewOwner`; khách xem bằng token hoặc tài khoản không sở hữu sẽ không thấy mục đánh giá.
- Form có chọn sao, nhãn cảm nhận tương ứng, nhận xét, thời hạn và trạng thái đã gửi. Không hiển thị nút/form khi chưa đăng nhập. Cache quyền đánh giá tách theo tài khoản.
- Cache chi tiết đơn và quyền hủy đơn tách theo tài khoản (khách vãng lai dùng phạm vi riêng). Khi đăng xuất, hết phiên hoặc bắt đầu phiên đăng nhập mới, frontend xóa cache và hủy các truy vấn đang chờ để không giữ lại dữ liệu/quyền đánh giá từ phiên trước.
- Trang hạng phòng có khu vực tổng điểm, ghi rõ điều kiện đánh giá, thẻ nhận xét, phản hồi khách sạn, lọc sao và phân trang. Mọi người vẫn xem được đánh giá công khai.
- Danh sách tự chuyển về trang hợp lệ nếu đánh giá bị xóa làm giảm tổng số trang.

## Manager

- `/management/reviews` lọc theo hạng phòng, số sao, **Chưa phản hồi / Đã phản hồi**, từ khóa tên khách/mã đơn/mã booking và khoảng ngày đánh giá. Từ khóa không phân biệt hoa/thường, tối đa 100 ký tự; `%`, `_` được tìm như ký tự thường. Mã booking bất kỳ thuộc cùng hạng trong đơn đều tìm được đánh giá chung. Từ bảng quản lý loại phòng có nút **Đánh giá**, áp dụng cả hạng đã ngừng kinh doanh.
- Khoảng ngày tính theo ngày gửi đánh giá (`createdAt`), gồm cả ngày đầu và ngày cuối, theo múi giờ `hotel.stay.zone` (mặc định Việt Nam). Có thể chọn riêng ngày đầu hoặc ngày cuối. Quy tắc này áp dụng cả tab **Đang hiển thị** và **Lịch sử đã xóa**; lịch sử vẫn sắp xếp theo thời điểm xóa.
- Thống kê tổng số, chưa phản hồi, đã phản hồi và đánh giá 1–2 sao được tính ở database trên **toàn bộ kết quả của cùng bộ lọc**, không phụ thuộc trang đang xem. Thêm/sửa/gỡ phản hồi hoặc xóa đánh giá đều tải lại thống kê và danh sách. Lỗi thống kê hiển thị cảnh báo, không thay bằng số 0 giả.
- Bộ lọc được lưu trên URL (`roomTypeId`, `rating`, `replied=true/false`, `search`, `fromDate`, `toDate`, `view=deleted`), giữ lại khi tải lại trang hoặc chuyển tab. Nhập từ khóa rồi nhấn **Tìm**/Enter để áp dụng. **Xóa bộ lọc** bỏ mọi điều kiện, giữ tab đang xem. Mỗi đánh giá có nhãn trạng thái và ngăn xem chi tiết; điện thoại hiển thị dạng thẻ dọc. Hộp phản hồi hiển thị đánh giá gốc và thông báo phản hồi sẽ được công khai.
- Chỉ MANAGER quản lý, phản hồi và xóa. ADMIN quản trị tài khoản, lễ tân, dọn phòng và khách không có quyền quản lý đánh giá.
- Chi tiết gồm tên đầy đủ, mã đơn/booking, các booking cùng hạng, phòng được phân, kỳ lưu trú, thời điểm nhận/trả thực tế. API công khai không chứa các thông tin này.
- Xóa bắt buộc nhập lý do tối đa 500 ký tự. Lưu `deleted_at`, `deleted_by`, `delete_reason`; bản gốc và phản hồi còn trong lịch sử. Gọi xóa lại giữ dấu vết lần đầu. Dữ liệu cũ có thể chưa có lý do.
- Đánh giá đã xóa không công khai, không tính điểm, không thể gửi lại cùng lần lưu trú. Dữ liệu đánh giá có sẵn được giữ lại khi đổi chính sách xác thực.
- Manager có thể thêm/sửa/gỡ một phản hồi tối đa 1.000 ký tự cho đánh giá chưa xóa. Lưu manager và thời điểm cập nhật. Phản hồi và ngày cập nhật xuất hiện công khai.

## API

Tiền tố `/api/v1`; dữ liệu trả theo `ApiResponse`, danh sách theo `PageResponse`.

| Method | Đường dẫn                                                                     | Quyền / dữ liệu                                                                                           |
| ------ | ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| GET    | `/room-types/{id}/reviews?page=0&size=6&rating=5`                             | Công khai; `rating` không bắt buộc                                                                        |
| GET    | `/room-types/{id}/reviews/summary`                                            | Công khai; `{averageRating,totalReviews}`                                                                 |
| GET    | `/bookings/{bookingId}/review-eligibility`                                    | JWT của chủ đặt phòng; `{canReview,reasonCode,message,deadline,review}`                                   |
| POST   | `/bookings/{bookingId}/reviews`                                               | JWT của chủ đặt phòng; `{rating,comment?}`; `201`                                                         |
| GET    | `/management/reviews?page=0&size=20&roomTypeId={uuid}&rating=1&deleted=false` | MANAGER; bộ lọc tùy chọn; `deleted=true` xem lịch sử                                                      |
| GET    | `/management/reviews/summary`                                                 | MANAGER; cùng bộ lọc, không phân trang; `{totalReviews,unrepliedReviews,repliedReviews,lowRatingReviews}` |
| DELETE | `/management/reviews/{id}`                                                    | MANAGER; `{reason}`; `204`                                                                                |
| PUT    | `/management/reviews/{id}/reply`                                              | MANAGER; `{reply}`; tạo hoặc cập nhật                                                                     |
| DELETE | `/management/reviews/{id}/reply`                                              | MANAGER; gỡ phản hồi; `204`                                                                               |

Danh sách công khai trả `ReviewResponse` gồm `hotelReply`, `repliedAt`. Danh sách quản lý trả `{review,bookingCode,orderCode,guestName,stays,deletedAt,deletedByName,deleteReason,repliedByName}`. Sắp xếp ngày tạo giảm dần rồi ID; lịch sử xóa theo ngày xóa giảm dần. `page>=0`, `1<=size<=100`, `rating` nếu có từ 1–5.

Cả danh sách quản lý và thống kê nhận thêm `replied` (bỏ trống = tất cả), `search`, `fromDate`, `toDate` (ngày ISO `yyyy-MM-dd`). Khoảng ngày đảo ngược, ngày sai định dạng, từ khóa quá dài hoặc giá trị bộ lọc không hợp lệ bị từ chối với `400`. Hai endpoint dùng chung điều kiện lọc để giữ thống kê khớp danh sách.

Thông tin lưu trú của mọi đánh giá trên trang được tải trong một truy vấn theo nhóm rồi ghép theo ID đánh giá. Trang đủ dữ liệu dùng 3 truy vấn SQL: danh sách, đếm tổng, các lần lưu trú. Kiểm thử với 25 đánh giá xác nhận trang chứa 1 phần tử và trang chứa 20 phần tử đều dùng 3 truy vấn, không phát sinh truy vấn riêng cho từng đánh giá. Thống kê dùng một truy vấn tổng hợp riêng.

Mã nghiệp vụ: `REVIEW_STAY_NOT_COMPLETED`, `REVIEW_WINDOW_EXPIRED`, `REVIEW_ALREADY_EXISTS`, `REVIEW_REMOVED` trả `409` khi gửi không đủ điều kiện. Đọc eligibility trả `200` kèm lý do. Sai chủ sở hữu trả `404`, chưa đăng nhập trả `401`, không có quyền manager trả `403`.

## Migration

1. `V202611091900__add_room_type_reviews.sql`: bảng đánh giá cơ bản.
2. `V202611092000__extend_review_management_and_guest_access.sql`: migration lịch sử bổ sung phản hồi/lý do xóa và từng tạo bảng xác thực vãng lai; giữ nguyên checksum cho các database đã chạy.
3. `V202611092100__remove_review_guest_access.sql`: xóa bảng xác thực vãng lai và toàn bộ grant/challenge cũ. Không thay đổi booking, payment hoặc nội dung đánh giá có sẵn. Flyway áp dụng khi backend khởi động.

Không sửa hoặc xóa migration cũ đã áp dụng. Mã Java, API, giao diện và tác vụ gửi email/dọn OTP của chức năng đánh giá đã được gỡ.

## Kiểm thử

- Backend: `./mvnw -Dtest='RoomTypeReview*,SecurityIntegrationTest,BookingOrderApiTest' test`. Cần Docker để chạy PostgreSQL Testcontainers; kiểm tra kết quả không bỏ qua ca tích hợp. Có các ca biên ngày theo giờ Việt Nam, lọc phản hồi, tìm booking cùng hạng, tìm ký tự `%`/`_`, thống kê nhiều trang và ngân sách truy vấn SQL.
- Dữ liệu mẫu: `./mvnw -Dtest=ReviewDemoSeedIntegrationTest test` kiểm tra nạp mới, sửa UUID cũ và giữ dữ liệu, chạy lại, rollback khi danh tính mẫu không khớp. Database kiểm thử chạy riêng trong Docker.
- Frontend: `npm run test:run`, `npm run build`.
- Trình duyệt với backend thật: build frontend rồi từ `hotel-booking` chạy `./mvnw -Dreview.e2e=true -Dtest=ReviewBrowserIntegrationTest test`. Cần Node, Playwright Chromium, Docker và cổng 5181 trống. Có thể chỉ định `PLAYWRIGHT_CHROMIUM_EXECUTABLE`.
- Luồng live dùng server Spring và PostgreSQL riêng: khách có token nhưng chưa đăng nhập bị từ chối; đăng nhập sau khi đặt vãng lai vẫn bị từ chối; chủ đơn đã trả phòng gửi đánh giá; manager xem lưu trú, phản hồi, xóa có lý do và xem lịch sử; kiểm tra điểm công khai, bảng xác thực cũ được xóa và giao diện desktop/mobile.

### Kết quả xác nhận ngày 11/11/2026

- Frontend: 64 tệp, **343 kiểm thử** thành công; production build và Prettier trên mã thay đổi thành công.
- Backend: **52 kiểm thử** thành công, không bỏ qua ca tích hợp: 15 ca nghiệp vụ, 12 ca HTTP/phân quyền, 10 ca PostgreSQL, 3 ca dữ liệu mẫu, 1 ca trình duyệt live, 7 ca hồi quy phân quyền chung và 4 ca API đặt nhiều phòng. Spotless xác nhận định dạng Java của chức năng.
- Kiểm thử hiệu năng với 25 đánh giá: trang chứa 1 phần tử và trang chứa 20 phần tử đều dùng **3 truy vấn SQL**; thống kê trả đủ 25 kết quả. Kiểm thử giao diện dùng 45 kết quả xác nhận số liệu không thay đổi khi chuyển trang.
- Trình duyệt live với HTTP và PostgreSQL thật xác nhận khách gửi đánh giá, manager phản hồi/xóa/lịch sử, lọc phản hồi, tìm kiếm không phân biệt hoa/thường, thống kê, giữ bộ lọc khi tải lại và xóa bộ lọc. Giao diện desktop/mobile và lịch chọn ngày không tràn ngang. Kiểm tra bổ sung database local xác nhận Deluxe Twin có 7 đánh giá, 6 chưa phản hồi, 2 đánh giá thấp; bố cục kiểm tra ở các độ rộng 390/768/1280/1440px.
