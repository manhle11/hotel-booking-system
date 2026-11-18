---
documentId: HANBAY-CANCELLATION-POLICY
title: Cancellation Policy
category: CANCELLATION
source: docs/knowledge-base/cancellation-policy.md
version: 2.3
hotel: HANBAY HOTEL
language: vi
---

# HANBAY HOTEL — Chính sách hủy và đổi lịch

## Phạm vi và phiên bản

Gói trực tiếp tiêu chuẩn `HANBAY-STANDARD-2026-11-V2`, áp dụng khi khách đã thấy và chấp thuận tại bước thanh toán. Lưu nội dung, phiên bản, thời điểm đồng ý, ngày nhận gốc và giá đêm đầu gốc P theo từng phòng. Không áp hồi tố: booking thiếu phiên bản giữ điều khoản cũ và cần đối soát. Không tự áp gói tiêu chuẩn cho OTA, hợp đồng đoàn hoặc gói không hoàn có điều khoản khác.

Mốc tính phí theo giờ nhận cấu hình, mặc định 14:00 Asia/Ho_Chi_Minh. P là giá phòng đêm đầu gốc sau giảm giá đã phân bổ, không gồm dịch vụ; P = 0 thì phí bằng 0. Làm tròn xuống đồng VND.

| Thời điểm khách xác nhận hủy / gửi đề nghị đổi đầy đủ | Tổng phí mục tiêu |
| --- | --- |
| Còn ít nhất 48 giờ | 0đ |
| Còn từ 24 đến dưới 48 giờ | 50% P |
| Còn dưới 24 giờ, kể cả sau giờ nhận | 100% P |
| No-show đã được nhân viên xác nhận | 100% P |

Đúng mốc 48 giờ thuộc mức 0đ; đúng mốc 24 giờ thuộc mức 50% P. Hủy muộn sau cutoff vẫn là CANCELLED nếu khách chủ động xác nhận hủy, không gọi là NO_SHOW.

### Căn cứ một đêm đầu tiên khi đơn có nhiều đêm hoặc nhiều phòng

Ở mốc 50%, **phí từng phòng = 50% × giá phòng đêm đầu gốc đã chốt sau giảm giá**.
Các đêm thứ hai trở đi, dịch vụ bổ sung và chênh lệch giá kỳ mới không tham gia căn cứ này.
Không lấy tổng tiền cả kỳ chia số đêm để thay cho snapshot giá đêm đầu, vì giá mỗi đêm có
thể khác nhau. Đơn nhiều phòng cộng phí của các phòng trong phạm vi hủy/đổi; không chọn
giá của một phòng đại diện và không nhân phí thêm với số đêm.

Ví dụ hai phòng có giá đêm đầu gốc 2.450.000đ và 3.600.000đ: phí 50% lần lượt là
1.225.000đ và 1.800.000đ, tổng **3.025.000đ**. Nếu đặt một đêm, tổng giá đêm đầu
6.050.000đ trùng với tiền phòng cả kỳ. Nếu đặt ba đêm cùng đơn giá, tiền phòng cả kỳ
là 18.150.000đ nhưng phí 50% vẫn là **3.025.000đ**, trước khi xét phí đã phát sinh hoặc
quản lý miễn/giảm. Số tiền thanh toán thêm sau đổi còn phụ thuộc tiền kỳ mới và tiền
đã thanh toán, tách riêng với phí hủy/đổi.

## Không thu phí trùng

Lưu F là tổng phí hủy/đổi/no-show còn hiệu lực. Tổng phí sau sự kiện = max(F trước đó, phí mục tiêu), tối đa P mỗi phòng; chỉ ghi thêm phần chênh lệch. Phí đã phát sinh không tự được hoàn khi ngày mới còn trong cửa sổ miễn phí. Đổi có phí rồi khách đến ở: tiền kỳ mới cộng F. Chỉ quản lý được giảm/miễn với lý do; lưu bút toán điều chỉnh, không xóa lịch sử.

