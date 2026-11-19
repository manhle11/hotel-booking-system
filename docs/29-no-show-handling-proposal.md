# Xử lý khách đến muộn và không đến (no-show)

Ngày chốt: 12/11/2026. Nhánh: `feat/guest-no-show-handling`.

**Trạng thái: đã chốt để triển khai theo yêu cầu ngày 12/11/2026.**
Mã chính sách: `HANBAY-ARRIVAL-2026-11-V1`. Bản triển khai nằm trên nhánh nêu trên;
không đồng nghĩa đã triển khai lên production.

| Tình huống                                                         | Phí chốt áp dụng                                                      |
| ------------------------------------------------------------------ | --------------------------------------------------------------------- |
| Đến muộn và được tiếp nhận trong cửa sổ thông thường               | Phụ thu **0 VND**; giữ tiền kỳ ở đã đặt                               |
| Đến sau cutoff, được quản lý duyệt giữ và tiếp nhận trong kỳ ở gốc | Phụ thu **0 VND**; giữ tiền kỳ ở đã đặt                               |
| Xác nhận không đến                                                 | **100% giá phòng đêm đầu đã chốt**; hoàn phần dư và dịch vụ chưa dùng |
| Quản lý duyệt giảm hoặc miễn phí no-show                           | Từ **0 VND đến mức phí chuẩn**, bắt buộc có lý do                     |

Không tính phí phạt theo từng giờ đến trễ. Chỉ đóng no-show sau cutoff và quyết
định của nhân viên; không tự đóng theo đồng hồ. Quy trình và API:
[hướng dẫn sử dụng và kiểm thử](testing/no-show-handling.md).

## 1. Hiện trạng trước triển khai đã đối chiếu

- Booking chỉ có `PENDING`, `CONFIRMED`, `CHECKED_IN`, `CHECKED_OUT`, `CANCELLED`,
  `EXPIRED`; chưa có `NO_SHOW`.
- Booking `CONFIRMED` được tiếp nhận trong ngày dự kiến và trước cutoff đến muộn
  hôm sau, mặc định 06:00 theo `Asia/Ho_Chi_Minh`. Đúng cutoff, booking chuyển sang
  danh sách chưa đến/quá giờ; trạng thái và tồn kho không tự thay đổi.
- Chính sách hủy hiện tại từ chối hủy khi đã qua cửa sổ hủy trước giờ nhận phòng.
  Không thể dùng nguyên luồng hủy của khách để đóng booking quá giờ.
- Luồng đặt hiện tại trả trước đủ tiền ban đầu để xác nhận. Sau gia hạn, booking
  xác nhận có thể có công nợ. Không được coi mọi khách không đến là khách chưa trả tiền.
- Hoàn tiền hiện có yêu cầu `PENDING`, chuyển khoản thủ công và quản lý xác nhận
  sau đối soát. Tạo yêu cầu hoàn không đồng nghĩa đã chuyển tiền.
- Đơn nhiều phòng thanh toán chung, có phân bổ tiền cho từng booking; khách được
  nhận/trả từng phòng độc lập.

Nguồn nội bộ: [hoàn thiện nhận phòng](testing/check-in-flow-completion.md),
[chính sách booking](knowledge-base/booking-policy.md),
[chính sách thanh toán](knowledge-base/payment-policy.md),
[đơn nhiều phòng](24-multi-room-booking.md), `BookingStatus`,
`HotelStayPolicy`, `StandardBookingCancellationPolicy`.

## 2. Quy trình đã chốt

Sau cutoff, hệ thống đưa booking vào hàng đợi cần xử lý. Lễ tân kiểm tra và liên
hệ khách; sau đó ghi nhận một quyết định rõ ràng. Chốt ca phải rà soát các booking
chưa xử lý, kể cả booking được giữ theo thỏa thuận nhưng đã quá hẹn mới.

