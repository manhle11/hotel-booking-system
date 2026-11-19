# Tự kiểm tra chính sách booking trên giao diện

## 1. Chuẩn bị

1. Khởi động backend bản mới để Flyway chạy đủ migration, gồm
   `V202611131000__unified_booking_policy.sql` và
   `V202611171100__align_no_show_fee_cap_with_original_policy.sql`. Không reset database để thử chức năng.
2. Khởi động frontend. Dùng ba hồ sơ trình duyệt cho **khách**, **RECEPTIONIST**, **MANAGER**.
   Tài khoản ADMIN quản trị tài khoản, không thay vai trò lễ tân/quản lý trong nghiệp vụ này.
3. Thời gian trong các ca dưới theo **Asia/Ho_Chi_Minh**, giờ nhận mặc định 14:00,
   cutoff nhận muộn mặc định 06:00 hôm sau. Nếu cấu hình khác, dùng hạn thực tế giao diện hiển thị.
4. Mỗi ca hủy/no-show dùng booking riêng. Booking đã đóng không khôi phục; khách đến lại cần kỳ ở mới.

Các màn cần mở:

| Vai trò | Màn hình |
| --- | --- |
| Khách | `/account/bookings` → chi tiết booking; đơn nhiều phòng ở `/booking-orders/{orderId}` |
| Lễ tân/quản lý | `/front-desk/arrivals/today` → ô tra cứu mã → **Xử lý khách** |
| Quản lý | `/front-desk/booking-requests` — quyết định đổi/đối soát booking cũ |
| Lễ tân | `/front-desk/check-in` và `/front-desk/departures/today` |
| Quản lý | `/payment-monitoring/refunds` — đối soát và xác nhận hoàn |

### Dữ liệu mẫu tùy chọn cho database local

Script [seed-booking-policy-qa.sql](../../scripts/seed-booking-policy-qa.sql) chỉ **thêm** một hạng QA,
tám phòng vật lý, chín booking và chứng từ **giả lập**, không sửa/xóa dữ liệu hiện có.
Chỉ chạy trên database local `hotelbooking_db`. Đăng ký tài khoản khách
`qa-policy@example.test` trước; hoặc thay email ở đầu script bằng tài khoản khách thử của bạn.
Không chạy seed trên database vận hành: chứng từ giả lập sẽ xuất hiện trong báo cáo tài chính.

Từ thư mục gốc, nếu dùng PostgreSQL của Docker Compose:

```sh
docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < scripts/seed-booking-policy-qa.sql
```

Cũng có thể chạy toàn bộ file trong SQL editor kết nối database local.
Script in mã dạng `QP...-FREE`, `QP...-DEBT` và ngày đổi gợi ý. Chạy lại tạo bộ mã mới.
Ngày mẫu tính theo ngày chạy; nên chạy và kiểm tra cùng ngày.

| Hậu tố mã | Mục đích | Giá trị đã chốt |
| --- | --- | --- |
| FREE | Hủy trước hơn 48 giờ | 3 đêm, tổng/đã trả 2.400.000đ; P 800.000đ |
| HALF | Hủy trong 24–48 giờ | 3 đêm, tổng/đã trả 2.400.000đ; P 800.000đ |
| LATE | Đến muộn, xin ngoại lệ sau cutoff | Đến từ hai ngày trước, chưa hết kỳ ở |
| NOSHOW | Không đến sau cutoff | Tổng/đã trả 2.400.000đ; P 800.000đ |
| DEBT | Phí lớn hơn kỳ mới sau đổi rẻ | Tổng/tiền còn bù trừ 300.000đ; P gốc 800.000đ |
| LEGACY | Booking cũ chưa chấp thuận V2 | Tổng/đã trả 2.400.000đ, thiếu điều khoản V2 |
| REPRICE | Đổi sang kỳ rẻ hơn | 3 đêm, tổng/đã trả 2.400.000đ |
| BALANCE | Đổi tăng giá, đổi QR hết hạn | Giá cũ 400.000đ/đêm, tổng 1.200.000đ |
| EARLY | Trả sớm sau hoàn chênh lệch | Kỳ hiện tại 300.000đ; thực thu 2.400.000đ, đã hoàn 2.100.000đ |