## Khách xác nhận hủy

Tra cứu điều kiện chỉ là báo giá. Xác nhận hủy có quyền truy cập mới đóng CANCELLED và giải phóng phần giữ chỗ chưa dùng ngay. Hủy đúng chính sách không cần duyệt lại; lễ tân được hủy cả sát giờ. Với booking cũ hoặc tiền chưa đối soát, đóng booking và chuyển phần quyết toán chờ quản lý; không ghi khoản phạt mới như công nợ đã chấp thuận. Không khôi phục booking do đang chờ hoàn hay khách khiếu nại phí.

Quyết toán theo tiền thực nhận trừ các khoản đã hoàn và đang dành cho hoàn khác. Nghĩa vụ còn lại là phí hợp lệ; bỏ tiền đêm và dịch vụ chưa cung cấp. Hoàn thêm = max(tiền còn bù trừ − nghĩa vụ, 0). Còn thu = max(nghĩa vụ − tiền còn bù trừ, 0). Không xóa công nợ nếu cọc nhỏ hơn phí. Khoản hoàn tạo PENDING, có thể bổ sung tài khoản nhận sau; PENDING không có nghĩa đã chuyển tiền.

## Đổi lịch trước nhận phòng

Đợt triển khai này hỗ trợ toàn booking/toàn đơn, cùng hạng và cùng số đêm, một lần, ngày nhận mới trong 90 ngày từ ngày nhận gốc. Tùy tồn phòng và quản lý xác nhận. Yêu cầu chờ giữ ngày cũ, không bảo đảm phòng kỳ mới hoặc gia hạn cutoff. Khách chấp thuận báo giá trước áp dụng; phí lấy thời điểm đề nghị hợp lệ, không tăng bậc vì nhân viên duyệt muộn. Chỉ ghi phí khi áp dụng thành công.

Giá kỳ mới được tính lại, cộng F. Xác nhận lại dịch vụ chuyển được; nếu không chuyển được, quản lý có thể bỏ toàn bộ dịch vụ chưa dùng trong phạm vi và báo giá lại. Tuổi trẻ em phải nhập lại theo ngày mới. Kiểm tra tồn và cập nhật ngày/phí/giải phóng ngày cũ trong cùng giao dịch. QR số dư cũ bị hủy. Nếu còn thiếu tiền, booking giữ CONFIRMED và lưu hạn thu chênh lệch đã thỏa thuận.

Ngoại lệ vượt một lần/90 ngày hoặc đổi khác số đêm cần quản lý ghi lý do để mở xét duyệt; khách phải chấp thuận lại báo giá. Hệ thống tính và hiển thị báo giá tăng/giảm đêm trước khi quản lý xét ngoại lệ, nhưng không đổi ngày, ghi phí, thu thêm hay tạo hoàn chỉ vì đã có báo giá. Chưa hỗ trợ hủy một phần phòng trong đơn; không giả lập bằng hủy cả đơn. Booking đã nhận phòng dùng chính sách lưu trú, không dùng hủy/no-show trước nhận.

Khách đã xác nhận hủy thì đề nghị đổi lịch đang chờ cùng booking/toàn đơn được kết thúc,
kể cả hủy từ trang khách; không còn quyền áp dụng báo giá cũ. Lịch sử được giữ với lý do
đã kết thúc do hủy. Đổi đã áp dụng trước khi hủy vẫn giữ lịch sử và theo dõi quyết toán
hiện tại trong **Yêu cầu hủy / đổi lịch → Giám sát thu / hoàn**; đã duyệt đổi không đồng
nghĩa khách đã trả đủ hoặc khách sạn đã hoàn. Chỉ khoản hoàn đã đối soát thành công mới
được tính là đã hoàn; yêu cầu hoàn đang chờ vẫn theo dõi riêng.

### Báo giá ngoại lệ thay đổi số đêm trước nhận phòng

