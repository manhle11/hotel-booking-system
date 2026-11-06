# Kiểm tra đặt nhiều phòng sau đợt sửa 06/11/2026

Các thay đổi nằm trên nhánh `feature/multi-room-booking`.

## 1. Phạm vi đã sửa

| Vấn đề | Cách xử lý | Kết quả mong đợi |
|---|---|---|
| Hai khoản hoàn bằng nhau có thể cập nhật nhầm cùng giao dịch | Mỗi yêu cầu hoàn liên kết duy nhất với một giao dịch; khóa payment trước khi xác nhận | Hai yêu cầu hoàn thành thì có đúng hai giao dịch hoàn thành; payment được đối soát đúng |
| API trạng thái payment cũ lỗi 500 khi hết hạn | Cập nhật có điều kiện bằng đúng enum PostgreSQL `payment_status` | Payment đang chờ chuyển sang `EXPIRED` khi đến hạn; không ghi đè `SUCCESS` |
| Đã chuyển một phần nhưng QR vẫn yêu cầu tổng tiền gốc | Bổ sung `remainingAmount`, tạo lại QR theo khoản thiếu và cập nhật số tiền trên giao diện | Số tiền hướng dẫn chuyển khoản và số tiền trong QR đều bằng khoản còn thiếu |
| Tổng đơn không tăng khi trả phòng có phí phát sinh | Giữ giá đặt ban đầu riêng; tổng thực tế lấy từ tổng các booking con | Tổng đơn, từng phòng, số đã thanh toán và lịch sử khớp nhau |

Liên kết giao dịch hoàn được áp dụng cả cho hủy booking cũ, trả sớm, hoàn tiền thừa/đến muộn và đơn nhiều phòng.

## 2. Chuẩn bị

1. Chạy backend mới và frontend mới. Backend sẽ tự áp dụng migration `V202611061800__link_refund_transactions.sql` qua Flyway.
2. Có tối thiểu hai phòng cùng loại và một phòng khác loại trống trong kỳ thử. Dùng những đơn thử riêng cho từng trường hợp.
3. Chuẩn bị khách vãng lai, một tài khoản khách, một tài khoản khách khác, lễ tân và quản lý.
4. Mở DevTools → Network, bật Preserve log. Ghi lại `orderId`, các `bookingId`, `paymentId`, mã `ORD-…`, mã `PAY-…`, `expiresAt`.
5. Các ví dụ dưới dùng tổng đơn **4.000.000đ** và hai phòng **2.000.000đ/phòng**. Nếu giá thực tế khác, thay số tương ứng; không sửa giá bằng request từ trình duyệt.
6. Dùng môi trường local/test để giả lập webhook. Không cần chuyển tiền ngân hàng thật khi chạy bộ kiểm thử tự động.

Khách vãng lai cần giữ nguyên tab đang đặt phòng; quyền xem đơn được lưu bằng token trong `sessionStorage`. Tiền tố API là `/api/v1`.

## 3. Thanh toán thiếu → QR còn thiếu → đủ tiền

1. Tìm phòng, chọn hai phòng, nhập liên hệ một lần và giữ phòng. Mở `/booking-orders/{orderId}`.
2. Đồng ý điều khoản và tạo QR. Kiểm tra chỉ có một payment; `payment.amount = 4000000`, `payment.remainingAmount = 4000000`.
3. Gửi một webhook hợp lệ ghi nhận **100.000đ** cho mã `PAY-…` này.
4. Chờ trang tự cập nhật, khoảng 3 giây. Kiểm tra:
   - Đơn và tất cả booking vẫn `PENDING`.
   - Đã nhận **100.000đ**, còn cần thanh toán **3.900.000đ**.
   - Dòng **Số tiền** trong hướng dẫn chuyển khoản là **3.900.000đ**.
   - Network → response GET đơn: `payment.amount` vẫn **4.000.000**, `payment.remainingAmount = 3900000`.
   - URL ảnh QR có tham số `amount=3900000`; nội dung chuyển khoản giữ nguyên.
