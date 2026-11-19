# Kiểm tra hủy và đổi lịch

Tài liệu này áp dụng bản triển khai chính sách `HANBAY-STANDARD-2026-11-V2`.
Xem [hướng dẫn thao tác trên giao diện và dữ liệu mẫu](booking-policy-ui-walkthrough.md)
để chạy từng ca. Không dùng các mức phần trăm trên tổng booking của tài liệu cũ cho booking V2.

## Chính sách đang áp dụng

P là giá phòng **đêm đầu gốc đã chốt**, sau giảm giá; giữ nguyên qua các lần đổi lịch.
Mốc tính phí là giờ nhận phòng của kỳ hiện tại (mặc định 14:00 Asia/Ho_Chi_Minh),
so với thời điểm hệ thống tiếp nhận yêu cầu, không phải thời điểm quản lý duyệt.

| Thời điểm tiếp nhận hủy/đổi | Mức phí mục tiêu |
| --- | --- |
| Còn ít nhất 48 giờ, kể cả đúng 48 giờ | 0đ |
| Còn từ 24 đến dưới 48 giờ, kể cả đúng 24 giờ | 50% P, làm tròn xuống đồng |
| Còn dưới 24 giờ hoặc đã quá giờ nhận | 100% P |
| Không đến, đủ điều kiện đóng NO_SHOW | 100% P |

Tổng phí còn hiệu lực trong cả chuỗi tối đa P mỗi phòng. Nếu đã ghi phí đổi 400.000đ
với P = 800.000đ, hủy sát giờ chỉ tăng thêm 400.000đ. Hủy sớm kỳ mới vẫn giữ phí đã phát sinh.
Quản lý có thể giảm/miễn với lý do; hệ thống ghi bút toán điều chỉnh, không xóa lịch sử.

### Hai ngày trên lịch chưa chắc đủ 48 giờ

Ví dụ nhận phòng **20/11/2026 lúc 14:00**, yêu cầu được tiếp nhận **18/11/2026 lúc 17:18**
(giờ khách sạn): còn **44 giờ 42 phút**, áp dụng **50% P**.
Miễn phí khi tiếp nhận đến **18/11 lúc 14:00**; mức 50% kéo dài đến **19/11 lúc 14:00**
(kể cả đúng mốc); sau đó mới là 100%. Dời sang ngày nhận mới xa hơn không đổi mốc tính này.

Hai phòng có P = 2.450.000đ và 3.600.000đ, đã trả đủ một đêm 6.050.000đ:
dời sang hai đêm cùng đơn giá có giá kỳ mới 12.100.000đ; phí 50% P = **3.025.000đ**;
cần thanh toán thêm **9.075.000đ** gồm 6.050.000đ tiền tăng đêm và 3.025.000đ phí.

Kiểm tra phần **Căn cứ tính phí** ngay trên báo giá ở cả màn khách và màn quản lý:
thời điểm tiếp nhận, giờ nhận của kỳ hiện tại, thời gian còn lại và ba mốc phí phải theo
múi giờ khách sạn. Máy chủ trả mốc và tỷ lệ, giao diện không tự suy đoán theo ngày lịch.
Làm mới báo giá sau mốc 24 giờ vẫn giữ phí theo lúc gửi. Nếu đã phát sinh phí từ lần trước,
mức 0% của yêu cầu hiện tại không tự xóa phí đó. Giữ ngày nhận và thêm đêm phải ghi rõ
không phát sinh phí đổi; giảm đêm dùng giá đêm đầu bị bỏ theo chính sách riêng.

## Kiểm tra căn cứ phí chỉ có một đêm đầu tiên

- Hai phòng có P gốc 2.450.000đ và 3.600.000đ, thời điểm tiếp nhận còn 30 giờ:
  thử kỳ ở cũ 1, 3 và 7 đêm. Phí từng phòng luôn là 1.225.000đ và 1.800.000đ,
  cộng lại 3.025.000đ, dù giá kỳ mới tăng hoặc số đêm mới thay đổi.