| Tình huống                                                    | Cách xử lý đề xuất                                                                                 | Ảnh hưởng phòng và tiền                                                                  |
| ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Khách báo đến muộn, vẫn dùng kỳ ở đã đặt                      | Ghi giờ dự kiến và hạn giữ phòng; quản lý duyệt ngoại lệ nếu vượt cutoff                           | Giữ booking `CONFIRMED`, ngày ở và giá đã thỏa thuận; tiếp tục giữ tồn                   |
| Chưa liên hệ được khách                                       | Ghi ít nhất một lần liên hệ, kênh, thời điểm và kết quả; lễ tân quyết định giữ có hạn hoặc no-show | Chưa xác nhận no-show thì vẫn giữ tồn; không tự đóng chỉ vì cuộc gọi không thành công    |
| Khách xác nhận không đến hoặc không đến sau hạn đã thống nhất | Xem trước quyết toán rồi xác nhận `NO_SHOW`                                                        | Đóng quyền nhận phòng, giải phóng phần giữ chỗ còn lại, ghi phí và khoản phải hoàn riêng |
| Khách muốn chuyển sang kỳ ở khác                              | Quản lý duyệt; tạo booking mới liên kết booking cũ, kiểm tra giá/tồn mới                           | Quyết toán booking cũ theo chính sách áp dụng; tiền và trạng thái booking mới độc lập    |
| Khách đến sau khi đã đóng no-show                             | Kiểm tra phòng và tạo booking mới liên kết                                                         | Không bảo đảm phòng cũ; không tự khôi phục hoặc chuyển tiền từ booking đã đóng           |

### Mốc xác định no-show

- Áp dụng cho booking `CONFIRMED`, chưa có nhận phòng thực tế, từ 06:00 ngày sau
  ngày nhận dự kiến theo cấu hình khách sạn. Không áp dụng cho `PENDING`,
  `CHECKED_IN` hoặc booking đã kết thúc.
- Thỏa thuận giữ phòng còn hiệu lực ngăn xác nhận no-show. Muốn kết thúc thỏa
  thuận sớm cần quản lý ghi lý do và ghi nhận việc thông báo cho khách.
- Hết hạn giữ bổ sung thì quay lại hàng đợi xử lý; không tự đánh no-show hoặc thu phí.
- Thời hạn giữ bổ sung phải cụ thể và không vượt giờ trả phòng của kỳ ở gốc.
  Nếu kỳ ở đã kết thúc, khách cần đặt kỳ mới.
- Nhận muộn ngoài cutoff chỉ được thực hiện sau khi có ngoại lệ được quản lý
  duyệt, còn trong kỳ ở và phòng đáp ứng điều kiện nhận. Lễ tân vẫn là người
  thực hiện check-in. Giữ nguyên ngày ở, ngày trả và giá đã thỏa thuận; không cộng
  thêm phí no-show khi khách được nhận theo ngoại lệ này.
- Ví dụ: đặt 12–15/11/2026, từ 06:00 ngày 13/11 bắt đầu cần xử lý. Khách hẹn
  đến 10:00 ngày 13 và được duyệt giữ phòng thì vẫn có thể được tiếp nhận theo
  ngoại lệ; không dời ngày trả sang 16/11.

## 3. Phí và hoàn tiền đã chốt

Đây là mức phí riêng của HANBAY được chốt cho giai đoạn đầu:

- Phí no-show bằng **giá phòng đêm đầu theo giá đã chốt của booking**, sau các
  điều chỉnh giá đã áp dụng. Không lấy giá bán hiện tại hoặc tổng tiền chia số đêm.
- Không thu tiền các đêm còn lại hoặc dịch vụ chưa cung cấp. Giai đoạn đầu chưa
  hỗ trợ dịch vụ trả trước không hoàn và gói giá không hoàn tiền.
- Phí no-show thay thế nghĩa vụ tiền phòng/dịch vụ chưa sử dụng khi quyết toán;
  không cộng phí này lên toàn bộ tổng tiền lưu trú cũ. Giữ snapshot và lịch sử
  giá gốc, lưu điều chỉnh quyết toán riêng để đối soát.
- Không tự phát sinh thuế hoặc phụ phí mới. Phạm vi này sử dụng số tiền VND đã
  được hệ thống chốt; chính sách xuất hóa đơn/thuế riêng cần đặc tả nếu mở rộng.
- Quản lý có thể miễn hoặc giảm phí kèm lý do. Lễ tân không tự nhập một mức
  khác hoặc đánh dấu tiền chưa nhận là đã thu.

### Nguyên tắc quyết toán

- Đối chiếu phí với tiền thực thu được phân bổ cho đúng booking, các khoản đã
  hoàn và các yêu cầu hoàn đang chờ. Khóa và kiểm tra lại khi xác nhận để không
  dùng cùng một khoản tiền cho hai lần hoàn.
- Nếu số tiền còn có thể sử dụng lớn hơn phí được duyệt: giữ phần phí, tạo yêu
  cầu hoàn phần dư. Nếu thiếu so với phí: ghi công nợ của phí thực tế, không giữ
  công nợ của những đêm hoặc dịch vụ đã được loại khỏi quyết toán.