5. Tải lại trang hoặc gọi lại POST `/booking-orders/{orderId}/payments`: vẫn cùng `paymentId`, mã chuyển khoản và khoản thiếu **3.900.000đ**.
6. Gửi thêm **200.000đ** bằng một sự kiện ngân hàng mới: khoản còn thiếu và QR cùng chuyển thành **3.700.000đ**.
7. Nếu trước đó ảnh QR lỗi tải, lần cập nhật URL mới vẫn thử hiển thị ảnh QR mới.
8. Chuyển đúng **3.700.000đ** còn lại: toàn bộ phòng chuyển `CONFIRMED`, payment `SUCCESS`, `remainingAmount = 0`, QR chờ thanh toán được ẩn.
9. Gửi lại chính webhook cuối, giữ nguyên `id`: số đã nhận, số phân bổ và số yêu cầu hoàn không tăng.

Kiểm tra thêm GET `/payments/{paymentId}/status` bằng đúng quyền/token của đơn: QR và `remainingAmount` phải khớp trang đơn ở bước 4 và 6. `amount` vẫn là số tiền gốc của payment.

### Gửi webhook local bằng Postman

POST `/api/v1/payments/sepay/webhook`, header `Authorization: Apikey <SEPAY_WEBHOOK_API_KEY của môi trường test>`, `Content-Type: application/json`.

```json
{
  "id": 990000001,
  "gateway": "TEST",
  "transactionDate": "2026-11-06 19:00:00",
  "accountNumber": "<SEPAY_BANK_ACCOUNT_NUMBER của môi trường test>",
  "code": "<PAY-… của đơn>",
  "content": "<PAY-… của đơn>",
  "transferType": "in",
  "transferAmount": 100000,
  "accumulated": 0,
  "description": "Test thanh toan mot phan",
  "referenceCode": "TEST-990000001"
}
```

Điền thời gian hiện tại theo giờ Việt Nam và gửi trước `expiresAt` khi kiểm tra thanh toán hợp lệ. Mỗi khoản chuyển mới dùng `id` và `referenceCode` mới. Khi kiểm tra chống trùng, gửi lại nguyên payload cũ. Lấy cấu hình từ môi trường test đang chạy; không đưa API key vào tài liệu hoặc Git.

## 4. Hai yêu cầu hoàn bằng nhau → xác nhận đồng thời

1. Tạo đơn mới **4.000.000đ**, tạo QR, ghi nhận **100.000đ**.
2. Hủy toàn đơn, nhập thông tin ngân hàng nhận hoàn. Đơn `CANCELLED`; có yêu cầu hoàn thứ nhất **100.000đ**.
3. Gửi một khoản chuyển mới **100.000đ** vào cùng mã `PAY-…` đã hủy.
4. Đơn vẫn `CANCELLED`; phát sinh yêu cầu hoàn thứ hai **100.000đ**. Bổ sung tài khoản nhận hoàn nếu trang yêu cầu.
5. Đăng nhập quản lý, mở trang **Yêu cầu hoàn tiền**; hoặc GET `/payment-monitoring/refunds?size=100`. Xác định đúng hai `refundId` cùng payment.
6. Xác nhận hai yêu cầu gần như đồng thời trên hai tab, nhập hai mã giao dịch ngân hàng khác nhau. API tương ứng:

   ```text
   POST /payment-monitoring/refunds/{refundId}/confirm
   Authorization: Bearer <access token quản lý>
   Body: {"bankReference":"TEST-REFUND-1"}
   ```

7. Cả hai xác nhận thành công; hai yêu cầu biến mất khỏi danh sách đang chờ. Kiểm tra bằng truy vấn chỉ đọc bên dưới: đúng **2 refund SUCCESS**, **2 giao dịch REFUND SUCCESS**, **0 giao dịch REFUND PENDING** của payment này; payment `REFUNDED` vì đã hoàn hết **200.000đ** thực nhận.
8. Mỗi yêu cầu trỏ tới giao dịch riêng và `provider_refund_id` bằng `transaction_ref` của chính giao dịch đó.
9. Gửi lại xác nhận cho một `refundId` đã xong: HTTP **409**, mã `REFUND_STATE_INVALID`; không thay đổi khoản hoàn còn lại hoặc tổng tiền.
10. Thử thứ tự ngược: xác nhận yêu cầu thứ hai trước. Chỉ yêu cầu đó và giao dịch được liên kết với nó chuyển thành công. Payment `PARTIALLY_REFUNDED` khi mới hoàn một trong hai khoản, rồi `REFUNDED` sau khoản còn lại.