- Thêm dịch vụ hoặc đổi giá các đêm sau: căn cứ P và phí đổi giữ nguyên; các khoản đó
  chỉ làm thay đổi tiền kỳ ở và khoản thu/hoàn. Kiểm tra đêm đầu có giá khác các đêm sau
  để phát hiện việc dùng giá bình quân hoặc toàn kỳ thay snapshot đêm đầu.
- Mở **Cách tính phí** ở màn khách hoặc quản lý để xem bảng **Phí tính trên đêm đầu
  từng phòng**, hiện phép nhân theo P riêng của từng phòng và tổng phí. Mục này mặc định
  đóng; bên ngoài chỉ hiện mức phí ngắn gọn. Khi kỳ cũ
  chỉ có một đêm và không có dịch vụ/phí cũ, giải thích vì sao giá đêm đầu trùng tiền cả kỳ.
- Có phí trước đây thì cột phí chuẩn phải ghi rõ đã đối chiếu phí cũ; bảng này là mức
  theo chính sách, khoản miễn/giảm do quản lý vẫn thể hiện riêng trong tổng phí được duyệt.
- Màn khách mặc định hiện số tiền cần thanh toán thêm/được hoàn và bốn dòng: giá kỳ mới,
  phí đổi, tổng tiền sau đổi, tiền đã trả được bù trừ. Các mốc giờ, căn cứ từng phòng và
  giá từng đêm chỉ hiện khi mở chi tiết. Kiểm tra trên điện thoại không tràn ngang;
  đơn vừa cần thu vừa cần hoàn vẫn hiện riêng cả hai khoản.

## Khách xác nhận hủy

1. Lễ tân mở **Khách đến hôm nay → tra mã → Xử lý khách → Khách báo hủy**.
2. Xem báo giá hủy, ghi căn cứ khách đã xác nhận và xác nhận phạm vi toàn đơn nếu nhiều phòng.
3. Bấm **Khách xác nhận hủy, giải phóng phòng**. Booking chuyển CANCELLED và giải phóng ngay,
   kể cả sau cutoff hoặc đang chờ đổi lịch. Yêu cầu đổi cũ kết thúc.
4. V2: phí được chốt theo chính sách; tiền thực thu còn bù trừ vượt phí tạo refund PENDING.
   Còn thiếu phí thì khách trả qua QR hoặc lễ tân/quản lý ghi nhận tiền mặt thực thu.
5. Booking cũ thiếu căn cứ: vẫn hủy ngay nhưng **chờ quản lý quyết toán**, chưa công bố phí/công nợ.
   Quản lý mở **Yêu cầu hủy / đổi lịch**, đối chiếu điều khoản cũ rồi quyết định.

Khách có tài khoản cũng kiểm tra/hủy tại **Đặt phòng của tôi → chi tiết booking** khi đủ điều kiện.
Hủy khác NO_SHOW; không tạo nhận/trả phòng hoặc chuyển phòng sang DIRTY.

## Khách xin đổi lịch

1. Khách gửi tại chi tiết booking/đơn, hoặc lễ tân ghi tại **Khách xin đổi lịch**.
2. Booking vẫn CONFIRMED và giữ kỳ cũ trong khi chờ. Yêu cầu không tự kéo dài hạn tiếp nhận;
   yêu cầu đang chờ chặn đóng NO_SHOW cho đến khi được xử lý.
3. Quản lý mở `/front-desk/booking-requests`, kiểm tra kỳ mới, tồn phòng, tiền phòng,
   phí, dịch vụ và tuổi trẻ em. Dịch vụ cần xác nhận chuyển; nếu bỏ dịch vụ phải báo giá lại.
4. Khách chấp thuận đúng báo giá hoặc quản lý ghi căn cứ sự đồng ý qua kênh đã xác minh.
   Giá/tồn/phạm vi thay đổi cần xem và chấp thuận lại.