Các booking mẫu đều CONFIRMED, chưa gán phòng. Giá gốc P lưu trong booking không đổi theo bảng giá hiện tại.
Đổi kỳ mới vẫn dùng engine tính giá thực tế: nếu có quy tắc mùa/ngày lễ toàn hệ thống,
dùng **báo giá hiển thị** để đối chiếu thay vì mặc định các con số ví dụ.

## 2. Khách chấp thuận điều khoản và hủy sớm

Với booking mới tạo qua giao diện: chọn phòng → tạo booking/đơn → xem P, mốc 48/24 giờ,
trần phí → tích đồng ý điều khoản → tạo QR. Khi chưa tải được chính sách hoặc chưa đồng ý,
chưa được mở thanh toán. Có thể kiểm tra bước này mà không chuyển tiền.

Với mẫu FREE đã xác nhận:

1. Khách mở **Đặt phòng của tôi → FREE → Hủy đặt phòng** và xem báo giá.
2. Kỳ vọng phí 0đ, hoàn 2.400.000đ. Nhập thông tin nhận hoàn nếu được yêu cầu rồi xác nhận.
3. Booking CANCELLED, tiền hoàn PENDING, giữ chỗ giải phóng. Reload vẫn một khoản hoàn.
4. Khách khác không xem/hủy được booking này. Nếu thử đường dẫn bằng tài khoản khác phải bị từ chối.

## 3. Hủy trong 24–48 giờ

Lễ tân tra HALF → **Xử lý khách → Khách báo hủy** → xem phí → ghi nội dung khách xác nhận
→ **Khách xác nhận hủy, giải phóng phòng**.

Kỳ vọng CANCELLED ngay, phí **400.000đ = 50% P**, hoàn 2.000.000đ PENDING.
Không dùng 50% tổng 2.400.000đ. Mẫu HALF chọn ngày để nằm trong cửa sổ này lúc seed;
nếu thử lâu sau đó, xem lại giờ còn lại trước khi đối chiếu.

## 4. Đến muộn và xin nhận sau hạn

1. Tra LATE → **Cập nhật giờ đến**. Lưu giờ dự kiến hợp lệ trong kỳ ở.
2. Kiểm tra giờ hẹn vừa nhập không tự thay **hạn tiếp nhận**; booking vẫn CONFIRMED.
3. Lễ tân **Xin nhận sau hạn**, chọn giờ tương lai trước giờ trả phòng gốc, nhập lý do.
4. Quản lý mở cùng booking → **Duyệt / từ chối nhận muộn** → duyệt hạn cụ thể.
5. Trong hạn được duyệt: nhận phòng được nếu đủ phòng/giấy tờ/công nợ; nút no-show bị chặn.
   Phụ thu đến muộn 0đ, ngày trả và tiền kỳ ở không tự đổi.
6. Có thể kiểm tra quản lý **Kết thúc hạn nhận muộn** với lý do và đã thông báo khách.
   Không tạo check-in giả để xử lý khách chưa đến.

## 5. Không đến và hoàn tiền

1. Tra NOSHOW, không có yêu cầu/ngoại lệ đang hiệu lực → **Xử lý không đến**.
2. Nhập kênh liên hệ, kết quả không liên lạc được/chưa quyết định, ghi chú và lý do.
3. Xem trước: phí 800.000đ, hoàn 1.600.000đ. Xác nhận.
4. Kỳ vọng NO_SHOW, giải phóng giữ chỗ; phòng vật lý vẫn AVAILABLE,
   không phát sinh nhận/trả phòng hoặc DIRTY.
5. Khách mở chi tiết → thẻ **Quyết toán khách không đến** → bổ sung ngân hàng.
6. Quản lý mở **Yêu cầu hoàn tiền**. Chỉ xác nhận đã hoàn sau khi thực sự đối soát/chuyển tiền
   trong môi trường thử. PENDING và SUCCESS phải hiển thị khác nhau; reload không tạo khoản thứ hai.

## 6. Phí 800.000đ nhưng chỉ còn 300.000đ: thu 500.000đ

1. Tra DEBT. Chọn **Khách báo hủy** hoặc **Xử lý không đến** (dùng bộ seed khác để thử cả hai).
2. Kỳ vọng đóng thành công, phí **800.000đ**, còn phải thu **500.000đ**, không có hoàn mới.
   Đây là ca kiểm tra P gốc lớn hơn tổng kỳ mới.