Kiểm thử tự động còn chủ động giữ khóa payment trước khi chạy hai xác nhận để buộc chúng phải chờ cùng tài nguyên; không chỉ dựa vào tốc độ bấm hai tab.

### Truy vấn đối chiếu chỉ đọc

Thay `<payment-uuid>` bằng payment đang thử:

```sql
SELECT p.id, p.payment_code, p.status AS payment_status,
       r.id AS refund_id, r.amount, r.status AS refund_status,
       r.provider_refund_id, r.transaction_id,
       t.status AS transaction_status, t.transaction_ref
FROM payments p
JOIN payment_refunds r ON r.payment_id = p.id
LEFT JOIN payment_transactions t ON t.id = r.transaction_id
WHERE p.id = '<payment-uuid>'::uuid
ORDER BY r.requested_at, r.id;

SELECT type, status, count(*) AS entries, sum(amount) AS amount
FROM payment_transactions
WHERE payment_id = '<payment-uuid>'::uuid
GROUP BY type, status;
```

## 5. Payment cũ hết hạn → không còn lỗi 500

1. Tạo một booking độc lập qua POST `/bookings`, rồi POST `/payments/requests` với `bookingId`; ghi lại `bookingAccessToken`, `paymentId`, `expiredAt`. Booking con trong đơn nhóm bị chặn ở API thanh toán độc lập, nên không dùng booking con cho trường hợp này.
2. Gọi GET `/payments/{paymentId}/status` kèm `X-Booking-Access-Token` trước hạn: HTTP **200**, payment `PENDING`.
3. Để quá hạn giữ phòng rồi gọi lại: HTTP **200**, payment `EXPIRED`; không còn thông báo PostgreSQL `type "paymentstatus" does not exist`.
4. Gọi lại vài lần: vẫn `EXPIRED`, không tạo thêm payment hoặc hoàn tiền ngoài nghiệp vụ hiện có.
5. Với một booking khác đã nhận đủ tiền trước hạn: sau thời điểm hạn giữ phòng, trạng thái vẫn `SUCCESS`, không bị cập nhật thành `EXPIRED`.

Kiểm thử tích hợp dùng đồng hồ kiểm soát để kiểm tra ngay trước hạn, đúng hạn, gọi lại sau hết hạn và bảo vệ trạng thái `SUCCESS`; không cần tự sửa giờ hệ thống.

## 6. Trả từng phòng có phí phát sinh → tổng đơn khớp

1. Tạo đơn mới hai phòng, trả đủ **4.000.000đ**, nhận cả hai phòng bằng tài khoản lễ tân.
2. Đến ngày trả phòng, trả phòng thứ nhất, nhập phí minibar **200.000đ**, lý do và thu thêm **200.000đ**.
3. Mở lại trang đơn bằng tài khoản khách hoặc tab khách có token. Kiểm tra:
   - Tiền đặt phòng ban đầu: **4.000.000đ**.
   - Phí phát sinh: **200.000đ**.
   - Tổng tiền thực tế: **4.200.000đ**.
   - Đã thanh toán: **4.200.000đ**; còn phải thanh toán **0đ**.
   - Phòng thứ nhất: tổng **2.200.000đ**, `CHECKED_OUT`; phòng thứ hai: **2.000.000đ**, vẫn `CHECKED_IN`.
4. Tải lại trang, mở lịch sử đặt phòng: tổng đơn vẫn **4.200.000đ**.
5. Trả phòng thứ hai không có phí: tổng không tăng thêm. Nếu có thêm phí **100.000đ** và thu đủ thì phí toàn đơn **300.000đ**, tổng **4.300.000đ**.
6. API GET đơn trả `bookedAmount`, `additionalChargeAmount`, `totalAmount`, `paidAmount` đúng các con số trên. Payment QR ban đầu và phân bổ ban đầu vẫn **4.000.000đ**; khoản thu tại quầy gắn với đúng booking phát sinh.

Nhánh lỗi cần thử trên một đơn khác:

1. Phí **200.000đ** nhưng chỉ nhập thu **100.000đ**: từ chối trả phòng, phòng vẫn `CHECKED_IN`, tổng đơn và tiền phòng không bị cộng dở.
2. Nhập thu vượt số cần thu hoặc phí âm/thiếu lý do: bị từ chối, không phát sinh khoản thu hay cộng phí.
3. Bấm trả phòng lần nữa sau khi đã thành công: bị chặn theo trạng thái, không cộng phí lần hai.

`paidAmount` là tiền đã ghi nhận cho các booking. Khoản hoàn được hiển thị ở thông tin hoàn tiền riêng; không coi đây là số tiền ròng sau hoàn. Phí trả sớm, tiền hoàn trả sớm và trạng thái lưu trú tiếp tục theo chính sách hiện có.

## 7. Checklist các luồng liên quan

| Mã | Thao tác | Kết quả cần kiểm tra |
|---|---|---|
| H01 | Hai phòng cùng loại, khác số khách/dịch vụ | Một đơn, hai booking, một lần nhập liên hệ; tiền tính theo từng phòng |
| H02 | Hai phòng cùng loại + một phòng khác loại | Ba booking, một hạn giữ và một QR ban đầu |
| H03 | Bấm tạo QR hai lần | Cùng payment, không sinh yêu cầu thu trùng |
| H04 | Chuyển đủ một lần | Xác nhận toàn bộ phòng cùng lúc |
| H05 | Nhiều khoản chuyển thiếu | Chưa xác nhận phòng nào cho đến khi đủ; QR giảm đúng sau mỗi khoản |
| H06 | Hủy khi chưa thanh toán | Hủy mọi booking, mở bán lại toàn bộ tồn kho |
| H07 | Hủy sau chuyển thiếu | Hoàn đúng phần đã nhận, không mất tiền đã chuyển |
| H08 | Hủy đơn đã thanh toán | Tính chính sách từng booking rồi cộng tổng hoàn; chỉ tạo một lần |
| H09 | Bổ sung ngân hàng cho tiền thừa/đến muộn | Lưu được tài khoản, yêu cầu hoàn tiếp tục được quản lý xử lý |
| H10 | Nhận/trả một phòng | Chỉ thay trạng thái phòng đó; các phòng khác giữ trạng thái riêng |
| H11 | Khách đăng nhập xem lịch sử | Hiển thị một đơn chung, tổng thực tế khớp chi tiết |
| U01 | Tổng số phòng vượt tồn kho dù từng dòng riêng lẻ có vẻ hợp lệ | Từ chối toàn đơn; không tạo booking dở dang |
| U02 | Phòng thứ hai sai sức chứa/dịch vụ | Rollback toàn bộ, không giữ riêng phòng đầu |
| U03 | Hai khách đặt phòng cuối cùng đồng thời | Chỉ một yêu cầu giữ phòng thành công |
| U04 | Mất phản hồi khi tạo đơn, thử lại cùng payload/key/token | Trả về cùng đơn, không giữ thêm tồn kho |
| U05 | Cùng key nhưng đổi payload hoặc token | Bị từ chối, không đọc được đơn cũ hay tạo đơn mới bằng key đó |
| U06 | Gửi lại chính webhook cũ | Không tăng tiền hoặc tạo hoàn trùng |
| U07 | Chuyển thừa | Chỉ phân bổ đủ giá phòng; phần dư có yêu cầu hoàn riêng |
| U08 | Chuyển thêm một giao dịch mới sau khi đã trả đủ | Ghi nhận giao dịch mới để hoàn, không tăng phân bổ phòng |
| U09 | Hết hạn khi chưa trả hoặc đã trả một phần | Mọi phòng hết hạn, giải phóng tồn kho; phần đã nhận được hoàn |
| U10 | Tiền đến sau hủy/hết hạn, kể cả chờ khóa qua hạn | Không khôi phục đơn/phòng; tạo hoàn đúng phần nhận muộn |
| U11 | Hủy lại đơn đã hủy | Không sinh hoàn tiền lần hai |
| U12 | Một phòng đã nhận/trả rồi yêu cầu hủy toàn đơn | Từ chối hủy toàn đơn |
| U13 | Gọi API thanh toán/sửa booking cũ cho booking con | Từ chối thao tác phá vỡ đơn nhóm |
| U14 | Khác tài khoản hoặc token sai/thiếu | Không xem, thanh toán, hủy hoặc bổ sung ngân hàng được cho đơn |
| U15 | Xác nhận lại cùng refund, hoặc xác nhận hai refund bằng nhau đồng thời | Một lần cho mỗi refund, đúng giao dịch liên kết và trạng thái payment |
| U16 | Payment độc lập hết hạn hoặc đã thành công | HTTP 200 cho truy vấn hợp lệ; `EXPIRED` đúng hạn, bảo vệ `SUCCESS` |
| U17 | Trả phòng thiếu tiền/vượt tiền/lặp thao tác | Từ chối và rollback, không cộng dở phí hoặc khoản thu |
| U18 | QR lỗi tải rồi có lần nhận tiền mới | URL QR và số tiền cập nhật; thử tải lại ảnh cho URL mới |
| U19 | Đơn `CANCELLED` hoặc `EXPIRED` | Ẩn QR thanh toán, không hướng dẫn tiếp tục chuyển tiền |

