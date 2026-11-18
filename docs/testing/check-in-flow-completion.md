# Hoàn thiện luồng nhận phòng — 12/11/2026

Phần xử lý sau khi khách quá giờ đã được bổ sung trong
[luồng đến muộn và không đến](no-show-handling.md). Các mục dưới ghi lại trạng
thái trước lần bổ sung này; dùng tài liệu mới cho phí, giữ phòng có hạn và NO_SHOW.

## Kết quả triển khai

| Hạng mục          | Hành vi sau thay đổi                                                                                                                                                                                 |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reset form        | Đổi mã booking sẽ bỏ phòng và lý do nhận sớm cũ. Nhận thành công hoặc bấm Làm mới sẽ xóa toàn bộ dữ liệu nhập, kể cả mã được điền từ URL.                                                            |
| Phân trang        | Khi số booking giảm làm trang hiện tại không còn hợp lệ, danh sách tự chuyển về trang cuối còn dữ liệu. Không dùng dữ liệu tạm của trang trước để quyết định chuyển trang.                           |
| Đối chiếu         | Lễ tân bấm Nhận phòng trên từng dòng để mở đúng mã; trang nhận phòng tự tải thông tin khách chính, liên hệ, hạng phòng, ngày ở, số khách, tổng tiền, đã trả và còn phải thu.                         |
| Khôi phục kết quả | Với lỗi mạng, lỗi server hoặc BOOKING_ALREADY_CHECKED_IN, giao diện tra cứu trạng thái đã lưu. Chỉ hiện kết quả nhận phòng khi booking đang CHECKED_IN, có phòng OCCUPIED và thời điểm nhận thực tế. |
| Khách quá giờ     | Tab Chưa đến / quá giờ liệt kê booking CONFIRMED đã qua cửa sổ nhận phòng; lễ tân có thể mở tra cứu. Booking không bị tự hủy hoặc đổi ngày.                                                          |

## API mới

- `GET /api/v1/bookings/{bookingCode}/check-in-context`: RECEPTIONIST hoặc MANAGER; trả thông tin đối chiếu, điều kiện nhận sớm, phòng đã gán và giờ nhận thực tế. Không dùng API lịch sử booking của khách để cấp quyền đọc cho nhân viên.
- `GET /api/v1/front-desk/arrivals/overdue?page=0&size=20`: RECEPTIONIST hoặc MANAGER; cùng giới hạn phân trang 1–100 như danh sách hôm nay.
- API POST nhận phòng vẫn chỉ cho RECEPTIONIST, giữ nguyên transaction, khóa booking/phòng và kiểm tra xung đột lịch.

## Quy tắc nghiệp vụ được giữ

- Khách hôm trước còn trong kỳ lưu trú được nhận trước 06:00 theo cấu hình. Khi cửa sổ còn mở, nhóm này thuộc danh sách hôm nay; đúng cutoff, chuyển sang danh sách quá giờ. Các booking cũ hơn hoặc đã hết kỳ lưu trú nằm trong nhóm quá giờ.
- Danh sách quá giờ chỉ theo dõi, không tạo trạng thái NO_SHOW, không giải phóng tồn, không tự hoàn tiền hoặc hủy booking đã xác nhận. Quyết định xử lý những tình huống này cần chính sách riêng của khách sạn.
- Nhận sớm trong chính ngày đặt vẫn cần lý do theo policy; khách đến muộn sau nửa đêm không bị bắt nhập lý do nhận sớm.
- Công nợ hiển thị theo từng booking. Booking CONFIRMED còn công nợ vẫn có thể nhận nếu đáp ứng các điều kiện hiện hành; không thêm yêu cầu thanh toán hết trước khi nhận.
- Cập nhật danh sách sau thành công, giữa các tab cùng phiên và kiểm tra lại mỗi 5 giây được áp dụng cho cả danh sách hôm nay lẫn quá giờ.

## Mất kết nối khi xác nhận