5. Duyệt cập nhật toàn đơn trong một giao dịch, giữ mã và CONFIRMED, bỏ gán phòng cũ.
   Quy tắc chuẩn: cùng hạng, cùng số đêm, một lần, ngày nhận mới từ ngày nhận gốc đến +90 ngày.
   Ngoại lệ số lần/90 ngày/số đêm cần quản lý ghi lý do và khách chấp thuận báo giá mới.
   Đổi số đêm có báo giá dự kiến ngay cả khi chưa xét ngoại lệ; chưa hỗ trợ đổi một phần phòng trong đơn.
6. QR cũ đóng. Thiếu tiền tạo QR số dư mới, lưu hạn thu đã thỏa thuận; dư tiền tạo hoàn chênh lệch.
   Hết hạn QR có thể tạo mã mới; mở lại khi mã còn hạn dùng lại cùng mã.
   Đơn nhiều phòng: tại **Tóm tắt đơn**, chọn **Thanh toán toàn bộ** để dùng một QR cho
   tổng khoản còn thiếu; vẫn có nút thanh toán riêng từng phòng. Chọn cách thanh toán khác
   đóng các mã chờ cũ và giữ tiền đã nhận. Tiền đến mã cũ đi đối soát/hoàn, không ghi thu trùng.
7. Khách đang mở chi tiết phải thấy ngày/giá/điều kiện hủy cập nhật theo kỳ mới.

Tiền đã hoàn hoặc dành cho refund đang chờ không được dùng lại ở lần hủy, no-show hay trả sớm sau đó.
Ví dụ kỳ mới 300.000đ, tiền còn bù trừ 300.000đ, P gốc 800.000đ:
hủy sát giờ/no-show vẫn chốt phí 800.000đ, còn phải thu 500.000đ.

## Kiểm tra form xem xét yêu cầu

- Mở **Yêu cầu hủy / đổi lịch → Xem xét yêu cầu**. Form chia thành kỳ ở cũ/mới,
  báo giá và thu/hoàn, xác nhận và quyết định; nút đóng/từ chối/duyệt nằm ở chân hộp thoại.
- Mỗi phòng hiện ngày theo `DD/MM/YYYY`, số đêm và mức tăng/giảm. Yêu cầu khác số đêm phải
  có báo giá dự kiến nếu đủ dữ liệu giá/thanh toán (`pricingAvailable = true`), dù vẫn chờ
  quản lý xét ngoại lệ (`canApprove = false`). Quản lý ghi lý do rồi chọn **Xét ngoại lệ thay
  đổi số đêm và báo giá lại**; khách phải đồng ý báo giá mới. Thiếu snapshot/tiền chưa đối
  soát thì không lấy tổng cũ làm giá kỳ mới; hiển thị lý do chưa tính được.
- Báo giá tách phí trước đây, phí phát sinh hoặc giảm lần này, tổng phí sau xử lý và
  tiền khách còn được bù trừ. Mở **Chi tiết giá từng đêm và căn cứ phí theo phòng** để
  đối chiếu căn cứ đêm đầu gốc và mức phí được phân bổ cho từng phòng.
- Chỉ mở ô sửa phí chuẩn khi chọn **Điều chỉnh hoặc miễn phí**. Booking cần quản lý xác định
  phí theo điều khoản cũ phải nhập rõ mức phí, kể cả `0`; chưa nhập thì chưa có quyết toán.
- Chỉ yêu cầu hạn thu tương lai khi đổi lịch còn thiếu tiền; ngày giờ nhập theo múi giờ khách sạn.
  Chỉ hiện xác nhận chuyển dịch vụ và ô tuổi trẻ em khi phòng có các thông tin tương ứng.
- Khách phải được đối chiếu chấp thuận báo giá trước duyệt đổi. Sửa mức phí hoặc cập nhật
  báo giá phải xác nhận lại; không tái sử dụng sự đồng ý của báo giá trước.
- Đơn có phòng dư tiền và phòng còn thiếu phải hiện riêng **Cần hoàn cho khách** và
  **Còn phải thu**, không tự bù trừ giữa các phòng. Kết quả duyệt là khoản hoàn được tạo
  và khoản thiếu tại thời điểm áp dụng; không thay cho trạng thái đã chuyển/đã thu tiền.