3. Lễ tân mở lại **Xử lý khách** → **Tiền mặt phí đã nhận**, nhập 200.000đ,
   ghi số phiếu/người nộp, tích **Đã nhận đủ số tiền mặt nhập ở trên** → ghi nhận.
4. Kỳ vọng còn 300.000đ. Đóng/mở lại vẫn 300.000đ. Thu tiếp 300.000đ thì còn 0đ và nút thu biến mất.
5. Chưa tích thực nhận/thiếu ghi chú thì nút thu khóa. Không thu vượt công nợ.
   Nếu mất phản hồi, dùng **Tra cứu / gửi lại xác nhận thu** để gửi đúng phiếu cũ, không nhập phiếu mới.
6. Trên một bộ DEBT khác, khách mở chi tiết → **Thanh toán phí còn thiếu** → QR đúng 500.000đ
   (hoặc số dư sau thu từng phần). Đây là QR **thu phí**, không phải đặt lại phòng.
7. Mở lại khi QR còn hạn phải về cùng mã; hết hạn tạo được mã mới. Booking vẫn CANCELLED/NO_SHOW.
   Không cần chuyển tiền thật để kiểm tra tạo/đổi QR; webhook chuyển thiếu/thừa/trễ được kiểm tra tự động.

## 7. Booking cũ: hủy ngay, chờ quản lý xác định phí

1. Lễ tân tra LEGACY → **Khách báo hủy** → ghi xác nhận và hủy.
2. Kỳ vọng CANCELLED và giải phóng ngay, cảnh báo **chờ quản lý đối soát theo điều khoản cũ**.
   Chưa có nút thu/QR phí và chưa kết luận tiền phải hoàn.
3. Quản lý vào **Yêu cầu hủy / đổi lịch** → mở yêu cầu → nhập phí có căn cứ và lý do → chốt quyết toán.
4. Sau đó mới hiện khoản hoàn/phải thu xác định. Thử gửi lại/mở lại để kiểm tra không ghi trùng.
5. Với một LEGACY khác, thử gửi đổi trước rồi báo hủy: yêu cầu đổi phải kết thúc,
   booking vẫn đóng ngay; không bị giữ CONFIRMED chỉ vì chờ quản lý chốt phí.

## 8. Đổi rẻ, đổi tăng giá và chấp thuận báo giá

**REPRICE:** khách gửi đổi sang ngày `cheap_new_arrival` mà seed in, giữ đúng ba đêm.
Trước duyệt, kỳ cũ vẫn giữ. Quản lý mở yêu cầu, xem giá mới, xác nhận dịch vụ/tuổi trẻ em
nếu có; khách chấp thuận đúng báo giá hoặc quản lý ghi căn cứ đã đồng ý rồi duyệt.

Kỳ vọng cùng mã, CONFIRMED, cập nhật cả ngày/giá/điều kiện hủy trên trang khách đang mở.
Nếu giá mới 300.000đ thì tạo hoàn 2.100.000đ; P gốc vẫn 800.000đ.
Đã hoàn hoặc đang dành hoàn không được dùng lần nữa. Lần đổi chuẩn thứ hai bị chặn;
ngoại lệ số lần/90 ngày cần quản lý ghi lý do và khách chấp thuận lại.

**BALANCE:** đổi sang `higher_new_arrival`, đúng ba đêm. Với giá mới 800.000đ/đêm,
tổng mới 2.400.000đ và thu thêm 1.200.000đ. Xem hạn thu đã thỏa thuận; booking vẫn CONFIRMED.
Khách tạo QR chênh lệch, mở lại lúc còn hạn (cùng mã), đợi hết hạn rồi tạo lại (mã khác, đúng số dư).
QR cũ không được dùng để tự xác nhận kỳ mới. Không đánh no-show trước ngày đến mới.

Nếu giá/tồn đổi trước lúc duyệt, phải báo xem lại; chưa được áp quyết định dựa trên báo giá cũ.
Để thử dịch vụ/trẻ em, tạo booking qua giao diện vì bộ seed không thêm các thành phần này.

## 9. Trả sớm sau khi đã hoàn chênh lệch

