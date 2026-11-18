# Đề xuất chính sách thống nhất: hủy, đổi lịch, đến muộn và không đến

**Trạng thái: đã triển khai mã nguồn gói tiêu chuẩn V2 ngày 13/11/2026 theo yêu cầu; chỉ áp khi khách chấp thuận phiên bản mới.**
Phạm vi và giới hạn triển khai xem [kiểm thử và vận hành](testing/unified-booking-policy.md); chính sách phục vụ khách ở `docs/knowledge-base/cancellation-policy.md`. Dữ liệu thật không được gán lại điều khoản tự động.
Các mức 48/24 giờ, 50%/100% một đêm, một lần đổi và thời hạn 90 ngày dưới đây là đề xuất riêng cho Hanbay, không phải mức bắt buộc chung của ngành khách sạn.

## 1. Phạm vi và nguyên tắc

Áp dụng cho đặt trực tiếp, gói giá tiêu chuẩn có quyền hủy/đổi theo điều kiện công bố.
Booking OTA, hợp đồng công ty/đoàn, gói khuyến mãi không hoàn có điều khoản riêng đã được khách chấp thuận; không tự ghi đè bằng chính sách trực tiếp.

- Nhận phòng chuẩn 14:00, trả phòng 12:00, múi giờ Asia/Ho_Chi_Minh.
- Mốc hủy/đổi tính chính xác theo giờ nhận phòng, không theo số ngày lịch.
- Khách báo hủy, khách xin đổi lịch, khách đến muộn và khách không đến là bốn tình huống khác nhau.
- Phí được xác định theo điều khoản của booking; miễn/giảm là quyết định có lý do của quản lý.
- Quyền sử dụng phòng, phí phải trả và tình trạng thực thu/hoàn được theo dõi riêng.
- Không thu đồng thời tiền các đêm đã hủy và một khoản phạt thay thế cho chính các đêm đó.
- Không đổi chính sách hồi tố cho booking cũ. Phải lưu phiên bản, nội dung và thời điểm khách chấp thuận.

## 2. Căn cứ tính phí chung

Gọi **P** là giá phòng đêm đầu của booking gốc đã xác nhận, sau giảm giá phân bổ cho đêm đó.
Không lấy giá bán hiện tại, giá trung bình cả kỳ, toàn bộ tiền đã trả hoặc tổng giá trị dịch vụ làm P.
Khách phải được thấy trước số tiền VNĐ dùng làm căn cứ; cách thể hiện thuế/phí trên khoản phạt phải theo cấu hình chứng từ áp dụng, không tự cộng thuế/phí của dịch vụ chưa cung cấp.

P và phiên bản chính sách được lưu để đối chiếu xuyên suốt chuỗi thay đổi của cùng booking.
Nếu nâng hạng nhưng giữ nguyên kỳ ở, báo giá mới phải nêu rõ có thay đổi căn cứ P hay không; không âm thầm thay đổi điều khoản.
Đợt triển khai đầu nên chỉ hỗ trợ đổi ngày cùng hạng, giữ P gốc cho toàn chuỗi.

Tổng phí do khách hủy/đổi lịch/no-show trên cùng booking tiêu chuẩn không vượt P mỗi phòng.
Phí này tách khỏi tiền khách thực ở và dịch vụ thực tế đã sử dụng.

## 3. Bảng phí hủy và đổi lịch

| Thời điểm tiếp nhận yêu cầu hợp lệ                                                               | Phí hủy                          | Phí đổi lịch nếu được chấp thuận                                      |
| ------------------------------------------------------------------------------------------------ | -------------------------------- | --------------------------------------------------------------------- |
| Còn ít nhất 48 giờ trước giờ nhận                                                                | 0đ                               | 0đ                                                                    |
| Còn từ 24 giờ đến dưới 48 giờ                                                                    | 50% P                            | 50% P                                                                 |
| Còn dưới 24 giờ, kể cả sau giờ nhận nhưng trước hạn tiếp nhận                                    | 100% P                           | 100% P; cần quản lý chấp thuận                                        |
| Đã hết hạn tiếp nhận, booking chưa đóng và khách chủ động liên hệ                                | Hủy muộn, 100% P; đóng CANCELLED | Không mặc nhiên được đổi; quản lý xét ngoại lệ, phí mục tiêu tối đa P |
| Đã hết hạn, chưa nhận phòng, không có thỏa thuận tiếp nhận còn hiệu lực và chưa xác nhận hủy/đổi | Không tự suy thành báo hủy       | Nhân viên xác nhận NO_SHOW, phí mục tiêu 100% P                       |