### Đối chiếu các khoản thu / hoàn khi thay số đêm

Giả sử ba phòng đã trả đủ 14.900.000đ cho 2 đêm, giá mỗi đêm là 2.450.000đ,
2.500.000đ, 2.500.000đ; không có dịch vụ, chưa có phí, không có hoàn trước đó.
Dời sang kỳ mới có giá mỗi đêm giữ nguyên:

| Số đêm mới | Phí mục tiêu | Giá kỳ mới | Tổng nghĩa vụ mới | Còn phải thu | Cần hoàn |
| --- | --- | --- | --- | --- | --- |
| 3 đêm | 100% P = 7.450.000đ | 22.350.000đ | 29.800.000đ | 14.900.000đ | 0đ |
| 1 đêm | 100% P = 7.450.000đ | 7.450.000đ | 14.900.000đ | 0đ | 0đ |
| 1 đêm | 50% P = 3.725.000đ | 7.450.000đ | 11.175.000đ | 0đ | 3.725.000đ |
| 1 đêm | 0đ | 7.450.000đ | 7.450.000đ | 0đ | 7.450.000đ |

Màn khách tự tải báo giá của yêu cầu đang chờ, đặt khoản **Bạn cần thanh toán thêm** /
**Bạn được hoàn** lên đầu phần đổi lịch ở vùng nội dung chính. Tóm tắt đơn cũ vẫn là số
tiền đã chốt trước khi áp dụng. Kiểm tra cả giá kỳ mới thay đổi theo ngày và trường hợp
giữ nguyên ngày nhận: thêm đêm giữ snapshot cũ, không thêm phí đổi; giảm đêm xét phần bỏ.

## Thu phí và hoàn tiền

- Booking đóng, đã quyết toán và còn nợ: khách bấm **Thanh toán phí còn thiếu**;
  lễ tân/quản lý mở **Xử lý khách**, nhập tiền mặt, ghi chú và xác nhận đã thực nhận.
- Mỗi lần thu có mã chống ghi trùng. Mất phản hồi thì gửi lại cùng xác nhận để tra kết quả;
  tiền chuyển QR đến sau khi đã thu đủ được ghi nhận để hoàn, không thu lần hai.
- Khách bổ sung ngân hàng ở thẻ quyết toán. Quản lý đối soát, chuyển khoản bên ngoài rồi
  xác nhận tại `/payment-monitoring/refunds`. PENDING chưa phải đã chuyển tiền.
- Thu phí không khôi phục CANCELLED/NO_SHOW hoặc giữ phòng trở lại.

## Điều kiện đạt

### Yêu cầu đổi còn lại sau khi khách hủy

- Khách hủy booking hoặc toàn đơn từ trang khách, hoặc lễ tân ghi nhận hủy: đề nghị đổi
  `PENDING` cùng phạm vi phải kết thúc trong cùng giao dịch; không được áp dụng lại bằng
  báo giá/token cũ. Lưu `REJECTED` kèm `snapshot.autoClosed = true` và
  `closureReason = BOOKING_CANCELLED`; giao diện hiển thị **Đã kết thúc do hủy**.
- Danh sách **Chờ xử lý** không còn dòng đó. Chọn **Đã từ chối / kết thúc** hoặc **Tất cả**
  để tra lại người yêu cầu, ngày đề nghị và lý do tự kết thúc; không xóa lịch sử.
- Migration `V202611181700__close_reschedule_requests_for_cancelled_bookings.sql` xử lý
  các đề nghị treo do luồng hủy cũ, giữ nguyên báo giá/căn cứ cũ trong snapshot.

### Giám sát thanh toán và hoàn tiền

1. Mở **Yêu cầu hủy / đổi lịch → Giám sát thu / hoàn**. Mỗi booking/toàn đơn có một dòng
   theo lần đổi đã áp dụng gần nhất; yêu cầu chưa duyệt nằm ở **Xử lý yêu cầu**.