1. Lễ tân mở EARLY ở màn **Nhận phòng**, chọn một phòng QA AVAILABLE, hoàn tất giấy tờ.
   Trước 14:00 cần căn cứ nhận sớm theo kiểm tra trên màn hình.
2. Mở **Khách trả phòng** `/front-desk/departures/today` → tìm EARLY → **Trả phòng sớm**,
   chọn ngày trả hôm nay, xem báo giá, nhập lý do và ngân hàng nhận hoàn.
3. Với ba đêm chưa dùng × 100.000đ, phí trả sớm 20% = 60.000đ, hoàn mới **240.000đ**.
   Nếu thử ngày khác, đối chiếu số đêm chưa dùng thực tế trên báo giá.
4. Xác nhận: CHECKED_OUT, phòng DIRTY; khoản hoàn cũ 2.100.000đ giữ nguyên,
   thêm hoàn mới 240.000đ PENDING. Không báo thiếu payment SUCCESS khi payment cũ PARTIALLY_REFUNDED.

## 10. Đơn nhiều phòng, phân quyền và trợ lý chính sách

- Tạo đơn hai phòng qua giao diện, thanh toán thử theo cấu hình môi trường.
  Hủy/đổi áp dụng **toàn đơn**; một phòng đã nhận/đóng thì không áp yêu cầu đổi toàn đơn cũ.
  Giờ hẹn/liên hệ/nhận muộn có phạm vi các phòng được chọn; no-show và thu phí theo từng phòng.
  Thu/hoàn phòng A không sử dụng tiền phân bổ phòng B.
- Lễ tân xem nhưng không duyệt đổi, giảm/miễn phí hay quyết toán ngoại lệ booking cũ.
  Quản lý thao tác được với lý do. Khách/token khác không xem/chấp thuận được báo giá.
- Trong trợ lý chính sách, hỏi “Hủy trước 30 giờ tính phí thế nào?” và “Đổi lịch rồi không đến thì sao?”.
  Kỳ vọng trả theo P gốc và trần P, có nguồn cancellation/no-show. Bước này cần cấu hình RAG/model đang hoạt động;
  kiểm thử tự động dùng embedding/model kiểm thử, không xác nhận chất lượng provider bên ngoài.

Hoàn tiền qua ngân hàng, thông báo khách và xác minh đồng ý qua điện thoại vẫn là tác vụ của nhân viên.
Chưa hỗ trợ đổi khác số đêm/hạng hoặc hủy/đổi một phần đơn trong luồng này.

## 11. Kết quả kiểm tra tự động ngày 18/11/2026

- Toàn bộ backend: **1.055 test đạt, 0 lỗi, 0 bỏ qua**; PostgreSQL riêng qua Testcontainers.
- Toàn bộ frontend: **481 test đạt trong 77 file** (`--testTimeout=15000`).
- Chromium gọi backend HTTP thật: hủy booking còn thiếu phí, owner tạo QR đúng 500.000đ,
  thu tiền mặt 200.000đ + 300.000đ, giữ trạng thái đã đóng, no-show và tạo hoàn đúng phân bổ.
  Các ca còn lại được kiểm tra bằng integration/API/component test; không phải mọi ca trong tài liệu đều đã chạy bằng trình duyệt.
- TypeScript/Vite build, ESLint, Prettier, Spotless và kiểm tra whitespace đạt.
- Seed QA được chạy lặp lại trong database tạm; không chạy trên database local đang dùng của bạn.
  Chuyển khoản/ngân hàng trong test là giao dịch giả lập, không xác nhận kết nối thanh toán ngoài thực tế.

Để chạy lại cả browser test, cần Docker, Node dependencies và Chromium của Playwright đã cài:

```sh
# Từ thư mục client
npm run build
npm run test:run -- --testTimeout=15000
npm run lint
npm run format:check

# Từ thư mục hotel-booking
./mvnw test -Dreview.e2e=true -Dbooking.policy.e2e=true
```

Browser test dùng các cổng frontend 5181/5182, backend cổng ngẫu nhiên và database tạm.

Log full suite có cảnh báo native stream từ tiến trình Node của browser test và timeout
shutdown JVM 30 giây khi đóng Hikari; Maven kết thúc exit code 0, BUILD SUCCESS sau khi
đủ 1.055 test hoàn tất. Đây là cảnh báo vòng đời test runner, không phải ca nghiệp vụ bị lỗi.
