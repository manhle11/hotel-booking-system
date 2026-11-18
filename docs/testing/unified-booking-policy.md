# Kiểm thử chính sách hủy, đổi lịch, đến muộn và không đến

Triển khai ngày 13/11/2026. Phiên bản `HANBAY-STANDARD-2026-11-V2` chỉ được gắn khi khách chủ động chấp thuận trên bước thanh toán. Migration không gán phiên bản hay căn cứ phí cho booking cũ.

Xem [luồng tự kiểm tra trên giao diện và dữ liệu QA](booking-policy-ui-walkthrough.md).

- Trước thanh toán: từng phòng hiển thị P sau giảm giá, hạn miễn phí, hạn mức 50%, phí 100%, trần phí và giới hạn đổi. Không mở thanh toán khi chưa tải được chính sách.
- Đúng 48 giờ: 0đ; sau 48 giờ một nano giây: 50% P; đúng 24 giờ: 50% P; sau đó: 100% P. Làm tròn xuống đồng. P = 0 vẫn tính 0.
- Khách xác nhận hủy tại quầy: CANCELLED và giải phóng ngay, kể cả sát giờ hoặc sau cutoff. Gửi lại cùng xác nhận không ghi trùng phí/hoàn. Hủy đã xác nhận không được chuyển NO_SHOW.
- Booking cũ qua quầy: đóng CANCELLED, tổng phí chưa xác định không ghi công nợ. Quản lý xử lý phần quyết toán, có căn cứ điều khoản cũ. Hạn phí lấy thời điểm tiếp nhận, không lấy lúc duyệt.
- Khách xác nhận hủy khi đang chờ đổi: kết thúc đề nghị đổi, đóng CANCELLED và giải phóng phòng ngay, kể cả booking cũ và toàn đơn nhiều phòng. Phần quyết toán booking cũ vẫn chờ quản lý; gửi lại cùng xác nhận không tạo yêu cầu trùng.
- Khách gửi đổi: giữ ngày cũ. Khách xem và chấp thuận báo giá; quản lý cũng có thể ghi nhận sự đồng ý qua kênh xác minh với căn cứ trong lý do. Quote thay đổi thì phải chấp thuận lại.
- Đổi chuẩn: cùng hạng, cùng số đêm, tối đa một lần, ngày nhận mới từ ngày nhận gốc đến hết 90 ngày. Ngoại lệ số lần/90 ngày/số đêm cần quản lý ghi lý do và khách chấp thuận lại báo giá. Tăng/giảm đêm được tính giá và thu/hoàn trước khi xét ngoại lệ; `pricingAvailable` khác quyền `canApprove`. Chưa hỗ trợ đổi/hủy một phần phòng trong đơn.
- Dịch vụ chuyển được cần xác nhận lại. Nếu không chuyển được, quản lý có thể bỏ toàn bộ dịch vụ chưa dùng của phạm vi yêu cầu và báo giá lại; khách cần chấp thuận lại. Tuổi trẻ em nhập lại theo ngày mới.
- Thiếu tiền sau đổi: giữ CONFIRMED, lưu hạn thu đã thỏa thuận; QR cũ bị hủy, tạo mã thu số dư mới. Không đánh no-show trước ngày đến mới.
- Đổi 30 giờ trước ngày đến với P = 800.000đ: F = 400.000đ. Hủy trước kỳ mới 5 ngày: giữ F; hủy sát giờ/no-show: F = 800.000đ, chỉ tăng 400.000đ.
- Đổi sớm sang kỳ rẻ hơn với P gốc = 800.000đ, kỳ mới = 300.000đ, thực thu còn bù trừ = 300.000đ: hủy sát giờ/no-show vẫn ghi phí 800.000đ và còn thu 500.000đ. Áp dụng cho hủy tự phục vụ, hủy tại quầy và từng phòng trong toàn đơn; không lấy tiền hoàn bằng 0 để suy ngược trần phí. No-show vẫn đóng và giải phóng tồn phòng thành công.
- No-show sau cutoff, hết ngoại lệ, không còn yêu cầu chờ: ghi liên hệ, đóng từng phòng, bỏ nghĩa vụ đêm/dịch vụ chưa dùng, giữ tổng phí tối đa P. Không check-in/check-out giả hoặc làm phòng DIRTY.
- Quản lý giảm phí: bắt buộc lý do; ghi bút toán âm trong `booking_policy_fees`, không sửa/xóa bút toán cũ. Tiền hoàn mới ở PENDING; không tái sử dụng phần tiền đã hoàn/đang dành hoàn.
- Booking CANCELLED/NO_SHOW đã quyết toán và còn thiếu phí: khách tạo QR từ thẻ quyết toán;
  lễ tân/quản lý ghi nhận tiền mặt thực thu, từng phần hoặc đủ số dư, không thu vượt và không ghi trùng.
  Booking cũ đang chờ quyết toán chưa được thu; thu tiền không khôi phục trạng thái/giữ phòng.
- QR bổ sung/thu phí còn hạn được dùng lại; hết hạn tạo được mã mới không lỗi unique key.
  Tiền vào QR phí đã đóng/hết hạn được dành hoàn, không làm tăng khoản thu áp dụng lần hai.
- Trả sớm sau hoàn chênh lệch dùng tiền còn bù trừ từ tất cả nguồn thanh toán,
  kể cả PARTIALLY_REFUNDED, trừ phần đã hoàn/đang dành hoàn và đúng phân bổ từng phòng.
- Knowledge base phải nạp được cả section `##` và `###`; chính sách hủy V2 không mất khỏi index.
- Owner hoặc token đúng mới xem chính sách/gửi đổi/chấp thuận. Token của khách khác bị từ chối. Lễ tân không được giảm/miễn hay duyệt đổi.
- Khi quản lý duyệt đổi và khách đang mở chi tiết booking: ngày ở, tổng tiền và điều kiện hủy phải tự cập nhật theo kỳ mới, không cần tải lại trang.

Các kiểm thử tự động chính: `UnifiedBookingPolicyTest`, `ArrivalHandlingIntegrationTest`, `BookingChangeSecurityIntegrationTest`, `BookingCancellationServiceTest`, các kiểm thử giao diện booking và front-desk. Integration dùng PostgreSQL tạm qua Testcontainers.

Các tác vụ vận hành còn thủ công: quản lý xét ngoại lệ khác số đêm, xử lý hủy một phần phòng chưa hỗ trợ, xác nhận dịch vụ theo nhà cung cấp, thông báo khách qua kênh đã xác minh, đối soát và chuyển khoản hoàn. Báo giá và áp dụng kỳ mới/thu thêm/tạo khoản hoàn đã được phần mềm xử lý trong giao dịch. Phần mềm không tự gửi thông báo ra ngoài hoặc chuyển tiền ngân hàng.

## Phạm vi xử lý khách đến nhiều phòng

Theo [quy tắc đã chốt](../31-arrival-handling-scope.md): giờ hẹn, liên hệ và nhận muộn có thể áp dụng
cho các phòng cùng đơn được chọn rõ; kiểm tra riêng từng phòng, cập nhật trong một giao dịch.
Nhận phòng và no-show theo từng phòng. Hủy/đổi kỳ ở hiện vẫn toàn đơn; không dùng lựa chọn
phạm vi giờ đến để giả lập hủy/đổi một phần.
