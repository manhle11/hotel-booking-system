# 16 luồng FE — chỉ phần liên kết, tồn phòng và mốc thời gian vừa sửa

Phạm vi đối chiếu working tree `main` ngày 03/11/2026. Quy tắc chuẩn tại
[thời gian lưu trú và tồn phòng](../23-stay-time-and-inventory.md). Đây là checklist
**Chưa chạy**; không đại diện kết quả test. Các chức năng khác trên web không nằm
trong checklist này; bộ toàn web nằm ở [web-uat-guide.md](web-uat-guide.md).

## Chuẩn bị

FE `http://localhost:5173`, BE `http://localhost:8080`, DB đúng migration; tài khoản
khách/lễ tân/quản lý. Dùng booking/phòng thử. D là ngày từ `GET /api/v1/stay-policy`,
không lấy ngày tùy ý từ đồng hồ browser. Hai phiên dùng profile/trình duyệt độc lập.

Mỗi luồng ghi Đạt/Lỗi/Bị chặn, mã booking/phòng, ngày đã chọn và ảnh/video. Ca thiếu
callback test, booking đúng trạng thái hoặc Clock test ghi Bị chặn. Không đặt tiền thật
để tạo fixture; không đổi giờ máy chủ đang phục vụ để chạy ca biên.

## Các luồng

| ID | Vai trò / điều kiện | Thao tác trên FE | Kỳ vọng / thử lỗi |
|---|---|---|---|
| FE-01 | Khách bất kỳ | `/rooms/search` → tìm → nhấn tên từng hạng; Back; Cmd/Ctrl+click | Đúng `/room-types/{id}`, đúng nội dung; mở tab mới được, không tạo booking khi chỉ nhấn tên. |
| FE-02 | Khách bất kỳ | Mở tìm phòng, chi tiết hạng và tóm tắt đặt | Nhãn Ngày nhận→Ngày trả rõ; ghi chú nhận14:00/trả12:00 theo policy, ngày trả không thêm đêm. |
| FE-03 | Kỳ tương lai còn phòng | D+1→D+9; tìm→chọn→Kiểm tra đơn hàng→thanh toán; sau đó D+1→D+2 | Mọi nơi cùng8 đêm hoặc1 đêm; ngày/giá tương ứng cùng lần tìm. Không tính theo số giờ/24. |
| FE-04 | Khách bất kỳ | Thử ngày quá khứ, cùng ngày nhận/trả, trả trước nhận | Chặn hoặc yêu cầu chọn lại; không tạo booking cho khoảng sai. |
| FE-05 | D sau14:00, trước cutoff; còn tồn | Chọn D→D+2; tạo pending; xem ngày/đêm/hạn giữ | Được đặt dù qua14:00; tính2 đêm, không tự giảm đêm đầu. Ví dụ03/11 lúc 19:55 nhận03→trả05/11 là hợp lệ. Đúng23:59:59 chặn nhận đặt mới cùng ngày. |
| FE-06 | Fixture tồn từng đêm biết trước | So kỳ ngắn/dài; fixture3 phòng, đêm1 bận2, đêm2 bận1 | Cả2 đêm còn1; không trừ gộp booking. Nhãn dùng availableRooms, không lấy readyRooms hiện tại. Muốn kết luận đúng DB cần đối soát độc lập. |
| FE-07 | Hai phiên, một pending mới | A tìm; B giữ một phòng cùng kỳ; A focus lại/đợi30s; A sửa ngày nhưng chưa tìm rồi focus | Tồn cập nhật; khi chưa submit ngày mới thì vẫn refresh kỳ đã tìm. Nối mạng lại và tìm cùng query vẫn làm mới. |
| FE-08 | Manager; hạng/phòng UAT | `/management/rooms` thêm phòng thử AVAILABLE; tìm lại; xóa phòng thử không ràng buộc; tìm lại | Tồn của đúng hạng tăng/giảm tương ứng khi không có thay đổi khác; cache không giữ số cũ. Không thử xóa phòng có lịch sử thật. |
| FE-09 | Chỉ còn 1 phòng; hai khách | Hai profile cùng gửi tạo pending gần đồng thời | Một hold hợp lệ; phiên còn lại báo hết/xung đột; không overbooking. |
| FE-10 | Pending mới, chưa trả | Ghi expiredAt; quay lại sửa; đợi hết hạn; thử tiếp tục; tìm lại | Sửa không kéo dài hold; đúng/sau hạn không xác nhận được; tồn được trả không phụ thuộc lúc scheduler đổi nhãn EXPIRED. Mốc chính xác cần Clock test. |
| FE-11 | Lễ tân; confirmed nhận D; phòng sẵn sàng | `/front-desk/check-in` tra mã→Tìm phòng khả dụng→chọn; trước 14 thử thiếu/có lý do | Thiếu lý do sớm bị chặn; lý do có nội dung≤500 ký tự được xem xét; đúng 14 không cần. Thành công CHECKED_IN/OCCUPIED, giờ thực tế đúng múi giờ. |
| FE-12 | Confirmed nhận D-1; trả≥D; Clock trước 06 | Mở khách đến, tra booking, check-in; fixture riêng thử đúng 06 | Trước06 được tiếp nhận booking gốc; đúng 06 không được. Không đổi ngày ở/giá thành booking mới hôm nay. |
| FE-13 | Phòng AVAILABLE nhưng có lịch gán xung đột; lễ tân | Tìm phòng khả dụng cho booking khác trùng kỳ | Không gợi ý/giao phòng xung đột. Phòng không đúng hạng hoặc không AVAILABLE cũng không được giao. Thiếu fixture ghi Bị chặn. |
| FE-14 | CHECKED_IN đến hạn D; R/M/Admin | `/front-desk/departures/today`; sau 12 thử trả thiếu ghi chú, rồi đủ ghi chú/tiền | Đúng 12 chưa muộn; sau 12 cần ghi chú; không tự cộng đêm. Trước xử lý vẫn đang ở/chặn tồn quá hạn; sau trả CHECKED_OUT/DIRTY, chưa AVAILABLE. |
| FE-15 | CHECKED_IN; ngày trả>D | Chọn Trả phòng sớm; thử chọn hôm qua/ngày mai; chọn D và xử lý theo quote | Execute chỉ cho D theo khách sạn; không ghi lùi/ghi trước thời điểm. Quote chưa phải đã checkout. |
| FE-16 | Khách chủ confirmed/checked-in | Lịch sử→Gia hạn lưu trú; thử đoạn thêm còn tồn rồi fixture hết một đêm/phòng gán xung đột | Thành công chỉ khi đủ cả tồn hạng và lịch phòng; giữ phòng/giá đêm cũ, thêm giá mới. Lỗi không lưu dở ngày/tiền. |

## Hiểu đúng luồng FE-05

14:00 = bắt đầu nhận phòng, 23:59:59 = ngừng nhận đặt mới cùng ngày, expiredAt =
hạn thanh toán riêng, 12:00 ngày trả = kết thúc kỳ ở chuẩn. Ví dụ booking03→05/11
lúc 19:55 có2 đêm; thanh toán đủ trong hạn mới được confirmed và lễ tân check-in.
Đặt thành công không có nghĩa đã tự nhận phòng.

## Phần không thể chứng minh đầy đủ bằng bấm FE

Biên giờ cần backend Clock test. Callback muộn/đúp cần bank fixture. Kiểu TIMESTAMPTZ,
index và bản ghi giờ thực tế cần kiểm DB/API. Guard seed cần thử khởi động backend trên
DB test. Các việc này không được ghi Đạt chỉ vì một trang FE hiển thị đúng.