Đúng mốc 48 giờ thuộc mức 0đ; đúng mốc 24 giờ thuộc mức 50% P. Tiền tính theo VND, phần lẻ đồng làm tròn xuống.
P bằng 0 do giá đã chốt thì phí theo P cũng bằng 0; không tự thay bằng giá niêm yết để phạt.

Ví dụ nhận 15/11 lúc 14:00, P = 1.000.000đ:

- Gửi hủy chậm nhất 13/11 lúc 14:00: 0đ.
- Sau 13/11 lúc 14:00 đến hết 14/11 lúc 14:00: 500.000đ.
- Sau 14/11 lúc 14:00: 1.000.000đ.
- Không đến và được đóng no-show: tối đa 1.000.000đ tổng phí trên chuỗi, không phải 100% toàn kỳ.

Quản lý được giảm/miễn các mức trên theo mục 10, không tự nâng quá mức khách đã chấp thuận.

## 4. Tiếp nhận báo hủy: không chờ duyệt để giải phóng phòng

Phân biệt hai thao tác:

1. **Hỏi điều kiện/số tiền hủy:** chỉ báo giá, chưa hủy, vẫn giữ booking.
2. **Khách xác nhận hủy:** xác minh người có quyền, ghi thời điểm và nội dung xác nhận, chuyển CANCELLED, giải phóng phần giữ chỗ chưa sử dụng ngay.

Hủy theo chính sách đã chấp thuận không cần quản lý duyệt lại. Khách có thể tự hủy với mức phí đã hiển thị; lễ tân cũng được thực hiện đúng mức chuẩn, kể cả dưới 24 giờ.
Nếu khách xin miễn/giảm hoặc booking cũ thiếu điều khoản, quản lý xem xét **phần quyết toán**. Không giữ phòng vô hạn khi khách đã xác nhận không còn nhu cầu sử dụng.
Với booking cũ chưa xác định căn cứ thu, không ghi phí mới như khoản nợ đã được chấp thuận; ghi trạng thái cần xem xét và lưu bằng chứng.

Thời điểm tính chính sách là lúc khách xác nhận hủy qua kênh hợp lệ, không phải lúc nhân viên duyệt hay chuyển tiền hoàn.
Cuộc gọi được nhập muộn cần có thông tin đối chiếu; thay đổi thời điểm tính phí phải qua quản lý và lưu lịch sử, không chỉ sửa một ô ghi chú.

Xác nhận hủy không tự mang nghĩa khách đã nhận tiền hoàn. Khách không đồng ý mức phí có thể tạo khiếu nại/đề nghị xem xét, không đổi CANCELLED sang NO_SHOW.

## 5. Tiếp nhận xin đổi lịch

### Điều kiện

- Booking đã xác nhận và chưa nhận phòng; có người có quyền yêu cầu.
- Chính sách tiêu chuẩn cho đổi tối đa **một lần**, kỳ mới trong **90 ngày từ ngày nhận gốc**, tùy tồn phòng. Lần thứ hai là ngoại lệ quản lý, không tự tạo quyền đổi vô hạn.
- Không mặc định chấp thuận đổi chỉ vì khách đã gửi yêu cầu.
- Quản lý kiểm tra giá, tồn cả kỳ, sức chứa và điều kiện dịch vụ; khách xác nhận báo giá trước khi áp dụng.
- Chỉ ghi phí đổi khi việc đổi được xác nhận thành công. Yêu cầu bị từ chối, khách không chấp thuận giá hoặc chưa áp dụng không phát sinh phí đổi.

### Khi chờ xử lý