- Booking vẫn được đóng và giải phóng giữ chỗ khi khoản hoàn/công nợ còn chờ
  xử lý. Việc quyết toán tài chính có trạng thái riêng.
- Thiếu tài khoản nhận hoàn: vẫn ghi nghĩa vụ hoàn và trạng thái chờ bổ sung
  thông tin; không bắt giữ tồn kho để chờ khách cung cấp tài khoản.
- Khoản chuyển thừa đã được dành cho yêu cầu hoàn riêng không được tính hoàn
  lại lần nữa. Tiền mới đến sau khi đóng đi luồng đối soát/hoàn, không khôi phục
  booking hoặc công nợ tiền lưu trú cũ.
- Với đơn nhiều phòng, giới hạn theo phần tiền của từng booking, không lấy toàn
  bộ tiền thanh toán chung để tính hoàn cho một phòng.

Ví dụ chưa có khoản hoàn trước đó: booking ba đêm có giá phòng lần lượt
800.000, 900.000 và 900.000 VND; dịch vụ chưa dùng 200.000 VND. Khách đã trả đủ
2.800.000 VND. Xác nhận no-show theo chính sách chuẩn: phí 800.000 VND, khoản
phải hoàn 2.000.000 VND. Nếu quản lý miễn phí: khoản phải hoàn 2.800.000 VND.

## 4. Trạng thái, tồn kho và phân quyền

- Thêm trạng thái kết thúc `NO_SHOW`, chuyển từ `CONFIRMED` qua thao tác có chủ
  đích. Lưu người xử lý, thời điểm, lý do, lịch sử liên hệ, phiên bản chính sách,
  snapshot phí và kết quả quyết toán. Phân biệt với khách chủ động hủy trước đó.
- Giải phóng các đêm còn lại trong tính tồn theo hạng phòng khi xác nhận. Các
  đêm đã qua chỉ còn ý nghĩa lịch sử, không trở thành tồn có thể bán lại.
- Bỏ gán phòng trước nếu có. Không đổi phòng sang `OCCUPIED` hoặc `DIRTY` vì
  khách chưa ở; giữ đúng trạng thái vận hành thực tế của phòng.
- Lễ tân: liên hệ, đề nghị ngoại lệ, xác nhận no-show đúng chính sách chuẩn.
  Quản lý: xem/xử lý no-show, duyệt giữ vượt cutoff, miễn/giảm phí, xử lý ngoại lệ
  và xác nhận hoàn sau đối soát. Quyền check-in tiếp tục thuộc lễ tân.
- Khách: xem kết quả, phí, tiền phải hoàn và bổ sung tài khoản qua cơ chế xác
  thực/chủ booking hiện có; không được tự đánh no-show hoặc duyệt ngoại lệ.
- Đơn nhiều phòng xử lý từng booking: một phòng no-show không đóng hoặc đổi
  trạng thái lưu trú của phòng khác. Đơn hiển thị tổng hợp số phòng đang ở,
  no-show và chờ đến; không suy trạng thái từ booking đại diện.
- Báo cáo tách số booking no-show, phí no-show, khoản hoàn đang chờ và khoản đã
  hoàn; không tính khách no-show là đã nhận phòng hoặc số đêm thực ở.

## 5. Trải nghiệm xử lý tại quầy

1. Từ tab **Chưa đến / quá giờ**, mở **Xử lý khách chưa đến**.
2. Đối chiếu thông tin khách, ngày ở gốc, tiền đã trả, các khoản hoàn và lịch sử
   liên hệ; hiển thị thỏa thuận giữ phòng nếu còn hiệu lực.
3. Chọn giữ phòng có hạn, xin ngoại lệ nhận muộn hoặc xác nhận không đến. Khi
   khách muốn đổi kỳ ở, chuyển sang quy trình quản lý duyệt booking thay thế.
4. Trước khi xác nhận no-show, hiển thị phí, số tiền phải hoàn/còn phải thu và
   các đêm sẽ được giải phóng. Nhập lý do và xác nhận.
5. Backend khóa và kiểm tra lại trạng thái, thỏa thuận, phiên bản quyết toán,
   tiền và quyền. Nếu đã có người nhận phòng hoặc dữ liệu thay đổi, yêu cầu xem
   lại thay vì áp dụng quyết định cũ.
6. Thành công: bỏ khỏi hàng đợi, cập nhật tồn, hiển thị kết quả và công việc hoàn
   tiền/thu công nợ còn lại. Lỗi mạng phải tra cứu kết quả trước khi gửi lại.

