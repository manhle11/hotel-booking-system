# Dữ liệu kiểm thử đánh giá hạng phòng

Chạy tại thư mục gốc repository, trên database local sau khi backend đã áp dụng Flyway mới nhất:

```sh
docker compose exec -T postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < scripts/seed-room-type-reviews-demo.sql
```

Hoặc mở `scripts/seed-room-type-reviews-demo.sql` trong Query Tool của pgAdmin, chọn database `hotelbooking_db` và chạy toàn bộ tệp. Nếu có lỗi thì `ROLLBACK` trước khi chạy lại.

Script dùng hai hạng phòng hiện có `DELUXE_TWIN` và `DELUXE_KING`, tạo 3 tài khoản, 19 đơn / 21 booking, 7 đánh giá công khai và 1 đánh giá đã xóa. Một đánh giá có sẵn phản hồi khách sạn. Không cần email thật hoặc OTP.

Dữ liệu chỉ phục vụ kiểm thử đánh giá: không tạo giao dịch thanh toán, chứng từ ngân hàng, phân phòng vật lý hoặc quy trình nhận/trả phòng. Không dùng các booking mẫu để kiểm thử thu tiền hay điều phối phòng. Mọi bản ghi mới có định danh cố định và các email `example.test`. Chạy lại giữ kết quả thao tác kiểm thử, không đưa dữ liệu về trạng thái ban đầu và không thay đổi danh mục.

Ba tài khoản mẫu dùng UUID v5 hợp lệ với kiểm tra UUID của frontend. Nếu đã nạp bản cũ dùng ID từ `md5(...)::uuid`, chạy lại cùng script để sửa ID và chuyển các khóa ngoại sang ID mới trong một transaction. Mật khẩu, hồ sơ, vai trò, booking, đánh giá, phản hồi và lịch sử xóa được giữ nguyên. Script chỉ sửa ba tài khoản mẫu có đúng email tương ứng; gặp xung đột sẽ rollback toàn bộ. Sau khi sửa, đăng xuất và đăng nhập lại tài khoản mẫu.

## Tài khoản

Mật khẩu chung: **`ReviewDemo@123`**.

| Email                          | Quyền                             | Dùng để                                            |
| ------------------------------ | --------------------------------- | -------------------------------------------------- |
| `review.customer@example.test` | Khách, không có vai trò nhân viên | Gửi và xem lại đánh giá của mình                   |
| `review.other@example.test`    | Khách, không có vai trò nhân viên | Kiểm tra không thể đánh giá booking của người khác |
| `review.manager@example.test`  | MANAGER                           | Lọc, phản hồi, xóa, xem lịch sử                    |

## Thử giao diện khách

Đăng nhập tài khoản khách chính, mở **Đặt phòng của tôi** (`/account/bookings`). Có thể mở trực tiếp `/account/bookings/RV-DEMO-01` để xem chi tiết; thao tác đánh giá nằm ở thẻ booking trong danh sách hoặc chi tiết đơn.

| Booking            | Trường hợp                                                       | Kết quả mong đợi                                                        |
| ------------------ | ---------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `RV-DEMO-01`       | Deluxe Twin, đã trả phòng 2 ngày, chưa đánh giá                  | Gửi được 1–5 sao, có hoặc không có nhận xét                             |
| `RV-DEMO-02`       | Deluxe King, đã trả phòng 1 ngày                                 | Gửi được đánh giá độc lập với Twin                                      |
| `RV-DEMO-03`       | Đang lưu trú                                                     | Không có nút đánh giá ở lịch sử; API trả `REVIEW_STAY_NOT_COMPLETED`    |
| `RV-DEMO-04`       | Đã xác nhận, chưa nhận phòng                                     | Không được đánh giá                                                     |
| `RV-DEMO-05`       | Đã hủy                                                           | Không được đánh giá                                                     |
| `RV-DEMO-06`       | Đã hết hạn giữ phòng                                             | Không được đánh giá                                                     |
| `RV-DEMO-07`       | Đã trả phòng 32 ngày                                             | Mở form thấy thông báo hết hạn 30 ngày                                  |
| `RV-DEMO-08`       | Đặt vãng lai, email liên hệ trùng khách chính                    | Không xuất hiện trong lịch sử khách; API vẫn trả `404` khi đăng nhập    |
| `RV-DEMO-09`–`15`  | Đã có đánh giá                                                   | Mở form để xem lại, không gửi thêm được                                 |
| `RV-DEMO-16`       | Đánh giá đã bị manager xóa                                       | Hiện thông báo đã xóa, không gửi lại được                               |
| `RV-DEMO-17`, `18` | Hai phòng Twin cùng một đơn                                      | Gửi qua một booking; booking còn lại thấy cùng đánh giá, không tạo thêm |
| `RV-DEMO-19`       | Phòng King trong cùng đơn `17`                                   | Vẫn đánh giá được vì là hạng phòng khác                                 |
| `RV-DEMO-20`       | Thuộc tài khoản khách khác                                       | Khách chính bị `404`; khách khác đăng nhập thì đánh giá được            |
| `RV-DEMO-21`       | Trả phòng sớm: thực tế cách đây 3 ngày, kế hoạch cách đây 1 ngày | Gửi được; hạn dựa trên thời điểm trả thực tế                            |