Giữ ngày và giữ chỗ cũ. Không tự giải phóng ngày cũ hoặc bảo đảm phòng kỳ mới chỉ bằng một dòng ghi chú.
Yêu cầu đang chờ không tự kéo dài hạn tiếp nhận và không được dùng để treo booking vô hạn.
Mục tiêu vận hành: xử lý trong 30 phút nếu khách đến trong ngày; yêu cầu ngày tương lai xử lý trong ca làm việc.
Yêu cầu còn chờ tại cutoff phải được quản lý quyết định hoặc kết thúc theo hạn đã thông báo, rồi mới xử lý no-show; không tự phạt do nhân viên xử lý chậm.

Thời điểm xác định mức phí đổi là lúc nhận **đề nghị đầy đủ, hợp lệ**. Quản lý xử lý muộn không làm khách tăng bậc phí.
Nếu khách thay đổi nội dung/ngày đề nghị hoặc rút yêu cầu rồi gửi yêu cầu khác, ghi nhận lần yêu cầu mới.

### Khi áp dụng

- Kiểm tra lại tồn, cập nhật ngày mới và giải phóng ngày cũ trong một giao dịch.
- Giữ mã booking hoặc liên kết rõ booking gốc/thay thế; giữ được lịch sử và phần phí đã phát sinh.
- Tính giá phòng kỳ mới theo báo giá khách đã chấp thuận; không bảo đảm giá cũ nếu kỳ mới đắt hơn.
- Dịch vụ chuyển được thì xác nhận lại ngày/số lượng. Dịch vụ chưa cung cấp, không chuyển được thì bỏ và hoàn; chi phí bên thứ ba không hoàn chỉ giữ khi đã công bố và có căn cứ.
- Thu chênh lệch theo nghĩa vụ mới. Tiền đang dành để hoàn không được đồng thời dùng thanh toán kỳ mới.
- Hủy mã thanh toán cũ đã sai ngày/số tiền, thông báo số tiền và hạn thanh toán mới.
- Nếu còn thiếu tiền sau đổi, booking giữ CONFIRMED kèm công nợ và hạn thu đã thỏa thuận; quá hạn thanh toán là trường hợp cần xử lý, không gọi là no-show trước ngày đến mới.
- Đổi ngày đến cần kiểm tra lại thông tin tuổi trẻ em theo ngày mới.

Khách đến tối cùng ngày chỉ cập nhật giờ đến; khách muốn bỏ đêm đầu và dời ngày nhận sang hôm sau là đổi kỳ/rút ngắn, phải báo rõ phí và giá mới.

## 6. Không tính phí trùng và không mở lại quyền hủy miễn phí

Lưu **F** là tổng phí hủy/đổi lịch/no-show hợp lệ đã phát sinh trên chuỗi booking, tách khỏi tiền phòng.
Đổi ngày không xóa F. Sau đổi, một yêu cầu hủy mới có thể nằm trong cửa sổ miễn phí của kỳ mới nhưng không tự hoàn lại phí đổi đã phát sinh.

Với một sự kiện tiếp theo:

```text
Phí mục tiêu = tỷ lệ áp dụng tại sự kiện × P gốc
Tổng phí sau sự kiện = max(F trước đó, Phí mục tiêu), tối đa P
Phí ghi thêm = Tổng phí sau sự kiện - F trước đó
```

Đây là đề xuất giới hạn thương mại cho gói tiêu chuẩn Hanbay; không phải quy tắc mặc định được áp dụng ở mọi khách sạn.
Quản lý vẫn có thể đảo/giảm F với lý do và chứng từ, không sửa/xóa lịch sử cũ.

Ví dụ P = 1.000.000đ:

| Chuỗi sự kiện                                           | Tổng phí cuối cùng                                                      |
| ------------------------------------------------------- | ----------------------------------------------------------------------- |
| Đổi trước 30 giờ, rồi hủy trước kỳ mới 5 ngày           | 500.000đ phí đổi đã phát sinh; không trở về 0đ                          |
| Đổi trước 30 giờ, rồi hủy kỳ mới trước 10 giờ           | 1.000.000đ tổng; chỉ ghi thêm 500.000đ                                  |
| Đổi sát giờ đã chịu 1.000.000đ, sau đó không đến kỳ mới | 1.000.000đ tổng; không ghi thêm một đêm phạt nữa                        |
| Đổi trước 3 ngày miễn phí, rồi hủy kỳ mới trước 30 giờ  | 500.000đ                                                                |
| Đổi có phí rồi khách đến ở bình thường                  | Tiền kỳ mới + phí đổi đã phát sinh; không tự trừ phí đổi vào tiền phòng |