Không yêu cầu khách cung cấp thông tin ngân hàng ngay tại bước đóng no-show.
Luồng bổ sung thông tin và luồng xác nhận hoàn tiếp tục kiểm tra quyền riêng.

## 6. Phạm vi triển khai

### Giai đoạn 1: hoàn thiện vận hành no-show

- Chính sách có phiên bản; công bố trên bước đặt phòng và lưu phiên bản khách
  đã chấp nhận. Booking cũ chưa có điều khoản này phải qua quản lý quyết định,
  không mặc nhiên áp mức phí mới cho đặt phòng đã phát sinh.
- Trạng thái `NO_SHOW`, lịch sử liên hệ, thỏa thuận giữ có hạn và duyệt ngoại lệ
  nhận muộn trong kỳ ở gốc.
- Xem trước/xác nhận phí và nghĩa vụ hoàn; dùng luồng hoàn thủ công đang có,
  bổ sung trường hợp thiếu tài khoản nhận hoàn.
- Booking độc lập và booking con của đơn nhiều phòng, cập nhật tồn và các màn
  lịch sử/chi tiết/báo cáo liên quan.
- Booking thay thế cho khách đến sau khi đóng: tạo và thanh toán theo luồng đặt
  hiện có, có liên kết/lý do nghiệp vụ, tiền cũ và mới được quyết toán độc lập.
- Kiểm thử mốc trước/đúng cutoff, giữ phòng có hạn, quyền, snapshot phí, công nợ,
  hoàn trùng, đơn nhiều phòng và thao tác no-show đồng thời với check-in/webhook.

### Giai đoạn 2: mở rộng nếu có nhu cầu

- Đổi lịch có tính chênh lệch và chuyển tiền đã trả giữa hai booking trong một
  quy trình có kiểm soát; không chuyển một khoản đang được hoàn.
- Khôi phục no-show có kiểm tra tồn và đảo quyết toán rõ ràng.
- Nhiều gói giá/chính sách không hoàn, đồng bộ OTA, nhắc khách và tự động hóa
  chốt ca. Mỗi cơ chế cần điều kiện và chính sách riêng.

## 7. Các quyết định đã chốt

1. Cutoff mặc định 06:00 hôm sau; sau đó xử lý thủ công, có giữ phòng theo thỏa
   thuận và quản lý duyệt ngoại lệ nhận muộn trong kỳ ở.
2. Phí chuẩn bằng tiền phòng đêm đầu; hoàn phần dư và dịch vụ chưa dùng; quản
   lý có quyền miễn/giảm với lý do.
3. Xử lý từng phòng trong đơn chung; đóng booking không phụ thuộc đã hoàn xong.
4. Giai đoạn đầu tạo booking mới nếu đổi kỳ hoặc khách đến sau khi đã đóng;
   chuyển tiền/khôi phục booking là phần mở rộng.
5. Booking cũ chưa chấp nhận chính sách được quản lý xem xét riêng; không tính
   phí mới hàng loạt bằng tác vụ tự động.

## 8. Tài liệu nghiệp vụ tham khảo

Đối chiếu ngày 12/11/2026. Các nguồn mô tả khả năng PMS; mức phí và quy trình
cụ thể trong tài liệu này là quyết định nghiệp vụ riêng đã chốt cho dự án.

- [Oracle OPERA Cloud: Managing No Show and Cancellation Deposits and Charges](https://docs.oracle.com/en/industries/hospitality/opera-cloud/26.2/ocsuh/t_managing_reservations_managing_no_show_and_cancellation_deposits_and_charges.htm):
  xử lý phí, tiền đã nộp và hoàn trên booking no-show.
- [Oracle OPERA Cloud: Reinstating Reservations](https://docs.oracle.com/en/industries/hospitality/opera-cloud/26.2/ocsuh/t_managing_reservations_reinstating_no_show_reservations.htm):
  khôi phục ảnh hưởng lại tới tồn kho và việc gán phòng.
- [Cloudbeds: How Smart Policies Automatically Post Cancellation and No-Show Fees](https://myfrontdesk.cloudbeds.com/hc/en-us/articles/57813669667483-How-Smart-Policies-Automatically-Post-Cancellation-and-No-Show-Fees-in-Cloudbeds-PMS):
  phí theo chính sách, tách ghi phí với thực thu, miễn phí theo quyền và chống
  ghi phí trùng.
