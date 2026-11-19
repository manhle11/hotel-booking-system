# Phạm vi xử lý khách đến: từng phòng và nhóm phòng cùng đơn

Chốt triển khai ngày 13/11/2026 cho Hanbay. Một đơn nhiều phòng có người đặt và thanh toán chung,
nhưng mỗi booking phòng vẫn có trạng thái, giờ đến thực tế và quyền tiếp nhận riêng.
Không suy ra cả đơn đến cùng giờ từ việc cùng tên, số điện thoại hoặc một người đại diện đã đến.

## Quy tắc áp dụng

| Thao tác | Đơn vị nghiệp vụ và cách áp dụng trong project |
| --- | --- |
| Ghi giờ dự kiến đến | Từng phòng; có thể chọn nhiều phòng cùng đơn nếu khách/đại diện xác nhận họ đến chung |
| Ghi kết quả liên hệ | Từng phòng hoặc các phòng được cuộc trao đổi đó đề cập; lưu cùng nội dung cho các phòng đã chọn |
| Đề nghị nhận sau hạn | Một hoặc nhiều phòng chưa nhận được chọn rõ; kiểm tra thời gian riêng từng phòng |
| Duyệt/từ chối nhận sau hạn | Chỉ quản lý; có thể áp dụng cho các phòng đã chọn có cùng hạn đề nghị đang chờ |
| Kết thúc ngoại lệ nhận muộn | Chỉ quản lý; một hoặc nhiều phòng đã chọn còn ngoại lệ hiệu lực |
| Nhận phòng | Xác nhận theo từng phòng thực tế và khách lưu trú; không tự nhận toàn đơn khi đại diện đến |
| Không đến / no-show | Xét từng phòng đủ điều kiện và quyết toán đúng phần tiền của phòng đó |
| Hủy hoặc đổi ngày lưu trú | Bản hiện tại chỉ hỗ trợ toàn đơn, có bước xác nhận riêng; tất cả phòng phải chưa nhận. Đây là giới hạn triển khai, không phải quy tắc khách sạn bắt buộc chỉ được hủy/đổi toàn đơn |
| Giảm/miễn phí | Quản lý quyết định và lưu bút toán riêng từng phòng |

Hủy/đổi một phần phòng có thể là nhu cầu thực tế, nhưng chưa được giả lập bằng đổi giờ hẹn,
đánh no-show, khôi phục booking hoặc hủy cả đơn rồi bỏ dấu vết tiền. Cần luồng phân bổ giá/tiền/hoàn
và kiểm thử riêng trước khi mở tính năng đó.

## Cách dùng trên màn Theo dõi khách đến

1. Từng dòng có mã booking và mã đơn, số thứ tự phòng trong đơn.
2. Mở **Xử lý khách**, xem mục **Cùng đơn … · N phòng** để kiểm tra trạng thái, giờ hẹn và hạn từng phòng.
3. Với đơn nhiều phòng, khi cập nhật giờ đến, ghi liên hệ hoặc xử lý nhận muộn, phải chọn phạm vi trước khi nhập/lưu:
   - **Chỉ phòng …**: áp dụng một booking đang mở.
   - **Các phòng cùng đơn đã chọn**: mặc định chọn các phòng phù hợp cho thao tác đó; có thể bỏ chọn phòng đến riêng.
4. Danh sách hiển thị chính xác các booking sẽ bị tác động. Phòng đang mở luôn thuộc phạm vi.
   Phòng đã nhận, đã hủy, đã trả hoặc đã đóng no-show không được chọn để cập nhật giờ đến.
   Khi duyệt nhận muộn, phòng có hạn đề nghị khác cũng không được chọn chung.
5. Máy chủ kiểm tra lại danh sách trong một giao dịch. Nếu có phòng sai đơn, đã đổi trạng thái hoặc sai thời gian,
   từ chối toàn bộ thao tác; không cập nhật dở một phần và không tự mở rộng sang phòng khác.
6. Lưu giờ hẹn/đề nghị không thay giá, ngày ở, công nợ hoặc tình trạng thực nhận phòng.
   Thao tác nhiều phòng lưu danh sách phạm vi trong lịch sử từng phòng.