Các mốc ngày tính theo ngày nạp dữ liệu lần đầu, múi giờ Việt Nam. Hạn gửi sẽ tự trôi theo thời gian; dữ liệu không tự làm mới mỗi lần khởi động.

## Thử trang công khai và manager

1. Mở trang hạng **Deluxe Twin**. Nếu hạng này chưa có đánh giá khác, ban đầu phải có **7 lượt, 3,4/5**, chia thành **2 trang** (6 + 1). Một nhận xét chỉ có số sao. Link chính xác được in khi chạy SQL.
2. Lọc từng mức sao; mức 1 sao vẫn có nhận xét tiêu cực hợp lệ. Đánh giá đã xóa của `RV-DEMO-16` không xuất hiện và không được tính điểm.
3. Đăng nhập manager, mở `/management/reviews`, lọc Deluxe Twin. Xem phản hồi có sẵn của `RV-DEMO-10`; thử sửa/gỡ hoặc thêm phản hồi cho đánh giá khác, sau đó kiểm tra trang công khai.
4. Xóa đánh giá 1 sao của `RV-DEMO-13`, nhập lý do. Khi chỉ có dữ liệu mẫu và chưa phát sinh thao tác khác, tổng còn **6 lượt, 3,8/5**; nhận xét chuyển sang tab **Lịch sử đã xóa**, có tên manager, thời điểm và lý do.
5. Mở lịch sử đã xóa để kiểm tra `RV-DEMO-16`. Đăng nhập khách và thử gửi lại đánh giá này: phải bị chặn.
6. Đăng xuất rồi xem trang công khai: vẫn đọc được nhận xét. Đăng nhập khách khác: không có menu quản lý, API quản lý trả `403`.

### Bộ lọc và thống kê quản lý

Với dữ liệu mẫu ban đầu, chưa gửi thêm/xóa/gỡ phản hồi:

1. Lọc Deluxe Twin: tổng **7**, chưa phản hồi **6**, đánh giá 1–2 sao **2**. Các số áp dụng toàn bộ kết quả đang lọc, không phải riêng trang hiện tại.
2. Chọn **Chưa phản hồi**: còn 6; chọn **Đã phản hồi**: chỉ còn `RV-DEMO-10`. Tìm `rv-order-10` hoặc `RV-DEMO-10` đều thấy cùng đánh giá; tìm `nguyễn minh an` không phân biệt hoa/thường.
3. Chọn khoảng **Ngày đánh giá** theo ngày hiển thị trên các dòng; cùng ngày đầu/cuối vẫn gồm các đánh giá trong ngày đó. Tải lại trang và chuyển tab: các bộ lọc còn trên URL. **Xóa bộ lọc** bỏ tất cả điều kiện và giữ tab hiện tại.
4. Khi chỉ lọc **Chưa phản hồi**, phản hồi một đánh giá: dòng đó rời danh sách, thống kê cập nhật theo kết quả mới. Gỡ phản hồi từ bộ lọc **Đã phản hồi** có hành vi tương ứng.
5. Sau khi khách đánh giá qua `RV-DEMO-17`, tìm `RV-DEMO-18` vẫn thấy đánh giá chung của hai phòng Twin trong đơn; chi tiết có đủ hai booking và không lặp đánh giá.

## Kiểm tra API trực tiếp

SQL in `booking_id` và `order_id` của từng booking. API dùng UUID, không dùng mã `RV-DEMO-xx` ở hai đường dẫn dưới đây. Đăng nhập lấy access token qua `POST /api/v1/auth/login` với body `{"identifier":"review.customer@example.test","password":"ReviewDemo@123"}`, sau đó gửi header `Authorization: Bearer <access-token>`.

```http
GET /api/v1/bookings/<booking_id>/review-eligibility

POST /api/v1/bookings/<booking_id>/reviews
Content-Type: application/json

{"rating": 5, "comment": "Phòng sạch, giường thoải mái, phục vụ tốt."}
```

Thử thêm `rating=0`, `6`, `1.5`, chuỗi `"5"`, thiếu số sao hoặc nhận xét quá 1.000 ký tự: đều phải bị từ chối. Chưa đăng nhập phải bị `401`.

Với đơn vãng lai số `08`, gọi `GET /api/v1/booking-orders/<order_id>` kèm header `X-Booking-Access-Token: ReviewDemoOrder08-LocalOnly-AccessToken`: đọc được đơn và `reviewOwner=false`. Gửi đánh giá hoặc đọc quyền đánh giá với cùng token vẫn bị `401` nếu chưa đăng nhập, hoặc `404` nếu đã đăng nhập khách chính. Không đưa token vào URL.