Không thu riêng thêm phí xử lý hồ sơ, phí báo hủy và phí no-show cho cùng một sự kiện.
Đã CANCELLED không tiếp tục bị đánh NO_SHOW. Từ chối đề nghị đổi không đồng nghĩa khách đã hủy booking cũ.

## 7. Đến muộn và không đến

### Đến muộn

- Hẹn 19:00 mà 20:00 chưa đến: liên hệ và cập nhật giờ, phụ thu 0đ.
- Trước 06:00 hôm sau: tiếp nhận theo booking, giữ tiền kỳ ở và ngày trả gốc.
- Từ 06:00: cần quản lý duyệt ngoại lệ có hạn, không vượt giờ trả phòng gốc; phụ thu đến muộn 0đ.
- Ghi giờ hẹn không tự thành thỏa thuận nhận sau hạn. Lễ tân không tự cho nhận sau hạn bằng một ghi chú.
- Đúng hạn ngoại lệ thì quyền nhận muộn hết hiệu lực; chưa có quyết định hủy/no-show thì trạng thái vẫn cần xử lý.

### No-show

Chỉ xác nhận khi booking CONFIRMED chưa nhận, đã hết hạn tiếp nhận, không có ngoại lệ còn hiệu lực và các yêu cầu đang chờ đã được giải quyết.
Nhân viên ghi nhận liên hệ; không nghe máy không chứng minh khách chủ động hủy.
Không bắt buộc phải liên lạc thành công mới được đóng, nhưng cần lưu kênh/thời điểm/kết quả đã thử liên hệ.

Đóng NO_SHOW giải phóng phần giữ chỗ còn lại, không tạo check-in/check-out giả hoặc tự làm phòng DIRTY.
Phí mục tiêu 100% P, theo quy tắc tổng phí mục 6; bỏ nghĩa vụ tiền các đêm và dịch vụ chưa cung cấp.
Khách đến sau khi booking đã đóng: kiểm tra tồn và tạo booking mới/liên kết, không tự khôi phục hoặc lấy khoản đang chờ hoàn để sử dụng.

Khách báo sẽ đến vào các đêm sau cần được quản lý xác nhận điều chỉnh kỳ ở, không vừa giữ toàn bộ booking cho khách vừa giải phóng bán lại.

## 8. Đổi phòng, rút ngắn và các trường hợp liên quan

| Tình huống                                          | Đề xuất xử lý                                                                                                                                                                     |
| --------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Đổi số phòng cùng hạng, cùng kỳ                     | Phí đổi 0đ nếu có phòng phù hợp; không bảo đảm số phòng cụ thể nếu chưa cam kết                                                                                                   |
| Nâng hạng, giữ ngày                                 | Thu chênh lệch theo báo giá; phí đổi thao tác 0đ                                                                                                                                  |
| Hạ hạng trước khi nhận                              | Tính lại phần giá được chấp thuận; rút bớt đêm/phòng phải xét hủy phần bị bỏ, không mặc định hoàn toàn bộ chênh lệch sát giờ                                                      |
| Phòng lỗi hoặc khách sạn không phục vụ đúng cam kết | Khách sạn xử lý đổi tương đương/nâng hạng theo thỏa thuận, không thu phí lỗi của khách sạn                                                                                        |
| Rút ngắn kỳ ở trước check-in                        | Coi phần đêm bị bỏ là hủy một phần; mức phí theo thời điểm yêu cầu trên giá đêm đầu tiên của phần bị bỏ, tối đa giá trị phần đó; không cộng thêm phí đổi ngày cho cùng phần bị bỏ |
| Khách đã nhận phòng                                 | Chuyển sang chính sách lưu trú: đổi phòng, gia hạn hoặc trả sớm; không dùng hủy/no-show trước check-in                                                                            |
| Booking chưa xác nhận, còn chờ thanh toán           | Hủy/hết hạn giữ chỗ không chịu phí hủy/no-show; tiền đã nhận một phần hoặc đến sau hạn phải đối soát hoàn, không bị coi tự mất                                                    |