Điều khoản V2 đã chấp thuận và P gốc không bị ghi đè. Khi quản lý đồng ý xét ngoại lệ,
khách xác nhận báo giá cụ thể; kết quả lưu căn cứ ngoại lệ, nội dung và phiên bản thỏa thuận
`HANBAY-STAY-CHANGE-2026-11-V1` cùng thời điểm chấp thuận trong lịch sử yêu cầu.

- Dời ngày nhận: báo giá lại toàn kỳ mới theo giá hiện hành, cộng phí mục tiêu 0/50%/100% P gốc theo thời điểm nhận yêu cầu so với giờ nhận của kỳ hiện tại. Vừa dời ngày vừa giảm đêm chỉ áp một mức phí chung này.
- Giữ ngày nhận, tăng số đêm: giữ giá các đêm cũ, đêm thêm theo giá hiện hành; không ghi thêm phí đổi. Phí trước đây vẫn còn hiệu lực.
- Giữ ngày nhận, giảm số đêm: giữ giá các đêm còn lại; bỏ tiền các đêm bị cắt. Phí mục tiêu 0/50%/100% giá đêm đầu bị bỏ theo snapshot, tối đa giá trị phần bỏ và P gốc. Phí trước đây tham gia trần chung; chỉ ghi thêm phần thiếu.
- Dịch vụ vẫn theo số lượng trong báo giá đã được xác nhận chuyển; không tự tăng số lượng chỉ vì tăng đêm. Nếu không chuyển được, báo giá lại sau khi bỏ dịch vụ chưa dùng.

Tổng nghĩa vụ mới = tiền phòng kỳ mới + dịch vụ còn hiệu lực + tổng phí còn hiệu lực.
Tiền được bù trừ = thực nhận − đã hoàn − tiền đang dành cho khoản hoàn khác.
Còn thu = max(nghĩa vụ mới − tiền bù trừ, 0); cần hoàn = max(tiền bù trừ − nghĩa vụ mới, 0).
Tính theo từng phòng rồi cộng lên đơn; không tự dùng tiền thừa của phòng này trả nợ phòng khác.
Giảm số đêm có thể không phát sinh hoàn nếu phí hoặc công nợ đã bù hết phần giảm.

## Booking chưa xác nhận và booking cũ

PENDING hủy giữ chỗ không có phí. Tiền nhận một phần hoặc sau hạn phải đối soát hoàn, không tự mất. Booking cũ chưa chấp thuận V2 không tự nhận căn cứ P mới: luồng tự phục vụ giữ điều khoản cũ (trước 48 giờ hoàn 100%, 24–48 giờ hoàn 50% tổng theo contract cũ; dưới 24 giờ liên hệ nhân viên). Qua quầy, xác nhận hủy giải phóng ngay; phần phí thiếu căn cứ cần quản lý quyết định với bằng chứng, không đánh no-show thay hủy.

Đơn nhiều phòng hiện hủy/đổi toàn đơn khi tất cả phòng chưa nhận; no-show xét từng phòng. Phí và trần tính riêng từng phòng. Không hoàn hai nơi cho cùng khoản tiền từ kênh OTA.

## Hoàn tiền và thông báo

Hoàn theo nguồn thanh toán/cơ chế kênh đã thỏa thuận. Chuyển khoản thủ công cần tên ngân hàng, chủ tài khoản, số tài khoản đã xác minh; quản lý đối soát rồi ghi mã tham chiếu. SLA thông báo: 3–7 ngày làm việc sau khi đủ điều kiện và thông tin nhận, thời gian nhận tiền tùy ngân hàng. Gửi xác nhận qua kênh đã xác minh là tác vụ nhân viên; phần mềm lưu người thao tác, thời điểm, báo giá và kết quả, không tự gửi tin hoặc chuyển tiền.

Nguồn: `UnifiedBookingPolicy`, `BookingChangeService`, `BookingSettlementLedger`, `BookingServiceImpl`, `BookingOrderService`, `ArrivalHandlingService`; [kiểm thử triển khai](../testing/unified-booking-policy.md).