2. Tìm bằng mã đơn hoặc mã booking bất kỳ trong đơn. Danh sách có phân trang phía server,
   tự cập nhật mỗi 10 giây và có nút **Làm mới thu / hoàn**.
3. Cột **Khách thanh toán**: **Đã thanh toán đủ**, **Còn thiếu tiền**, **Chưa thanh toán**,
   **Quá hạn thu** hoặc **Cần đối soát**. Hạn xét theo giờ khách sạn; đúng hạn mà còn nợ
   là quá hạn. Tiền đã dành hoàn không được dùng lại để kết luận trả đủ.
4. Cột **Khách sạn hoàn tiền** tách số **Đã hoàn** và **Chờ hoàn**. Chỉ refund `SUCCESS`
   sau đối soát ngân hàng mới tính đã hoàn; refund `PENDING/PROCESSING` vẫn là đang chờ.
   Có thể đồng thời còn nợ ở một phòng và chờ hoàn ở phòng khác.
5. **Chi tiết thu / hoàn** hiển thị nghĩa vụ hiện tại, thực nhận, tiền còn bù trừ, công nợ
   từng phòng, các khoản hoàn và mã đối soát/thời điểm đã hoàn; hai khoản thu thêm/hoàn
   tại thời điểm duyệt được giữ riêng để so sánh lịch sử với số liệu hiện tại.
6. Quản lý mở **Mở quản lý hoàn tiền** để đối soát/chuyển tiền bên ngoài rồi xác nhận ở
   luồng hoàn hiện có. Lễ tân được xem nhưng không có các nút quản lý tài chính.
7. Đơn hủy sau đổi vẫn có lịch sử và thông báo **Booking / đơn đã hủy sau khi đổi lịch**;
   công nợ/hoàn tính theo quyết toán sau hủy. Không dùng số thu/hoàn lúc duyệt cũ làm số
   tiền hiện tại. Hạn thu của kỳ đã hủy không tự áp làm hạn thu phí hủy mới.

API: `GET /api/v1/front-desk/booking-change-requests/settlements?keyword=&page=0&size=20`
và `GET /api/v1/front-desk/booking-change-requests/{id}/settlement`; chỉ
`RECEPTIONIST`/`MANAGER` được truy cập. Các endpoint giám sát không ghi đã thu/đã hoàn,
không tự hủy quá hạn, không tạo phí mới và không chuyển tiền ngân hàng.

- Đơn ví dụ đã trả 14.900.000đ, sau đổi tổng 29.800.000đ: nút thanh toán chung phải tạo QR
  14.900.000đ, phân bổ 4.900.000đ + 5.000.000đ + 5.000.000đ. Chuyển đủ một lần cập nhật cả ba phòng.
- Chuyển một phần rồi mở lại dùng cùng mã nếu còn hạn, QR chỉ hiển thị số chưa trả. Phòng đã
  trả riêng không bị thu lại trong mã chung. Kiểm tra mã riêng đến muộn sau khi tạo mã chung
  và chiều ngược lại; số tiền phòng không vượt nghĩa vụ thực tế, phần dư được dành hoàn.
- Tạo QR chung đồng thời chỉ có một mã đang chờ được dùng; hết hạn tạo mã mới. Hủy/đổi tiếp
  hoặc hoàn tiền sau thanh toán chung vẫn đối chiếu đúng các khoản phân bổ từng phòng.

Kiểm tra cả booking đơn/toàn đơn, giảm/tăng giá, hết tồn, yêu cầu trùng, quyết định đồng thời,
thu từng phần, QR hết hạn, hủy khi chờ đổi, và trả sớm sau khi đã hoàn chênh lệch.
Owner/token phải đúng; lễ tân không được duyệt đổi, quyết toán ngoại lệ hoặc giảm phí.
Các tác vụ chuyển tiền ngân hàng và thông báo khách vẫn do nhân viên thực hiện.