Nếu hỗ trợ rút ngắn trước check-in, khoản phí hủy phần đêm bị bỏ cũng tham gia tổng F và trần P của booking gốc.
Không gọi rút ngắn trước khi ở là trả phòng sớm. Quy tắc 20% đêm chưa dùng của trả sớm hiện tại phải được công bố riêng; không cộng vào hủy/đổi trước check-in.

## 9. Đơn nhiều phòng và kênh bán

- Tính căn cứ/giới hạn phí theo từng phòng bị ảnh hưởng, không lấy một phòng đại diện cho cả đơn.
- Về nghiệp vụ có thể hủy/đổi một phần phòng chưa nhận; phòng đang ở hoặc không bị thay đổi vẫn giữ nguyên.
- Nếu bản phần mềm hiện tại chỉ hỗ trợ đổi/hủy toàn đơn, giao diện phải báo rõ phạm vi trước xác nhận; chưa triển khai hủy một phần thì không giả lập bằng hủy cả đơn rồi tạo lại mà bỏ dấu vết tiền.
- No-show từng phòng; người trưởng đoàn đến không có nghĩa tất cả phòng đã nhận.
- Booking từ OTA: đối chiếu đúng chính sách, bên thu tiền và trạng thái của kênh; hoàn theo cơ chế của kênh, không hoàn cùng khoản ở cả hai nơi.
- Giá lễ/Tết, đoàn hoặc không hoàn cần chính sách riêng được gắn từ lúc bán. Không tự đổi booking tiêu chuẩn sang không hoàn khi ngày đến gần.

## 10. Phân quyền, ngoại lệ và hoàn tiền

| Vai trò                    | Quyền đề xuất                                                                                                  |
| -------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Khách/chủ booking có quyền | Xem phí, xác nhận hủy, gửi đổi lịch, chấp thuận giá mới, cung cấp thông tin nhận hoàn                          |
| Lễ tân                     | Ghi liên hệ/giờ hẹn, hủy đúng chính sách, gửi đề nghị đổi/nhận muộn, xác nhận no-show theo mức chuẩn           |
| Quản lý                    | Duyệt/từ chối đổi và nhận sau hạn, giảm/miễn/đảo phí, xử lý booking cũ/ngoại lệ, xác nhận đã hoàn sau đối soát |

Chỉ quản lý giảm/miễn, bắt buộc lý do; trường hợp khách sạn không thể phục vụ thì đề xuất hoàn phần không cung cấp và đảo phí do khách sạn gây ra.
Khách ốm, chuyến bay bị hủy hoặc sự kiện bất thường được xem xét từng trường hợp, không mặc nhiên nhận mọi yêu cầu hoặc tự thu mức ngoài thỏa thuận.
Khi phí chưa xác định được thì ghi cần xem xét, không ghi phí đã thu hoặc tiền đã hoàn.

Quyết toán theo thực thu, trừ các khoản đã hoàn/đang dành để hoàn; không hoàn hai lần.
Nghĩa vụ sau hủy/no-show là phí hợp lệ và chi phí thực tế không hoàn đã được công bố; tiền các đêm/dịch vụ chưa cung cấp được loại khỏi nghĩa vụ.
Nghĩa vụ sau đổi là tiền kỳ mới/dịch vụ đã chấp thuận cộng tổng phí đổi phát sinh còn hiệu lực.

```text
Tiền còn bù trừ = tiền thực nhận - tiền đã hoàn - tiền đang được dành cho khoản hoàn khác
Hoàn thêm = max(Tiền còn bù trừ - Nghĩa vụ còn lại, 0)
Còn phải thu = max(Nghĩa vụ còn lại - Tiền còn bù trừ, 0)
```

Không tự xóa công nợ vì tiền cọc nhỏ hơn phí. Không tự thu tiền ngoài cơ chế khách đã cho phép.
Hoàn ưu tiên nguồn thanh toán gốc; chuyển khoản thủ công cần xác minh tài khoản nhận và mã tham chiếu.
SLA đề xuất thông báo cho khách: xử lý hoàn trong 3–7 ngày làm việc sau khi đủ điều kiện và thông tin nhận hoàn, thời gian tiền về phụ thuộc ngân hàng/kênh thanh toán.
Không giữ booking chờ hoàn xong mới hủy; không ghi SUCCESS khi mới tạo yêu cầu PENDING.