1. Không tự gửi lại POST nhận phòng.
2. Tra cứu trạng thái server. Nếu đã nhận, hiển thị đúng phòng và giờ đã lưu, kể cả phòng khác với lựa chọn ban đầu.
3. Nếu chưa xác minh được, khóa xác nhận và hiện nút Tra cứu lại kết quả.
4. Khi tra cứu xác nhận booking chưa được nhận, lễ tân có thể kiểm tra lại phòng trước khi thực hiện tiếp. Backend tiếp tục khóa và kiểm tra lại ở mỗi POST.

## Kiểm tra và sử dụng

- Backend: 77 test đạt, không lỗi hoặc bỏ qua; gồm PostgreSQL Testcontainers riêng, bộ lọc khách quá giờ, mốc cutoff, kiểm tra quyền và luồng đặt nhiều phòng/nhận phòng. Spotless đạt.
- Frontend: **120 test đạt** trong các nhóm lễ tân, nhận phòng và sơ đồ. Trong đó 14 test nhận phòng đã chạy lại sau bản sửa cuối để kiểm tra phản hồi tra cứu cũ không ghi đè booking vừa nhập. Các ca bao gồm reset lý do, phân trang co lại, mở booking từ URL, thông tin đối chiếu, tách hai nhóm danh sách, cập nhật giữa tab, mất phản hồi, nhận trùng và chặn gửi lại khi chưa xác minh.
- Frontend build/TypeScript, ESLint các file liên quan, Prettier và `git diff --check` đều đạt. Các cảnh báo môi trường jsdom và kích thước chunk Vite không làm thất bại kiểm tra.
- Khởi động lại backend để nạp hai API mới; frontend Vite nhận thay đổi qua HMR. Không cần migration mới.
- Không chạy reset hoặc chỉnh sửa database local trong lượt triển khai này; không tạo bản sao lưu dữ liệu.

## Bổ sung: tự khôi phục và nhận phòng đồng thời

- Khi đang chờ xác minh sau lỗi mạng, phản hồi CHECKED_IN hợp lệ từ làm mới nền, focus hoặc reconnect chạy cùng bước hoàn tất như POST thành công: xóa mã/phòng/lý do nhận sớm, bỏ trạng thái chờ xác minh, cập nhật danh sách khách đến/quá giờ, sơ đồ và các cache liên quan.
- Bước hoàn tất được chặn chạy lặp theo booking để tránh lặp thông báo và cập nhật cache khi tra cứu thủ công trùng với cập nhật tự động. Tra cứu thông thường một booking đã nhận vẫn giữ mã trên form, không phát sinh một lượt hoàn tất mới.
- `CheckInConcurrencyIntegrationTest` dùng hai tài khoản lễ tân và hai transaction độc lập trên PostgreSQL Testcontainers. Test giữ khóa ở bản ghi tranh chấp và kiểm tra `pg_blocking_pids` để xác nhận cả hai transaction thực sự đang chờ khóa trước khi tiếp tục.
- Hai booking chọn cùng phòng: chỉ một lần thành công, lần còn lại nhận ROOM_NOT_AVAILABLE; booking thua vẫn CONFIRMED và chưa có phòng/giờ nhận/lịch sử nhận.
- Cùng booking chọn hai phòng khác nhau: chỉ một lần thành công, lần còn lại nhận BOOKING_ALREADY_CHECKED_IN; phòng không được chọn vẫn AVAILABLE. Cả hai ca đều kiểm tra đúng phòng, giờ nhận và duy nhất một lịch sử CONFIRMED → CHECKED_IN với đúng lễ tân thắng.
- Kiểm tra lượt bổ sung: **18 test frontend nhận phòng đạt; 16 test backend đạt**, trong đó có hai test đồng thời mới. ESLint, TypeScript/build, Prettier và Spotless được kiểm tra cho phần thay đổi. Các số 120/77 ở trên là phạm vi hồi quy của đợt hoàn thiện năm hạng mục trước đó.