API tương thích cũ: không có `bookingCodes` vẫn chỉ áp dụng booking trong URL.
Các endpoint `/arrival-estimate`, `/arrival-contact-result`, `/arrival-contact`, `/arrival-hold`,
`/arrival-hold/decision`, `/arrival-hold/release` nhận tùy chọn `bookingCodes` (1–10 mã, cùng đơn,
không trùng và gồm booking đang mở). Các endpoint nhận phòng/no-show/quyết toán không mở rộng theo danh sách này.
Thông tin nhóm nằm trong `orderRooms` của phản hồi dành cho nhân viên; danh sách theo dõi có `orderCode`, `roomCount`, `orderRoomNumber`.

## Tình huống BK20261113001401

Nếu BK20261113001401 và ba booking còn lại cùng đơn, đều chưa nhận và cả đoàn xác nhận sẽ đến chung:
chọn **Các phòng cùng đơn đã chọn**, giữ đủ bốn mã rồi lưu giờ đến. Nếu chỉ khách của phòng 1401 đến muộn,
chọn **Chỉ phòng …**. Nếu ba phòng kia thực tế đã check-in, giữ trạng thái của ba phòng đó và chỉ xử lý phòng còn lại.

Giờ đến **14/11/2026 08:50** vượt hạn thông thường **14/11/2026 06:00**:

- Lưu hẹn 08:50 chỉ cập nhật thông tin; không kéo dài quyền nhận cho bất kỳ phòng nào.
- Chọn **Chuyển sang xin nhận sau hạn** để giữ nguyên danh sách phòng, điền hạn sau 08:50 (ví dụ 09:00),
  không vượt giờ trả gốc của bất kỳ phòng đã chọn nào; ghi lý do và gửi quản lý.
- Quản lý kiểm tra rồi duyệt đúng các phòng. Chỉ các phòng đã duyệt được tiếp nhận trước hạn ngoại lệ.
- Nếu khách muốn bỏ đêm đầu và đổi sang ngày nhận khác, dùng đổi kỳ ở; không coi một ghi chú giờ hẹn là đổi ngày.

Không tự sao chép giờ hẹn đã lưu của booking 1401 sang ba booking còn lại: cần nhân viên xác nhận rõ phạm vi.

## Căn cứ tham khảo và lựa chọn của Hanbay

Các PMS có cả thao tác reservation riêng và thao tác nhóm trên những reservation được chọn.
Oracle OPERA mô tả Mass Check In với điều kiện kiểm tra từng reservation; Cloudbeds cho phép chọn các reservation
trong Group Profile trước khi thực hiện Bulk Action. Điều đó hỗ trợ mô hình “phòng có trạng thái riêng,
nhóm là phạm vi thao tác được chọn”; không chứng minh mọi khách sạn bắt buộc dùng cùng một giao diện hay bảng phí.
Lựa chọn yêu cầu xác nhận phạm vi, cập nhật tất cả hoặc từ chối tất cả và giới hạn các thao tác tài chính
là quyết định triển khai của Hanbay dựa trên mô hình dữ liệu hiện có.

Đối chiếu ngày 13/11/2026:

- [Oracle OPERA — Checking In Multiple Reservations](https://docs.oracle.com/en/industries/hospitality/opera-cloud/24.1/ocsuh/t_checking_in_multiple_reservations_mass_check_in.htm)
- [Cloudbeds — Manage Group Profiles, Reservations, and Credit Cards](https://myfrontdesk.cloudbeds.com/hc/en-us/articles/27230464304795-Manage-Group-Profiles-Reservations-and-Credit-Cards)

## Kiểm thử bắt buộc

- Đơn bốn phòng: lưu chung giờ hẹn thì đủ bốn phòng đổi, cutoff và tiền giữ nguyên.
- Chọn một phòng hoặc một nhóm hai phòng: các phòng còn lại giữ giờ riêng.
- Chặn phòng khác đơn dù cùng người đặt; chặn danh sách rỗng, trùng mã hoặc thiếu booking đang mở.
- Một phòng đã nhận hoặc một phòng có kỳ ở không phù hợp: từ chối toàn bộ, các phòng khác chưa bị cập nhật.
- Lễ tân gửi đề nghị chung; chỉ quản lý duyệt chung. Một đề nghị đổi sau khi xem: không duyệt dở phần còn lại.
- Ghi liên hệ chung không đánh no-show. Đóng no-show một phòng không đóng các phòng khác.
- Mất phản hồi: tra cứu lại dữ liệu và phạm vi trước khi thao tác tiếp.