## 11. Nội dung khách cần thấy trước xác nhận

Hiển thị một bản tóm tắt bằng tiền cụ thể cho booking đó:

- Phí hủy/đổi trước 48 giờ, từ 24–48 giờ, dưới 24 giờ và không đến.
- Căn cứ P, ngày/giờ hạn hủy miễn phí và hạn 50% bằng ngày/giờ cụ thể.
- Phí đổi không phải tiền phòng kỳ mới; tổng số phải trả và chênh lệch.
- Giới hạn một lần đổi/90 ngày, tùy phòng còn và quản lý xác nhận.
- Phần phí đã phát sinh không tự được hoàn do đổi ngày; cách bù phí khi hủy/no-show tiếp theo.
- Khoản phải hoàn/còn thu, trạng thái chờ chuyển, phạm vi toàn đơn/từng phòng.

Gửi xác nhận hủy/đổi bằng kênh khách đã xác minh; lưu người thao tác, thời điểm, giá/chính sách cũ và mới, đồng ý của khách và lý do ngoại lệ.

## 12. Khác biệt cần triển khai sau khi chủ khách sạn chốt

| Đang có                                                     | Đề xuất thay đổi                                                                               |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Hủy 24–48 giờ: 50% tổng booking                             | 50% giá phòng đêm đầu P                                                                        |
| Hủy dưới 24 giờ: quản lý nhập ngoại lệ 0 đến tổng booking   | Mức chuẩn 100% P; quản lý chỉ giảm/miễn theo quyền                                             |
| Mọi yêu cầu hủy qua quầy chờ quản lý duyệt booking          | Hủy chuẩn xác nhận ngay; tách quản lý duyệt ngoại lệ tài chính                                 |
| Đổi lịch 0đ, tính lại ngày/giá                              | Phí theo cùng bảng 48/24 giờ; báo giá và khách chấp thuận trước khi áp dụng                    |
| Sau đổi, chính sách hủy xét kỳ mới nhưng chưa có sổ phí đổi | Lưu P gốc, phiên bản chính sách, F và các khoản điều chỉnh; không đặt lại F về 0               |
| Chỉ đổi/hủy toàn đơn nhiều phòng                            | Giữ rõ giới hạn hiện tại; mở rộng xử lý từng phòng khi đã có phân bổ tiền và kiểm thử          |
| Dịch vụ đổi lịch giữ nguyên giá đã chọn                     | Xác nhận lại khả năng cung cấp; không giữ nghĩa vụ dịch vụ không chuyển được mà chưa thông báo |

Không cập nhật corpus chính sách đang phục vụ khách hoặc áp mức mới cho dữ liệu thật chỉ bằng việc có tài liệu đề xuất này.

## 13. Cơ sở nghiệp vụ tham khảo

Đối chiếu ngày 12/11/2026. Nguồn dưới đây hỗ trợ cách thiết kế chính sách và vận hành; không quy định các mức số Hanbay đề xuất.

- [Cloudbeds — Set up Smart Policies](https://myfrontdesk.cloudbeds.com/hc/en-us/articles/44193507004827-Set-up-Smart-Policies): khách sạn cấu hình điều kiện hủy/no-show theo chính sách/gói giá; có cách tính theo đêm đầu hoặc tỷ lệ.
- [Cloudbeds — Posting cancellation and no-show fees](https://myfrontdesk.cloudbeds.com/hc/en-us/articles/57813669667483-How-Smart-Policies-Automatically-Post-Cancellation-and-No-Show-Fees-in-Cloudbeds-PMS): phân biệt ghi phí với thu/hoàn thực tế; cần đối chiếu khoản đã ghi để tránh giữ cả tiền phòng cũ và phí thay thế.
- [Cloudbeds — Cancel or modify OTA reservations](https://myfrontdesk.cloudbeds.com/hc/en-us/articles/216541178-Cancel-or-modify-OTA-reservations): thay đổi từ OTA cần được đối chiếu/đồng bộ với kênh, không suy từ thay đổi một phía trong PMS.
- [Marriott — Change or cancel a reservation](https://help.marriott.com/s/article/change-cancel-marriott-reservation): điều kiện theo khách sạn/gói giá; đổi ngày, số người hoặc hạng phòng có thể đổi giá.