## 8. Dữ liệu hoàn tiền trước migration

Migration liên kết lại các khoản hoàn cũ khi có thể xác định một cặp duy nhất theo mã ngân hàng; các khoản đang chờ được ghép một-một theo thứ tự ổn định khi số lượng và số tiền hai phía khớp. Migration không tự thay trạng thái hay số tiền.

Nếu dữ liệu cũ đã lệch sổ hoặc không xác định được giao dịch tương ứng, `transaction_id` có thể để trống. Khi xác nhận, API trả **409** với thông báo cần đối soát; không lấy đại một giao dịch có cùng số tiền. Các dòng này cần đối chiếu chứng từ thực tế trước khi sửa dữ liệu. Có thể tìm bằng truy vấn chỉ đọc:

```sql
SELECT id, payment_id, amount, status, provider_refund_id
FROM payment_refunds
WHERE transaction_id IS NULL AND status IN ('PENDING', 'PROCESSING', 'SUCCESS')
ORDER BY requested_at;
```

## 9. Chạy lại kiểm thử tự động

Backend cần Docker hoạt động để chạy PostgreSQL Testcontainers:

```sh
cd hotel-booking
./mvnw -o verify
```

Frontend, mở terminal khác từ gốc repo:

```sh
cd client
npm run test:run -- --maxWorkers=1
npm run build
```

Các ca tích hợp mới nằm trong `BookingOrderIntegrationTest`: chia khoản chuyển và QR còn thiếu; hai xác nhận hoàn đồng thời; xác nhận lặp/thứ tự ngược; dữ liệu cũ thiếu liên kết; backfill hợp lệ/lệch sổ; hết hạn đúng mốc PostgreSQL; trả phòng có phí và rollback khi thu thiếu. Kiểm thử giao diện bổ sung cho QR, số tiền chuyển, tổng thực tế, lịch sử và ẩn QR của đơn đã hủy/hết hạn.

Kiểm thử tự động sử dụng webhook giả lập và cơ sở dữ liệu thử; việc chuyển tiền và hoàn tiền tại ngân hàng thật cần xác nhận riêng khi nghiệm thu tích hợp SePay.

## 10. Kết quả đã chạy ngày 06/11/2026

| Kiểm tra | Kết quả |
|---|---|
| Backend `./mvnw -o verify` | **765/765 passed**, 0 lỗi, 0 bỏ qua; đóng gói và kiểm tra định dạng thành công |
| `BookingOrderIntegrationTest` trên PostgreSQL Testcontainers | **24/24 passed**, có xác nhận hoàn đồng thời, backfill, hết hạn đúng mốc và phí trả phòng |
| Frontend `npm run test:run -- --maxWorkers=1` | **297/297 passed**, 58 file kiểm thử |
| Frontend `npm run build` | Thành công |
| ESLint và Prettier cho các file frontend thay đổi | Thành công |
| `git diff --check` | Không có lỗi khoảng trắng |

Các mục 3–7 là hướng dẫn nghiệm thu để bạn tự chạy trên môi trường của mình; chưa ghi nhận rằng toàn bộ checklist thủ công đã được chạy với ngân hàng thật.
