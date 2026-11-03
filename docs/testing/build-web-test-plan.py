#!/usr/bin/env python3
"""Build the manual UAT workbook and checklist; never connects to the application/DB.

Run from any directory: python3 docs/testing/build-web-test-plan.py
Only Python's standard library is required. The authored cases below are the source.
"""

from collections import Counter
import csv
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile

OUT = Path(__file__).resolve().parent
CASES = []
GROUPS = []
KINDS = {"H": "Happy", "U": "Unhappy", "B": "Biên", "C": "Đồng thời"}
MODES = {
    "W": "Web",
    "A": "Web + API fixture/DevTools",
    "T": "Web + Clock máy chủ kiểm soát",
    "S": "Web + hai phiên độc lập",
    "E": "Web + dịch vụ ngoài giả lập/test",
    "N": "Web + giả lập mạng",
}


def group(code, name, role, route, setup, rows):
    GROUPS.append((code, name, role, route))
    for number, line in enumerate(rows.strip().splitlines(), 1):
        values = [part.strip() for part in line.split("|")]
        if len(values) == 6:
            values.append("W")
        assert len(values) == 7, (code, number, values)
        kind, priority, title, data, steps, expected, mode = values
        assert kind in KINDS and mode in MODES and priority in {"P0", "P1", "P2"}
        assert all(values), (code, number)
        CASES.append({
            "ID": f"{code}-{number:03d}", "Tên ca": title, "Nhóm": name, "Loại": KINDS[kind],
            "Ưu tiên": priority, "Vai trò": role, "Trang": route,
            "Chuẩn bị chung": setup, "Dữ liệu / điều kiện riêng": data,
            "Thao tác": steps, "Kết quả mong đợi": expected, "Cách chạy": MODES[mode],
            "Trạng thái": "Chưa chạy", "Kết quả thực tế": "", "Bằng chứng": "", "Mã lỗi": "",
        })


group("NAV", "Điều hướng công khai", "Mọi người", "/; /room-types; /rooms/search", "Chưa đăng nhập; FE/BE đang chạy.", r"""
H|P1|Trang chủ và menu|Có hạng phòng ACTIVE|Mở /; dùng logo và từng menu khách|Trang đúng URL; không mất nội dung hoặc báo lỗi console
H|P0|Tên hạng phòng mở đúng chi tiết|Kết quả có ít nhất hai hạng|Nhấn lần lượt tên từng hạng; dùng Back quay lại|Đi đến /room-types/{đúng ID}; tên, mô tả, tiện nghi thuộc đúng hạng
H|P1|Mở liên kết tên bằng tab mới|Có kết quả tìm kiếm|Ctrl/Cmd+click tên; thử Tab rồi Enter|Liên kết hoạt động như link; không tự tạo booking
H|P1|Nút chọn phòng|Đã chọn ngày và số khách|Nhấn Chọn phòng trên một thẻ|/bookings/new nhận đúng hạng, ngày, người lớn, trẻ em
U|P1|URL không tồn tại|Đường dẫn /khong-ton-tai|Nhập URL trực tiếp và tải lại|Hiện trang 404; có thể quay về trang hợp lệ
H|P1|Deep link và tải lại|Một URL chi tiết hợp lệ|Dán URL vào tab mới; F5|Trang tải đúng; không lỗi định tuyến phía hosting
U|P1|ID chi tiết sai|UUID sai định dạng và UUID không tồn tại|Mở /room-types/{id} cho từng mẫu|Thông báo không tìm thấy/lỗi hợp lệ; không hiển thị hạng khác
""")

group("AUTH", "Đăng ký và đăng nhập", "Khách; từng vai trò nhân viên", "/register; /login", "Tài khoản thử riêng; không dùng thông tin cá nhân thật.", r"""
H|P0|Đăng ký khách hợp lệ|Email/điện thoại mới; mật khẩu 8–72 ký tự|Điền đủ; xác nhận mật khẩu; gửi một lần; đăng nhập|Tạo một tài khoản khách roles=[]; đăng nhập được; không có quyền nhân viên
U|P1|Thiếu trường đăng ký|Lần lượt bỏ tên, email, điện thoại, mật khẩu|Gửi từng mẫu; sửa trường lỗi|Chỉ rõ trường thiếu; không tạo tài khoản một phần
B|P1|Biên tên đăng ký|Tên 1, 2, 150, 151 ký tự; chỉ khoảng trắng|Thử từng mẫu với dữ liệu còn lại hợp lệ|Chấp nhận 2–150 sau chuẩn hóa; các mẫu sai bị chặn
U|P1|Email không hợp lệ hoặc trùng|Email sai; email của khách A; khác hoa/thường|Đăng ký từng mẫu|Sai định dạng bị từ chối; không tạo trùng tài khoản theo email chuẩn hóa
B|P1|Số điện thoại đăng ký|0xxxxxxxxx hợp lệ; +84 tương ứng; ký tự lạ; trùng|Gửi từng mẫu và đăng nhập lại|Số VN hợp lệ được chuẩn hóa; mẫu sai/trùng có lỗi; không sinh hai tài khoản cùng số
B|P1|Biên mật khẩu|7, 8, 72, 73 ký tự; xác nhận khác|Đăng ký từng mẫu|8–72 và xác nhận khớp được nhận; không tự cắt mật khẩu; không giả định bắt buộc hoa/thường/số
H|P0|Đăng nhập bằng email và điện thoại|Khách A ACTIVE|Đăng nhập bằng email; logout; đăng nhập bằng điện thoại|Đúng tài khoản, header và trang đến; mật khẩu không lộ
U|P0|Sai thông tin đăng nhập|Sai mật khẩu; định danh không tồn tại|Thử từng mẫu dưới ngưỡng rate limit|Không cấp phiên; thông báo an toàn, không phân biệt tài khoản tồn tại
U|P0|Tài khoản bị khóa hoặc vô hiệu|Fixture INACTIVE và LOCKED|Đăng nhập với mật khẩu đúng|Bị từ chối; không vào trang bảo vệ
H|P1|Ghi nhớ đăng nhập|Khách A; hai cấu hình checkbox|Đăng nhập; đóng/mở phiên trình duyệt theo từng cấu hình|Thời gian lưu phiên phù hợp lựa chọn; không nhầm tài khoản giữa phiên
H|P0|Đăng xuất|Đã đăng nhập; mở trang bảo vệ|Đăng xuất; Back; tải lại trang bảo vệ|Phiên phía web bị xóa; dữ liệu riêng không còn truy cập được
U|P0|Hết hạn access và refresh token|Fixture access hết hạn; sau đó refresh hết hạn|Mở trang bảo vệ; tiếp tục sau khi refresh hết hạn|Access được refresh hợp lệ; refresh không hợp lệ buộc đăng nhập; không lặp vô hạn|A
C|P0|Nhiều request cùng gặp 401|Access hết hạn; refresh còn hiệu lực|Mở màn hình tải nhiều API cùng lúc|Refresh được điều phối; không logout nhầm hoặc tạo vòng lặp request|N
U|P0|Chặn dò mật khẩu|Cấu hình thử: 5 lần sai/cặp tài khoản-IP trong 900s|Sai tới ngưỡng; thử tiếp và thử Enter|429 và thời gian chờ; nút/Enter không gửi liên tục; không khóa toàn tài khoản từ mọi IP|A
B|P1|Hết thời gian khóa tạm|Có Retry-After; đồng hồ thử kiểm soát|Đợi hết hạn; focus lại; đăng nhập đúng|Đếm ngược đúng; cho thử lại đúng hạn; đăng nhập đúng reset bộ đếm liên quan|T
U|P0|Đường dẫn sau đăng nhập không an toàn|from=//evil.example; URL ngoài; trang không có quyền|Mở login với từng đường dẫn và đăng nhập|Chỉ chuyển đến đường dẫn nội bộ có quyền; không open redirect
""")

group("RESET", "Quên và đặt lại mật khẩu", "Khách", "/forgot-password; /reset-password", "Mail test/sandbox; tài khoản A; token mới riêng cho mỗi ca.", r"""
H|P0|Khôi phục mật khẩu thành công|Email A; token còn hạn|Yêu cầu email; mở link; nhập mật khẩu mới khớp; đăng nhập lại|Mật khẩu mới dùng được; mật khẩu cũ không dùng được; token bị tiêu thụ|E
U|P1|Email không tồn tại|Email mới chưa đăng ký|Gửi yêu cầu giống tài khoản có thật|Phản hồi không tiết lộ tồn tại tài khoản; không lộ token
U|P1|Email trống hoặc sai|Trống; abc; email quá dài|Gửi từng mẫu|Thông báo validation; không gửi yêu cầu hợp lệ
U|P0|Token thiếu, sai hoặc hết hạn|Không token; token sửa; token hết hạn|Mở reset; thử đổi mật khẩu|Không đổi được mật khẩu; có hướng dẫn xin link mới|A
U|P0|Token đã sử dụng|Đã reset thành công|Mở lại link cũ; gửi lại|Bị từ chối; không đổi mật khẩu lần hai
C|P0|Hai tab dùng cùng token|Một token hợp lệ|Gửi hai mật khẩu mới từ hai tab cùng lúc|Chỉ một lần tiêu thụ thành công; không nhận cả hai|S
B|P1|Validation mật khẩu mới|Ngắn/quá dài/không khớp/chỉ khoảng trắng|Gửi từng mẫu rồi sửa hợp lệ|Không đổi khi sai; mẫu hợp lệ thành công theo policy thực tế
U|P1|Giới hạn yêu cầu và lỗi mail|Gửi vượt cấu hình; giả lập mail timeout|Gửi liên tiếp; quan sát; thử lại sau hạn|Có 429/lỗi phù hợp; không báo đã đổi mật khẩu; không lộ cấu hình mail|E
""")

group("PROFILE", "Hồ sơ cá nhân", "Khách và nhân viên đã đăng nhập", "/account/profile", "Khách A/B có số điện thoại khác nhau.", r"""
H|P1|Đọc hồ sơ đúng người|Lần lượt khách A và nhân viên|Mở hồ sơ|Đúng tên, email, số điện thoại; nhân viên không có lịch sử đặt phòng cá nhân như khách
H|P1|Cập nhật hồ sơ|Tên/số mới hợp lệ|Sửa; lưu; F5|Dữ liệu tồn tại; header/sidebar đồng bộ
B|P1|Biên dữ liệu hồ sơ|Tên 1/2/100/101; số sai/8–15 chữ số|Lưu từng mẫu|Tên 2–100 hợp lệ; số theo schema hồ sơ; lỗi không ghi đè dữ liệu cũ
U|P1|Số điện thoại đã thuộc người khác|Điện thoại khách B|Sửa hồ sơ A bằng số B|Bị từ chối; cả hai hồ sơ giữ đúng dữ liệu
H|P2|Không có thay đổi|Mở form không sửa|Nhấn lưu|Thông báo phù hợp; không làm mất dữ liệu
U|P0|Can thiệp email và quyền|Thêm email/roles/status vào request bằng DevTools|Gửi cập nhật vượt quyền; mở lại hồ sơ|Không đổi quyền/trạng thái hoặc email qua form chỉ sửa tên/số|A
""")

group("CAT", "Danh mục và chi tiết hạng phòng", "Khách ẩn danh hoặc đăng nhập", "/room-types; /room-types/:roomTypeId", "Có hạng ACTIVE/INACTIVE; ảnh và tiện nghi thử.", r"""
H|P1|Danh mục công khai|Nhiều hạng đang hoạt động|Mở danh mục; phân trang nếu có|Đúng tên, ảnh, giá từ, sức chứa; không lẫn phòng vật lý với hạng phòng
U|P0|Ẩn hạng không hoạt động|Hạng INACTIVE|Tìm hạng; mở link cũ; thử đặt|Không cho tạo booking mới với hạng đã ngừng bán; lỗi rõ ràng
H|P1|Chi tiết đầy đủ|Hạng có mô tả, diện tích, giường, ảnh, tiện nghi|Mở chi tiết; xem các khối nội dung|Các khối cùng một hạng; ảnh không méo; không thiếu dữ liệu bắt buộc
H|P0|Đặt từ chi tiết|Khoảng ngày còn phòng; khách trong sức chứa|Chọn ngày/số khách; xem giá; tiếp tục|Giá và ngày chuyển đúng sang booking; chưa tự nhận phòng vật lý
U|P1|Chưa chọn đủ ngày|Chỉ một ngày hoặc chưa chọn|Thử tiếp tục đặt|Chặn hoặc hướng dẫn chọn đủ; không tạo booking lỗi
U|P0|Quá sức chứa tại chi tiết|Vượt người lớn hoặc trẻ em|Chọn khách vượt giới hạn; thử tiếp tục|Báo không đáp ứng; backend cũng từ chối nếu vượt UI
U|P2|Không có ảnh hoặc ảnh lỗi|Hạng không ảnh; URL ảnh 404|Mở danh mục và chi tiết|Có fallback; vẫn đọc và thao tác được|N
U|P1|API danh mục lỗi hoặc rỗng|Giả lập danh sách rỗng rồi 500|Mở trang; dùng thử lại khi có|Trạng thái rỗng/lỗi rõ; không giữ thẻ hạng từ request trước|N
""")

group("TIME", "Ngày lưu trú và mốc thời gian", "Khách; lễ tân", "/rooms/search; /bookings/new; trang nhận/trả phòng", "D = ngày khách sạn từ /api/v1/stay-policy; Clock backend kiểm soát cho các mốc giờ.", r"""
H|P0|Khoảng lưu trú theo đêm|05/11/2026 → 13/11/2026; Clock trước ngày nhận|Tìm kiếm; mở chi tiết; booking; xem tổng|8 đêm ở mọi màn hình; đêm 05–12/11; trả 13/11; không tính 7 vì 14:00→12:00|T
B|P0|Một đêm tối thiểu|D+1 → D+2|Tìm và đặt|1 đêm; tính tiền một đêm
U|P0|Ngày bằng nhau hoặc đảo ngược|D+2→D+2; D+3→D+1|Chọn UI; can thiệp query/request|Từ chối khoảng sai; không tự sửa ngầm rồi tính tiền khác|A
U|P0|Ngày nhận trong quá khứ|D-1→D+1|Thử đặt mới qua UI và request|Không tạo mới booking quá khứ|A
B|P1|Đổi tháng, năm và năm nhuận|30/11→02/12; 31/12→02/01; 28/02→01/03 năm nhuận|Tìm kiếm với Clock phù hợp|Lần lượt 2, 2, 2 đêm; không phụ thuộc số giờ thực|T
B|P0|Đặt hôm nay sau giờ nhận|D lúc 19:55; D→D+2; còn tồn|Tìm và tạo pending; xem kỳ ở và expiredAt|Được đặt trước cutoff 23:59:59 dù qua14:00;2 đêm; expiredAt là hạn trả tiền riêng, không tự CHECKED_IN|T
B|P0|Biên cutoff đặt cùng ngày|23:59:58; 23:59:59; 00:00 ngày mới|Gửi tạo booking cho ngày vừa kết thúc tại từng mốc|Trước23:59:59 được nếu đủ điều kiện; đúng23:59:59 chặn nhận hôm nay; qua00 ngày cũ bị chặn; ngày tương lai vẫn xem xét|T
B|P0|Nhận sớm trước 14:00|D lúc 13:59:59; phòng AVAILABLE|Nhận phòng có và không có lý do|Thiếu lý do bị chặn; đủ lý do và không xung đột được nhận|T
B|P0|Nhận đúng 14:00|D lúc 14:00:00|Check-in booking hợp lệ không nhập lý do sớm|Thành công; không còn bị coi là nhận sớm|T
B|P0|Khách đến muộn qua nửa đêm|Booking nhận D-1; hiện D lúc 05:59:59; trả sau D|Mở khách đến; check-in|Có thể nhận; giữ ngày đặt ban đầu, không dời giá/ngày|T
B|P0|Hết cửa sổ đến muộn|Booking nhận D-1; hiện D lúc 06:00:00|Tìm và cố check-in|Không còn đủ điều kiện đến muộn; không giao phòng|T
U|P0|Đến trước ngày đặt hoặc sau khi hết kỳ ở|NhậnD+1 nhưng hiệnD; hoặc nhậnD-2 nhưng hiệnD; hoặc trả<D|Thử check-in|Từ chối; không coi như ngoại lệ nhận sớm trong cùng ngày|T
B|P0|Biên trả phòng 12:00|Đến hạn D; lúc 12:00:00 rồi 12:00:01|Thử checkout không ghi chú ở từng mốc|Đúng 12:00 được; sau 12:00 cần ghi chú xử lý muộn; không tự cộng đêm|T
B|P0|Không tự checkout hoặc giải phóng phòng|Khách CHECKED_IN quá hạn; qua nửa đêm|Chưa thao tác checkout; xem phòng và tìm ngày mới|Vẫn đang ở và chiếm tồn; chỉ checkout thực tế mới chuyển DIRTY|T
B|P1|Múi giờ và đồng hồ máy khách sai|Browser UTC/US; chỉnh giờ máy khách lệch; server GMT+7|Mở tìm kiếm và nhận/trả phòng|Ngày/mốc khách sạn theo policy; không đổi quy tắc vì giờ máy khách|T
H|P1|Thời điểm thực tế và policy|Nhận/trả thử có Clock biết trước|Đối chiếu policy; làm thủ tục; xem thời gian thực tế nếu có|Lưu checked_in_at/checked_out_at đúng instant UTC; hiển thị theo giờ khách sạn; dữ liệu cũ không bịa giờ|A
""")

group("INV", "Tồn phòng theo từng đêm", "Khách; người đối soát", "/rooms/search", "Fixture INV biệt lập; đối chiếu chỉ đọc DB/API theo hướng dẫn, không sửa số trên UI.", r"""
H|P0|Đếm theo đúng hạng|Hai hạng có số phòng vật lý khác nhau|Tìm cùng khoảng; so thẻ với dữ liệu chuẩn|Mỗi thẻ dùng phòng thuộc đúng roomTypeId; không lấy tổng toàn khách sạn|A
H|P0|Trừ booking CONFIRMED|3 phòng bán được; 1 booking trùng một đêm|Tìm khoảng có đêm đó|Còn 2; không chỉ đếm trạng thái AVAILABLE hiện tại|A
H|P0|Giữ chỗ PENDING còn hạn|3 phòng; 1 pending chưa expiredAt|Tìm khoảng trùng|Còn 2 trong thời gian giữ chỗ|A
B|P0|PENDING hết hạn đúng instant|3 phòng; pending với expiredAt biết trước|Tìm trước hạn rồi đúng hạn, chưa chạy scheduler|Đến hạn không giữ chỗ nữa; số tăng 1 dù status chưa được job đổi|T
H|P0|Booking không chiếm tồn|CANCELLED; EXPIRED; CHECKED_OUT|Tìm khoảng từng booking|Không trừ các trạng thái này khỏi tồn|A
H|P0|Lấy tồn nhỏ nhất từng đêm|3 phòng; đêm1 bận2, đêm2 bận1|Tìm cả hai đêm|Còn 1; không cộng gộp tất cả booking giao nhau rồi trả 0|A
B|P0|Ngày trả không chiếm đêm mới|Booking A trả D+2; B nhận D+2|Tìm/đặt B từ D+2|Không xung đột ngày theo [nhận,trả); nhận thực tế vẫn cần phòng đã dọn|A
U|P0|MAINTENANCE và OUT_OF_SERVICE|Đưa phòng vào hai trạng thái không bán được|Tìm lại cùng khoảng|Trừ khỏi nguồn phòng bán được; không giới thiệu đó là phòng sẵn sàng|A
H|P0|Phòng DIRTY/CLEANING cho ngày tương lai|Có phòng đang dọn, không booking/maintenance chặn kỳ tương lai|Tìm kỳ tương lai; thử giao phòng hôm nay|Không đồng nhất tồn bán theo ngày với khả năng giao chìa khóa; check-in chỉ phòng AVAILABLE|A
U|P0|Khách ở quá ngày trả|Một CHECKED_IN đã quá hạn chưa checkout|Tìm kỳ tương lai|Booking quá hạn vẫn chặn tồn đến khi xử lý; không bán giả một phòng|A
U|P0|Hết phòng và số không âm|Tất cả tồn đã bị giữ; thêm fixture dữ liệu thiếu tồn|Tìm kiếm và mở link đặt cũ|Không hiện thẻ còn phòng sai; tạo booking bị chặn; số không âm|A
H|P1|Phân trang sau lọc tồn|Trang đầu dữ liệu có hạng hết phòng; nhiều hạng còn phòng phía sau|Tìm và chuyển trang|Không trả trang rỗng giả; tổng/phân trang tính theo hạng đủ điều kiện|A
C|P0|Hai khách tranh phòng cuối|Chỉ còn 1 phòng; hai browser độc lập|Cùng gửi tạo pending cho cùng đêm|Chỉ một booking giữ được phòng; bên còn lại báo hết; không overbooking|S
C|P0|Thay đổi tồn khi đang xem kết quả|A mở kết quả; B giữ phòng cuối/manager bảo trì|A chọn phòng sau thay đổi|Backend kiểm tra lại; không tin số cache; refresh phản ánh tồn mới|S
""")

group("BOOK", "Tạo và sửa booking đang giữ", "Khách ẩn danh; khách A", "/bookings/new", "Hạng còn phòng; G là thông tin khách thử; không dùng CCCD thật.", r"""
H|P0|Booking ẩn danh|Ngày hợp lệ; G có tên, email, giấy tờ|Chọn hạng → thông tin → tiếp tục thanh toán|Một booking PENDING; mã, tổng tiền, hạn giữ và access token phục vụ truy cập riêng
H|P0|Booking khi đăng nhập|Khách A; dữ liệu trong sức chứa|Tạo booking; mở lịch sử A|Booking thuộc A; dữ liệu tự điền vẫn sửa được theo form
U|P1|Thông tin bắt buộc thiếu|Lần lượt thiếu tên/email/giấy tờ; chỉ khoảng trắng|Gửi từng mẫu|Báo đúng trường; chưa tạo booking
U|P1|Email và số điện thoại sai|Email sai; nếu nhập phone thì không đủ 10 số bắt đầu 0|Thử gửi|Validation rõ; không chấp nhận dữ liệu sai qua UI
B|P0|Sức chứa độc lập|Bằng/vượt maxAdults, maxChildren và maxGuests|Gửi từng mẫu; kể cả sửa request|Bằng giới hạn được nhận; vượt bất kỳ giới hạn nào bị từ chối|A
U|P0|Số khách không hợp lệ|Adults=0/-1; children=-1; số lẻ; null|Thử query và request|Từ chối; không tạo booking hoặc giá sai|A
U|P0|Hạng mất hiệu lực giữa chừng|Đang mở form; manager ngừng bán hạng|Gửi form cũ|Bị từ chối; không giữ phòng hạng đã ngừng bán|S
C|P0|Nhấn tiếp tục nhiều lần|Mạng chậm; form hợp lệ|Double-click hoặc Enter liên tiếp|UI ngăn submit lặp; kiểm tra chỉ một booking từ một lần thao tác|N
H|P1|Quay lại sửa thông tin pending|Pending còn hạn của A/token đúng|Từ bước thanh toán quay lại sửa; gửi lại|Sửa cùng booking; không tạo thêm giữ chỗ; tổng và snapshot cập nhật
B|P0|Sửa pending không gia hạn giữ chỗ|Pending sắp hết hạn|Sửa tên/dịch vụ; xem expiredAt|Hạn giữ gốc không tăng; hết hạn vẫn chặn thanh toán|A
U|P0|Sửa pending đã hết hạn|expiredAt ≤ serverNow|Bấm quay lại/sửa rồi gửi|Bị từ chối; không tái giữ tồn bằng booking cũ|T
U|P0|Sửa booking của người khác|A dùng booking B; token thiếu/sai|Gửi update qua DevTools|404/403 phù hợp; không lộ nội dung hoặc sửa booking B|A
U|P0|Sửa sau khi đã confirmed|Callback đã xác nhận thanh toán|Gửi lại request sửa pending cũ|Không cho sửa theo luồng pending; tiền và snapshot cũ không bị ghi đè|A
H|P0|Thay đổi tổng khi đang có QR|Pending sửa thêm/bớt dịch vụ làm đổi tổng|Sửa; yêu cầu QR lại; xem QR cũ|Khoản cần trả đúng tổng mới; payment không còn phù hợp được xử lý lại; QR cũ không xác nhận sai số tiền|A
U|P1|Query đầu vào giả mạo|roomTypeId sai; ngày rác; khách NaN/quá lớn|Mở /bookings/new với từng query|Hướng dẫn chọn lại/lỗi có kiểm soát; không crash, không tạo dữ liệu rác
""")

group("ADD", "Dịch vụ bổ sung", "Khách; ADMIN/MANAGER khi quản trị", "/bookings/new; /add-on-services", "Có dịch vụ ACTIVE/INACTIVE; giá fixture 200.000đ/đơn vị.", r"""
H|P0|Chọn số lượng dịch vụ|Booking phòng 3.000.000đ; dịch vụ 200.000đ x2|Chọn dịch vụ; xem tổng; tạo booking|Dịch vụ 400.000đ; tổng 3.400.000đ; không nhân thêm số đêm nếu giá là theo đơn vị
H|P1|Bỏ chọn dịch vụ|Đã chọn x2|Bỏ chọn hoặc đưa lượng về trạng thái bỏ chọn của UI|Tổng giảm đúng 400.000đ; không còn dòng dịch vụ đó
U|P0|Số lượng sai hoặc dịch vụ lạ|Âm, số lẻ, rất lớn; addOnId không tồn tại/inactive|Gửi từng mẫu qua request|Từ chối dữ liệu không hợp lệ; không tràn số hoặc cộng dịch vụ ẩn|A
H|P1|Danh mục công khai chỉ bán dịch vụ active|Một active và một inactive|Mở chọn dịch vụ|Chỉ active có thể chọn mới
H|P1|Tạo dịch vụ|Code mới; tên 2–150; giá ≥0|Admin/manager tạo; tìm lại; mở form booking|Hiện đúng dữ liệu và giá; quyền khác không có thao tác tạo
U|P1|Dịch vụ trùng hoặc thiếu trường|Code trùng; tên rỗng/1/151; giá âm|Thử lưu từng mẫu|Lỗi rõ; không ghi một phần hoặc tạo trùng
H|P0|Đổi giá không đổi booking cũ|Booking đã có snapshot; dịch vụ tăng giá|Sửa giá; mở booking cũ và tạo booking mới|Booking cũ giữ giá đã chốt; booking mới dùng giá mới
H|P1|Ngừng bán và mở bán lại|Dịch vụ active|Đổi trạng thái; tìm/lọc; mở lại form khách|Không bán mới khi inactive; dữ liệu booking lịch sử vẫn có
H|P2|Tìm, lọc, sắp xếp, phân trang dịch vụ|Nhiều dịch vụ; bộ lọc có/không kết quả|Thử từng điều khiển; đổi lọc khi ở trang cuối|Dữ liệu, tổng, trang đúng; không bịa thao tác xóa nếu UI không có
""")

group("PAY", "Thanh toán QR và callback ngân hàng", "Chủ booking hoặc token ẩn danh đúng", "/bookings/new; /payments/:paymentId/result", "Bank sandbox/fixture webhook; không chuyển tiền thật; lưu booking/payment/providerTransactionId riêng.", r"""
H|P0|Tạo yêu cầu QR|Pending còn hạn; chưa trả tiền|Chuyển bước thanh toán; tạo QR|Đúng tài khoản nhận, nội dung mã payment, số còn thiếu và hạn thanh toán
H|P0|Thanh toán đủ trong hạn|Payment pending; booking còn hạn|Gửi callback hợp lệ đủ tiền; chờ web cập nhật|Payment thành công; booking CONFIRMED; paidAmount đúng; không tự CHECKED_IN|E
H|P0|Thanh toán nhiều phần|Tổng 1.000.000đ; lần đầu 400.000đ|Gửi lần1; xem web; gửi lần2 600.000đ với mã giao dịch khác|Lần1 còn thiếu 600.000đ; chỉ sau đủ tổng mới CONFIRMED; không cộng trùng|E
C|P0|Callback trùng|Một giao dịch đã nhận|Gửi lại cùng event/providerTransactionId nhiều lần|Một lần cộng tiền; không nhân payment/refund/dashboard|E
H|P0|Chuyển dư|Còn thiếu 1.000.000đ; chuyển 1.200.000đ|Gửi callback; xem booking và danh sách refund|Booking đủ tiền; thừa 200.000đ thành refund PENDING; chưa được coi là đã hoàn|E
U|P0|Tiền đến sau hạn|Pending đã hết hạn chính xác|Gửi tiền đúng mã sau hạn|Không hồi sinh booking; tạo xử lý hoàn tiền chờ duyệt theo policy|T
U|P0|Tiền đến sau hủy|Booking CANCELLED; payment cũ|Gửi callback đến muộn|Booking vẫn hủy; tiền được đối soát/refund, không CONFIRMED lại|E
U|P0|Webhook sai xác thực|API key sai hoặc thiếu|Gửi callback; quan sát web và monitor|Không cộng tiền/xác nhận; không lộ khóa; phản hồi lỗi phù hợp|E
U|P0|Sai chiều tiền hoặc tài khoản nhận|Outgoing; bank account không đúng cấu hình|Gửi từng callback|Không dùng để xác nhận booking; ghi nhận/loại theo handler, không cộng paidAmount|E
U|P0|Mã thanh toán thiếu hoặc lạ|Nội dung thiếu mã; mã không tồn tại|Gửi callback|Không gán tùy tiện cho booking khác; có dấu vết đối soát phù hợp|E
U|P0|Không có quyền xem payment|A dùng payment B; ẩn danh thiếu/sai token|Mở result và gọi status qua DevTools|Không lộ booking/khách/QR của B; không đổi trạng thái|A
H|P1|Tải lại và mở result trực tiếp|Payment hợp lệ và quyền đúng|F5; mở tab mới; quay lại từ bước đặt|Trạng thái backend là nguồn chính; không coi tải lại là giao dịch mới
B|P0|Hạn QR không vượt hold|Booking chỉ còn giữ 2 phút|Tạo QR; yêu cầu lại trước hết hạn|Hạn payment ≤ expiredAt booking; không kéo dài hold|A
H|P0|Yêu cầu lại cùng số tiền|Payment pending còn hạn, tổng không đổi|Nhấn yêu cầu lại/thực hiện lại request hợp lệ|Tái dùng yêu cầu còn phù hợp; không tạo nghĩa vụ thu tiền trùng|A
U|P0|Không có số dư cần thanh toán|Booking đã đủ tiền; hoặc trạng thái không cho trả|Thử tạo payment request|Không tạo QR số tiền 0/âm hoặc trả lại booking đã hoàn tất|A
H|P0|Trả thêm tiền gia hạn|Booking CONFIRMED hoặc CHECKED_IN có nợ mới|Tạo QR; gửi đủ tiền còn thiếu|PaidAmount tăng đúng; giữ nguyên trạng thái đang ở/đã xác nhận; không tạo booking mới|E
C|P0|Callback cạnh tranh với hết hạn|Một hold sắp hết hạn; Clock kiểm soát|Gửi callback sát expiredAt cùng tác vụ hết hạn|Kết quả nhất quán theo thời điểm xử lý; không vừa bán lại tồn vừa xác nhận cùng hold|T
U|P1|Mất mạng khi chờ thanh toán|Payment đang pending; callback vẫn tới backend|Ngắt mạng web; nối lại/tải lại|Đọc lại trạng thái thành công thực tế; không yêu cầu trả lần hai|N
U|P1|Hết hạn không thanh toán|Không callback tới hết hạn|Theo dõi countdown và thử tiếp tục|Thông báo hết hạn; không còn giữ phòng; hướng dẫn đặt lại, không giả thành công|T
""")

group("HIST", "Lịch sử đặt phòng của khách", "Khách A roles=[]", "/account/bookings; /account/bookings/:bookingCode; /account/profile?section=bookings", "Có booking A/B và nhiều trạng thái.", r"""
H|P1|Lịch sử đúng chủ|A có nhiều booking|Mở hai lối vào lịch sử|Chỉ booking thuộc A; số tiền/ngày/hạng/trạng thái nhất quán
H|P1|Chi tiết và deep link mã booking|Mã thuộc A|Nhấn xem; dán link; F5|Mở đúng chi tiết; không mất quyền sau refresh hợp lệ
H|P1|Lọc và phân trang lịch sử|Nhiều trạng thái, nhiều trang|Lọc trạng thái; chuyển trang; xóa bộ lọc|Tổng và dữ liệu đúng; reset trang phù hợp; có empty state
U|P0|Xem mã booking người khác|Mã B; mã không tồn tại|A nhập trực tiếp URL/request|Không lộ dữ liệu B; hiển thị không tìm thấy/không quyền|A
H|P1|Nút hành động theo trạng thái|PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, EXPIRED|Mở từng booking|Chỉ hiển thị/hỗ trợ hủy, sửa, gia hạn, trả tiền theo trạng thái thực; backend kiểm lại
U|P1|Booking ẩn danh và lịch sử tài khoản|Đặt ẩn danh rồi đăng nhập A|Mở lịch sử; thử xem bằng mã không token|Không tự suy diễn quyền chỉ vì cùng email; chỉ hiện nếu thực tế đã liên kết hợp lệ
U|P1|Lỗi tải và dữ liệu đổi ở tab khác|Tab B cập nhật booking; tab A đang xem|Focus/tải lại A; giả lập 500 rồi thử lại|Dữ liệu mới thay thế dữ liệu cũ; lỗi không làm mất bộ lọc hoặc lộ booking khác|N
""")

group("CANCEL", "Hủy booking và tính hoàn", "Khách sở hữu booking", "/account/bookings; /account/profile?section=bookings", "Clock kiểm soát; giờ nhận theo policy; booking có giao dịch SUCCESS khi cần hoàn.", r"""
H|P0|Hủy pending chưa trả tiền|Pending của A còn hạn|Mở hủy; xác nhận; tìm tồn lại|CANCELLED; giải phóng giữ chỗ; không tạo hoàn tiền giả
U|P0|Pending đã nhận một phần tiền|Pending có tiền nhận nhưng chưa đủ|Xem cảnh báo/chính sách; hủy; đối soát giao dịch|Không báo đã hoàn tự động; khoản nhận một phần cần đối soát theo giới hạn hiện tại|E
B|P0|Hủy trước hoặc đúng 48 giờ|Confirmed; total 2.000.000đ; còn 48h và 48h+1s|Mở hủy; nhập bank hợp lệ; xác nhận|Refund dự kiến 2.000.000đ; tạo PENDING; booking hủy|T
B|P0|Hủy dưới 48 giờ và đúng 24 giờ|Còn 47h59m59s rồi 24h|Hủy fixture riêng từng mốc|Hoàn 50% tổng theo policy; không làm tròn giờ thành 100%|T
B|P0|Hủy dưới 24 giờ|Còn 23h59m59s|Mở và thử gửi hủy|Bị từ chối; không đổi booking hoặc tạo refund|T
U|P1|Thiếu thông tin ngân hàng khi có hoàn|Confirmed đủ điều kiện; thiếu tên ngân hàng/chủ/số TK|Gửi từng mẫu|Chặn thiếu trường; không tạo refund thiếu đích nhận
U|P0|Trạng thái không được hủy|CHECKED_IN, CHECKED_OUT, CANCELLED, EXPIRED|Thử nút/DevTools gửi hủy|Backend từ chối; không đổi trạng thái hay hoàn lần nữa|A
U|P0|Hủy booking người khác|Khách A dùng booking B|Gửi hủy với bank của A|Không được phép; dữ liệu B không đổi|A
C|P0|Hai tab hủy cùng booking|Confirmed đủ điều kiện|Xác nhận đồng thời từ hai tab|Một lần chuyển trạng thái; tối đa một nghĩa vụ refund cho lần hủy|S
U|P0|Sửa số tiền hoàn trên request|Backend tính được 50%; client gửi 100%|Can thiệp request và gửi|Số hoàn do backend quyết định; không nhận giá trị client giả mạo|A
H|P1|Đóng modal hủy|Mở modal chưa xác nhận|Đóng/Cancel rồi mở lại|Booking không thay đổi; không phát sinh refund
U|P0|Hủy sau gia hạn hoặc nhiều giao dịch|Total và paidAmount khác nhau; nhiều payment SUCCESS|Xem quote và đối chiếu từng giao dịch trước khi xác nhận|Phát hiện hoàn vượt tiền thực nhận/hoàn sai payment là lỗi nghiệp vụ cần xử lý; không ghi Pass chỉ vì API 200|A
""")

group("EXT", "Gia hạn lưu trú", "Khách là bookedBy của booking", "/account/bookings; /account/profile?section=bookings", "Booking CONFIRMED/CHECKED_IN của A; có kỳ gia hạn còn và hết phòng.", r"""
H|P0|Gia hạn booking confirmed|Ngày trả D+3; xin thêm tới D+5 còn tồn|Mở gia hạn; chọn ngày; xác nhận|Ngày trả D+5; thêm 2 đêm theo giá hiện tại; vẫn CONFIRMED
H|P0|Gia hạn khi đang ở|CHECKED_IN có phòng gán không xung đột|Gia hạn tới ngày hợp lệ|Giữ đúng phòng đang ở; vẫn CHECKED_IN; nợ tăng đúng
B|P0|Ngày gia hạn không tiến lên|Ngày mới bằng/trước ngày trả hiện tại|Thử chọn và gửi|Bị từ chối; không tạo đêm giá 0/âm
U|P0|Hết tồn một đêm trong đoạn thêm|Chỉ một đêm thêm bị hết phòng|Yêu cầu gia hạn nhiều đêm|Từ chối toàn bộ; không cập nhật dở ngày/giá
U|P0|Phòng đang ở xung đột booking kế tiếp|Còn phòng khác cùng hạng nhưng phòng hiện tại đã gán khách sau|Gia hạn|Bị từ chối; không tự chuyển sang phòng khác hoặc đẩy booking kế tiếp
H|P0|Giữ snapshot cũ, tính giá mới cho đêm thêm|Giá cũ khác giá hiện tại; thêm 2 đêm x400.000đ|Đổi giá rồi gia hạn|Đêm cũ không đổi; thêm 800.000đ; tổng/nợ khớp
U|P0|Booking không được gia hạn|PENDING, EXPIRED, CANCELLED, CHECKED_OUT|Gửi gia hạn|Từ chối mọi trạng thái không hợp lệ|A
U|P0|Không phải chủ booking|A gia hạn B; hoặc nhân viên không phải bookedBy|Gửi request trực tiếp|Không được gia hạn thay qua endpoint chỉ cho chủ; không suy diễn nhân viên được mọi quyền|A
C|P0|Hai yêu cầu gia hạn cùng lúc|Hai tab cùng booking; ngày mới khác nhau|Gửi đồng thời|Không mất cập nhật/nhân đôi đêm; kết quả giá và ngày là một lịch hợp lệ|S
H|P0|Thanh toán khoản gia hạn|Có nợ mới; hoặc đã đủ tiền sau QR|Trả QR; hoặc để thu đúng dư nợ khi checkout|Không thu lại tiền cũ; status lưu trú được giữ; số còn thiếu giảm đúng|E
""")

group("ARR", "Danh sách khách đến", "RECEPTIONIST; ADMIN/MANAGER chỉ quyền được cấp", "/front-desk/arrivals/today", "D từ server; booking đến hôm nay và hôm qua sát cutoff.", r"""
H|P1|Danh sách và thống kê khách đến|Có nhiều trạng thái/ngày đến|Mở; tìm/lọc/phân trang theo UI|Đúng phạm vi và tổng; không lẫn khách tương lai với khách đến hôm nay
B|P0|Bổ sung khách đến muộn hôm qua|D trước 06:00 rồi đúng 06:00|Mở danh sách ở hai mốc|Chỉ bổ sung booking hôm qua đủ điều kiện trong cửa sổ muộn|T
H|P0|Từ danh sách sang check-in|Lễ tân; booking hợp lệ|Ghi/sao chép mã từ danh sách; mở menu Check-in; nhập mã và tìm phòng|Đúng mã và cửa sổ; nhận qua màn hình riêng, không giả định có nút check-in trên bảng khách đến
U|P0|Admin/manager không tự có quyền nhận phòng|ADMIN hoặc MANAGER chỉ có role đó|Mở danh sách; thử hành động và URL check-in|Đọc được danh sách; không vượt quyền check-in riêng của lễ tân
H|P1|Sau khi đã nhận phòng|Lễ tân nhận ở tab khác|Refresh danh sách|Trạng thái/thống kê cập nhật; không còn gợi ý nhận lần hai
U|P1|Không có khách đến hoặc API lỗi|Fixture rỗng; sau đó 500|Mở và thử lại|Empty/error rõ; không hiện dữ liệu ngày trước như dữ liệu hôm nay|N
""")

group("CIN", "Nhận phòng", "RECEPTIONIST", "/front-desk/check-in", "Booking đủ tiền CONFIRMED; phòng cùng hạng AVAILABLE; đúng cửa sổ giờ.", r"""
H|P0|Nhận phòng chuẩn|Mã booking hợp lệ; từ 14:00 ngày nhận|Tra mã; tìm phòng khả dụng; chọn phòng; xác nhận|Booking CHECKED_IN, gán đúng phòng; phòng OCCUPIED; ghi thời điểm thực tế
U|P1|Mã rỗng hoặc không tồn tại|Trống; mã giả|Tra từng mẫu|Validation/không tìm thấy; không hiển thị nhầm khách trước
U|P0|Booking chưa thanh toán đủ|PENDING có/không tiền một phần|Tra và cố nhận|Không nhận phòng; hướng dẫn xử lý thanh toán
U|P0|Nhận lại hoặc booking đã kết thúc|CHECKED_IN/CHECKED_OUT/CANCELLED/EXPIRED|Gửi nhận phòng lần nữa|Không đổi phòng/ghi lại giờ; không bàn giao hai phòng|A
U|P0|Chọn phòng sai hạng|RoomId của hạng khác|Sửa request chọn phòng|Backend từ chối dù phòng AVAILABLE|A
U|P0|Chọn phòng không sẵn sàng|OCCUPIED, DIRTY, CLEANING, MAINTENANCE, OUT_OF_SERVICE|Thử chọn/gửi từng trạng thái|Không được nhận; chỉ AVAILABLE hợp lệ và không xung đột|A
U|P0|Phòng được gán booking xung đột|AVAILABLE hiện tại nhưng có lịch gán xung đột kỳ ở|Tìm phòng; gửi roomId trực tiếp|Không cấp phòng xung đột; không dựa riêng status hiện tại|A
B|P1|Lý do nhận sớm|Trước 14:00; lý do trắng/500/501 ký tự|Gửi từng mẫu|Cần lý do có nội dung ≤500; lỗi không thay trạng thái|T
C|P0|Hai lễ tân lấy cùng phòng|Hai booking cùng hạng; một phòng AVAILABLE|Hai phiên nhận cùng roomId|Chỉ một lần chiếm phòng; bên còn lại báo xung đột|S
C|P0|Hai lễ tân nhận cùng booking|Một booking; hai phòng khác nhau|Xác nhận đồng thời|Chỉ một phòng được gán/OCCUPIED cho booking; phòng còn lại không bị giữ nhầm|S
U|P0|Thiếu/sai quyền check-in|Khách, housekeeping, ADMIN-only, MANAGER-only|Mở URL và gửi API bằng phiên tương ứng|403/redirect đúng; không thay booking/phòng|A
U|P1|Mất phản hồi sau khi đã xử lý|Backend thành công nhưng web timeout|Tải lại/tra lại rồi thử thao tác|Hiển thị trạng thái đã nhận; không gán thêm hoặc ghi thời điểm lần hai|N
""")

group("COUT", "Trả phòng thông thường và thu tiền", "RECEPTIONIST / MANAGER / ADMIN", "/front-desk/departures/today", "Booking CHECKED_IN; phòng OCCUPIED; đến ngày trả D; số dư từ backend.", r"""
H|P0|Checkout đã đủ tiền, không phí|Dư nợ 0; phí 0|Mở trả phòng; xác nhận|CHECKED_OUT; phòng DIRTY; ghi giờ thực tế; không tạo phiếu thu 0/thu trùng
H|P0|Thu dư nợ và phí phát sinh|Dư nợ 500.000đ; phí 150.000đ có lý do|Nhập thu đúng 650.000đ; xác nhận|Phiếu CASH SUCCESS 650.000đ, người thu/giờ; tổng và paidAmount khớp; checkout thành công
U|P0|Thu thiếu hoặc thừa|Cần 650.000đ; nhập 649.999/650.001|Thử xác nhận mỗi mẫu|Không checkout hoặc ghi phiếu thu sai; yêu cầu đúng số cần thu
U|P0|Tiền âm, số lẻ hoặc quá giới hạn|Phí/thu=-1, 1.5, >9.999.999.999|Thử UI và request|Validation backend; không làm tròn ngầm hoặc tràn số|A
U|P1|Có phí nhưng thiếu lý do|Phí >0; lý do trống/độ dài quá giới hạn|Thử gửi|Chặn trước cập nhật; giữ nguyên trạng thái và số tiền
B|P0|Trả muộn cần ghi chú|Sau 12:00 ngày trả; hoặc đã quá ngày|Thử thiếu ghi chú rồi điền lý do|Thiếu bị chặn; có lý do được xử lý; không tự thu thêm một đêm|T
U|P0|Checkout sai luồng ngày|Chưa đến ngày trả và chọn luồng thường|Thử thao tác|Phải dùng trả sớm để tính credit; không bỏ qua chính sách hoàn bằng luồng thường|T
U|P0|Booking/phòng không hợp lệ|Booking CONFIRMED hoặc đã checkout; room không OCCUPIED|Gửi checkout|Từ chối; không tạo thu/đổi phòng sai|A
C|P0|Hai nhân viên checkout cùng booking|Booking còn dư nợ|Xác nhận đồng thời từ hai phiên|Một checkout và một phiếu thu; không nhân đôi thu tiền|S
C|P0|Khách trả QR lúc nhân viên thu tiền|Cùng khoản dư nợ; callback đến trong lúc mở modal|Gửi callback rồi submit form cũ|Recheck dư nợ; không vừa thu bank vừa thu cash cho cùng khoản|E
H|P0|Tính nguyên tử khi lỗi ghi phiếu thu|Giả lập lỗi transaction trong môi trường test|Thực hiện checkout có tiền mặt|Rollback đồng bộ booking/phòng/phiếu thu; không báo thành công một phần|E
H|P1|Đủ ba vai trò được checkout|Ba fixture độc lập; R, M, A|Lần lượt đăng nhập và trả phòng|Tất cả được phép theo quyền; các vai trò khác bị chặn
""")

group("EARLY", "Trả phòng sớm", "RECEPTIONIST / MANAGER / ADMIN", "/front-desk/departures/today", "Booking CHECKED_IN; ngày D trước ngày trả dự kiến; room OCCUPIED.", r"""
H|P0|Quote trả sớm theo đêm chưa dùng|Ngày thực tế D; các đêm snapshot rõ|Mở trả sớm; xem bảng tính trước xác nhận|Chỉ đêm stayDate ≥D chưa dùng được tính; phí 20%, credit 80%; add-on không được hoàn theo đêm
H|P0|Credit lớn hơn nợ và phí|U=2.000.000đ; nợ300.000đ; phí200.000đ|Nhập bank hợp lệ; xác nhận|Credit1.600.000đ; refund PENDING1.100.000đ; không thu cash; CHECKED_OUT/DIRTY
H|P0|Credit nhỏ hơn nợ và phí|U=1.000.000đ; tổng nợ+phí1.200.000đ|Thu đúng400.000đ; xác nhận|Không refund; một phiếu thu400.000đ; checkout thành công
B|P0|Credit bằng khoản cần bù|Credit800.000đ; nợ+phí800.000đ|Xác nhận không thu thêm|Không thu/hoàn 0; checkout thành công
U|P0|Ngày thực tế không phải hôm nay|Chọn ngày quá khứ/tương lai bằng request|Gửi execute trả sớm|Từ chối; không lùi hoặc ghi trước giờ thực tế|A
U|P0|Ngoài khoảng trả sớm|Ngày <checkIn hoặc ≥checkOut dự kiến|Gửi quote/execute theo validation|Không áp dụng trả sớm sai khoảng; không hoàn đêm không tồn tại|A
U|P1|Thiếu bank khi thực có refund|Quote refund>0; thiếu trường nhận tiền|Gửi xác nhận|Từ chối; không tạo refund thiếu đích nhận
U|P0|Sai số tiền thu hoặc phí|Quote cần thu400.000đ; nhập khác; phí âm/lẻ|Gửi xác nhận|Backend tính lại và từ chối số sai; không tin quote/client cũ|A
C|P0|Quote cũ hoặc gửi hai lần|Quote xong có giao dịch mới; hoặc hai tab|Gửi form cũ/đồng thời|Tính lại theo dữ liệu hiện tại; một checkout; không tạo hai khoản thu/hoàn|S
H|P0|Đối soát sau trả sớm|Checkout sớm thành công|Xem lịch sử booking, phòng, refund/phiếu thu, dashboard|Các số khớp công thức; refund pending chưa trừ dòng tiền thực đến khi hoàn SUCCESS
""")

group("HOUSE", "Buồng phòng và vòng đời phòng", "HOUSEKEEPER", "/housekeeping", "Có phòng DIRTY, CLEANING, AVAILABLE, OCCUPIED.", r"""
H|P0|Bắt đầu dọn|Phòng DIRTY vừa checkout|Chọn bắt đầu dọn; tải lại|DIRTY→CLEANING; không làm AVAILABLE ngay
H|P0|Hoàn tất dọn|Phòng CLEANING|Nhấn hoàn tất; lễ tân tìm phòng lại|CLEANING→AVAILABLE; phòng xuất hiện trong phòng có thể giao khi không có xung đột
U|P0|Chuyển trạng thái sai|AVAILABLE/OCCUPIED/MAINTENANCE; hoặc DIRTY→AVAILABLE trực tiếp|Thử thao tác và API|Từ chối bước nhảy không hợp lệ; không đẩy khách đang ở ra khỏi phòng|A
C|P0|Hai người cùng bắt đầu dọn|Một phòng DIRTY|Hai phiên nhấn bắt đầu|Một chuyển trạng thái; bên còn lại nhận dữ liệu mới/xung đột; không ghi hai phiên làm việc sai|S
C|P0|Hoàn tất trên dữ liệu cũ|Phòng đã được phiên khác hoàn tất|Submit lại từ tab cũ|Không đổi trạng thái trái phép hoặc tác động booking khác|S
H|P1|Danh sách, bộ lọc và làm mới|Có phòng hai trạng thái|Mở; lọc/tìm theo UI; refresh|Số phòng, trạng thái, tổng thống nhất; empty state đúng
U|P0|Vai trò khác mở buồng phòng|Khách; lễ tân; manager-only; admin-only|Mở URL và gọi thao tác|Không có quyền theo cấu hình hiện tại; không suy diễn cấp trên tự có quyền này|A
H|P0|Vòng quay trong cùng ngày|Khách cũ trả12:00; khách mới đến14:00|Checkout→bắt đầu dọn→hoàn tất→check-in khách mới|Chỉ nhận khách mới sau AVAILABLE; giữ lịch theo ngày và giờ bàn giao hợp lệ|T
""")

group("ROOM", "Quản lý phòng vật lý", "ADMIN / MANAGER", "/management/rooms", "Dữ liệu UAT riêng; không xóa phòng có lịch sử thật.", r"""
H|P1|Tạo phòng mới|Số phòng mới; hạng hợp lệ; tầng hợp lệ|Tạo; tìm lại; mở chi tiết|Một phòng đúng hạng; mặc định AVAILABLE; tồn liên quan cập nhật
U|P1|Trùng số phòng|Số đã tồn tại|Tạo hoặc đổi một phòng khác về số này|Báo trùng; không tạo bản sao hoặc đổi nhầm phòng
B|P1|Định dạng số và tầng|Số1–30 ký tự A-Z/a-z/0-9/_/-; mẫu sai; tầng -10/200 và ngoài biên|Lưu từng mẫu|Chấp nhận đúng biên; từ chối ký tự/độ dài/tầng không hợp lệ
H|P1|Sửa phòng AVAILABLE|Phòng không có ràng buộc ngăn sửa|Sửa tầng/ghi chú hợp lệ; F5|Lưu đúng; không tự đổi hạng/trạng thái ngoài yêu cầu
U|P0|Sửa hoặc xóa phòng chưa AVAILABLE|Lần lượt OCCUPIED/DIRTY/CLEANING/MAINTENANCE/OOS|Thử thao tác qua UI và request|Backend chặn; không làm mất phòng đang phục vụ|A
U|P0|Đổi hạng khi hạng cũ có nhu cầu đang hiệu lực|Phòng AVAILABLE nhưng hạng có booking active|Yêu cầu đổi hạng|Không làm hụt tồn của booking đã bán; giữ quan hệ cũ khi bị chặn
H|P1|Xóa phòng thử không có lịch sử|AVAILABLE; không FK/demand chặn|Xóa; xác nhận; tìm lại|Phòng biến mất; tổng và tồn giảm đúng một
U|P0|Xóa phòng có ràng buộc lịch sử|Phòng có booking/tham chiếu|Yêu cầu xóa|Lỗi nghiệp vụ rõ; không xóa dây chuyền dữ liệu booking
B|P2|Ghi chú và dữ liệu trống|Ghi chú2000/2001; thiếu số/hạng; tầng số lẻ|Thử lưu|Đúng giới hạn; dữ liệu sai không ghi một phần
H|P1|Tìm kiếm, lọc, phân trang, đồng bộ|Nhiều hạng/trạng thái; tạo/sửa ở tab khác|Thử bộ lọc; về cuối trang; refresh/focus|Tổng và hàng đúng dữ liệu DB; không tự seed thêm phòng khi khởi động lại backend
""")

group("RTYPE", "Quản lý hạng phòng và ảnh", "ADMIN / MANAGER", "/management/room-types", "Amenity có sẵn; MinIO test hoạt động; ảnh JPG/PNG thử không có dữ liệu riêng.", r"""
H|P0|Tạo hạng đầy đủ|Code mới; giá/sức chứa/diện tích hợp lệ; tiện nghi|Tạo; mở catalog và chi tiết khách|Một hạng đúng dữ liệu; ACTIVE bán được khi có phòng vật lý
U|P1|Mã hạng trùng hoặc sai|Trùng; chữ thường/ký tự ngoài A-Z0-9_-; >50|Lưu từng mẫu|Không tạo trùng; lỗi định dạng rõ
B|P1|Biên văn bản|Tên150/151; shortDesc500/501; desc10000/10001; giường100/101|Lưu từng mẫu|Chấp nhận đúng tối đa; không cắt ngầm dữ liệu
B|P0|Biên giá và sức chứa|Giá0/âm; adults1/100/101; child0/100/101; maxGuests|Thử lưu từng tổ hợp|Giá không âm; adults≤maxGuests≤adults+children; giới hạn số đúng
B|P1|Diện tích và tiện nghi|0.01/9999.99 và ngoài biên; amenityId sai|Lưu từng mẫu|Diện tích đúng giới hạn; không nhận tiện nghi không tồn tại|A
H|P1|Tải ảnh hợp lệ|JPEG/PNG thực, ≤5MB, ≤25MP|Upload; chọn/sắp ảnh nếu UI hỗ trợ; lưu; mở khách|Ảnh lưu bền; thumbnail/ảnh đầu đúng; không lẫn ảnh hạng khác
U|P0|Giả định dạng hoặc ảnh hỏng|Text đổi đuôi .jpg; SVG/GIF; MIME giả; file hỏng|Upload từng mẫu|Kiểm nội dung thực; chỉ JPEG/PNG hợp lệ; không lưu dữ liệu giả ảnh
B|P1|Giới hạn upload|File0 byte; >5MB; >25MP; ảnh thứ21|Thử từng giới hạn|Từ chối đúng giới hạn; không mất ảnh đã lưu trước đó
U|P1|Kho ảnh lỗi và URL giả|MinIO timeout; tự gửi URL ảnh mới không thuộc kho|Lưu/upload với fixture|Báo lỗi có kiểm soát; không ghi URL ngoài tùy ý vào hạng mới|E
H|P0|Sửa giá và ngừng bán|Có booking cũ snapshot|Đổi thông tin/giá; INACTIVE; mở booking cũ và tìm mới|Booking cũ giữ giá; hạng ngừng bán không được đặt mới
U|P0|Xóa hạng có ràng buộc|Có phòng/booking/quy tắc giá tham chiếu|Yêu cầu xóa|Chặn theo ràng buộc; không xóa dữ liệu lịch sử
H|P1|Hạng không có ràng buộc và danh sách|Hạng UAT trống; nhiều kết quả|Xóa fixture trống nếu UI có; tìm/lọc/trang|Danh sách và tổng đúng; không áp dụng xóa cho hạng đang vận hành
""")

group("PRICE", "Quy tắc giá và cách tính tiền đêm", "ADMIN / MANAGER; khách đối chiếu giá", "/price-rules; /rooms/search", "Hạng thử giá gốc1.000.000đ; rule riêng, không sửa giá hạng đang dùng thật.", r"""
H|P0|Giá gốc không có rule|Hai đêm không có rule áp dụng|Tìm; xem chi tiết từng đêm; tạo booking|1.000.000đ/đêm, tổng2.000.000đ; VND không có phần thập phân
H|P0|Cuối tuần tăng giá|WEEKEND +10%; thứ bảy/chủ nhật|Tạo rule; tìm kỳ gồm ngày thường/cuối tuần|Chỉ T7/CN đủ ngày hiệu lực thành1.100.000đ; không áp T6 như weekend
H|P0|Mùa tăng/giảm và khuyến mãi|SEASONAL +20%; PROMOTION giảm10% cùng đêm|Tìm ngày đủ điều kiện|Chọn modifier mùa rồi promotion:1.000.000×1.2×0.9=1.080.000đ
H|P0|Ưu tiên loại rule, không cộng mọi phụ phí|HOLIDAY/WEEKEND/SEASONAL cùng áp dụng; holiday active|Tìm ngày trùng cả ba|Chọn một modifier HOLIDAY > WEEKEND > SEASONAL; sau đó một promotion tốt nhất; không cộng cả ba
H|P1|Cùng loại rule và promotion tốt nhất|Hai rule cùng loại; khác priority; hai promotion|Tìm; xem applied rules|Modifier theo priority và tie-break backend; promotion có mức giảm lớn nhất; kết quả lặp lại ổn định
B|P0|Ngày hiệu lực rule và ngày trả|startDate=endDate một ngày; booking trả đúng ngày đó|So đêm trước, trong, sau ngày hiệu lực|Rule gồm cả start/end cho stayDate; ngày trả không phải đêm tính phí
B|P1|Giới hạn giá trị điều chỉnh|Phần trăm0/100/101; số tiền âm; priority0/1000/1001|Tạo/sửa từng mẫu|0–100%; số tiền không âm; priority0–1000; lỗi không lưu rule
U|P0|Hướng điều chỉnh không hợp lệ|PROMOTION tăng; WEEKEND/HOLIDAY giảm; BASE qua API|Thử form và can thiệp request|Từ chối hướng sai; BASE không quản lý qua API này|A
U|P1|Rule thiếu trường hoặc ngày đảo|Tên1/151; thiếu hạng/loại/ngày; start>end|Lưu từng mẫu|Validation đúng; không phát sinh rule nửa vời
H|P0|Giảm lớn hơn giá và làm tròn|Promotion fixed>giá; giá lẻ nhân %|Tìm và đặt|Giá cuối không âm; giảm bị chặn ở tiền hiện có; chia phần trăm theo số nguyên VND
H|P0|Sửa/ngừng rule không đổi snapshot cũ|Booking đã tạo trước thay đổi|Sửa/tắt rule; tìm mới và xem booking cũ|Giá tìm mới cập nhật; đêm của booking cũ giữ giá đã chốt
H|P1|Tổng các đêm và bộ lọc quản trị|Kỳ nhiều loại đêm; nhiều rule|Mở popover giá; cộng từng đêm; lọc/sắp/trang rule|Tổng đêm bằng tổng phòng; rule active/hạng/ngày đúng; không lẫn giá add-on
""")

group("HOL", "Lịch ngày lễ", "ADMIN / MANAGER", "/price-rules", "Mục quản lý lịch ngày lễ; rule HOLIDAY cho hạng thử.", r"""
H|P1|Tạo và sửa lịch lễ|Tên2–150; start≤end|Tạo; tìm lại; sửa khoảng ngày|Lưu đúng; hiển thị trạng thái active
B|P0|Lễ một ngày và biên hai đầu|start=end; sau đó dải3 ngày|Tìm từng stayDate quanh dải|Ngày đầu/cuối được coi ngày lễ; ngoài dải không áp dụng
U|P1|Lịch lễ không hợp lệ|Tên trống/1/151; thiếu ngày; start>end|Lưu từng mẫu|Lỗi rõ; không tạo lịch sai
H|P0|Tắt lịch lễ|Có rule HOLIDAY và lịch lễ active|Tắt lịch; tìm lại ngày trong dải|Không áp HOLIDAY chỉ vì rule có khoảng ngày; cần lịch lễ active
H|P1|Tìm, lọc và phân trang lễ|Nhiều lịch active/inactive|Thử các điều khiển có trên UI|Dữ liệu/tổng đúng; đổi lọc không kẹt trang rỗng giả
U|P0|Quyền và trường API không hỗ trợ|Khách gọi CRUD; thêm field tùy ý vào request manager|Thử mỗi mẫu|Khách bị chặn; trường không được hỗ trợ bị validation; không tự có lịch lặp hàng năm nếu chưa triển khai|A
""")

group("MAINT", "Bảo trì và chi phí", "MANAGER", "/maintenance; /rooms/maintenance", "Phòng UAT AVAILABLE; Clock D; hai tài khoản manager để thử revision.", r"""
H|P0|Tạo yêu cầu bảo trì|Phòng AVAILABLE; title/description hợp lệ|Tạo ticket|Ticket PENDING; phòng MAINTENANCE; giảm tồn bán; cùng transaction
U|P0|Phòng không phù hợp hoặc ticket trùng|Phòng OCCUPIED/DIRTY/CLEANING/OOS; phòng đã có ticket mở|Thử tạo|Từ chối; không đổi sai trạng thái hoặc tạo hai ticket mở
B|P1|Biên nội dung ticket|Tiêu đề trống/150/151; mô tả2000/2001|Tạo/sửa theo UI|Nhận đúng giới hạn; lỗi không đổi phòng
H|P0|Vòng đời yêu cầu|Ticket PENDING|Bắt đầu→IN_PROGRESS; hoàn tất→COMPLETED|Chuyển đúng thứ tự; hoàn tất trả phòng về AVAILABLE
U|P0|Chuyển trạng thái sai|PENDING→COMPLETED; COMPLETED→IN_PROGRESS; lặp bước|Thử UI/request|Backend chặn; không mở lại hoặc hoàn tất hai lần|A
U|P0|Kết thúc bảo trì qua API cũ khi còn ticket|Có ticket đang mở|Gọi thao tác kết thúc bảo trì trực tiếp/legacy|Không bỏ qua ticket; phải dùng vòng đời yêu cầu|A
H|P0|Ghi chi phí lần đầu|IN_PROGRESS/COMPLETED; amount1.000.000; revision0|Ghi ngày hợp lệ và ghi chú; lưu|Một tổng chi phí; có người ghi/giờ/history; dashboard phản ánh ngày chi phí
B|P1|Biên tiền và ghi chú chi phí|0 có lý do; âm/lẻ; >999.999.999.999; note trống/>2000|Lưu từng mẫu|0 có giải thích hợp lệ; số nguyên trong biên; dữ liệu sai bị chặn
B|P0|Biên ngày chi phí|Trước ngày tạo; ngày tạo; D; D+1|Lưu từng mẫu|Chỉ ngày tạo≤expenseDate≤D; ngày theo múi giờ khách sạn|T
U|P0|Ghi chi phí khi ticket chưa bắt đầu|Ticket PENDING|Gửi lưu chi phí|Bị từ chối; không hạch toán công việc chưa được xử lý|A
C|P0|Hai manager sửa cùng revision|Cùng revision hiện tại; tổng mới khác nhau|Hai phiên cùng lưu|Một thành công; stale revision409; không lost update; lịch sử bất biến|S
H|P0|Sửa tổng chi phí và cảnh báo thiếu phí|Tổng cũ1m; đổi1.5m; ticket khác completed chưa có cost|Lưu sửa; xem dashboard/history; mở URL legacy|Dashboard tính1.5m, không2.5m; có cảnh báo thiếu chi phí; legacy redirect đúng
""")

group("REF", "Duyệt hoàn tiền thủ công", "MANAGER", "/payment-monitoring/refunds", "Refund PENDING từ hủy/tiền dư/trả sớm; bank sandbox, không chuyển thật.", r"""
H|P1|Danh sách và chi tiết hoàn|Có refund PENDING/SUCCESS|Lọc/tìm/trang; mở bản ghi|Đúng booking, nguồn tiền, số tiền, đích nhận; PENDING chưa được hiểu là đã trả khách
H|P0|Xác nhận sau đối soát ngoài hệ thống|Có chứng từ chuyển tiền sandbox thực sự|Nhập mã tham chiếu/người xử lý theo form; xác nhận|Refund SUCCESS một lần; lưu dấu vết; dashboard trừ đúng ngày hoàn thành
U|P0|Thiếu chứng từ/thông tin bắt buộc|Thiếu mã tham chiếu theo form|Thử xác nhận|Validation rõ; không giả rằng đã có giao dịch ngân hàng
U|P0|Duyệt lại hoặc số tiền bị sửa|Refund đã SUCCESS; request đổi amount|Thử gửi lại/sửa payload|Không hoàn hai lần; không nhận số tiền client trái bản ghi|A
C|P0|Hai manager cùng duyệt|Một refund PENDING|Xác nhận đồng thời|Một kết quả thành công; một dấu vết hoàn tiền, không nhân đôi dòng tiền|S
U|P0|Admin-only và vai trò khác duyệt refund|ADMIN-only, lễ tân, buồng phòng, khách|Mở URL/gửi confirm|Không có quyền; MANAGER là role được cấp hiện tại|A
U|P1|Đã tạo refund nhưng chưa chuyển khoản|Refund PENDING; chưa có chứng từ ngoài|Theo dõi web/đối chiếu dashboard|Giữ pending; không tự mô tả hệ thống đã chuyển tiền hoặc có callback hoàn tự động
""")

group("MON", "Giám sát thanh toán và webhook", "ADMIN / MANAGER; raw payload chỉ ADMIN", "/payment-monitoring/payments; /payment-monitoring/webhook-events", "Có PAYMENT/REFUND, nhiều trạng thái và webhook hợp lệ/không hợp lệ.", r"""
H|P1|Theo dõi giao dịch và số liệu|Nhiều giao dịch, có cash/QR/refund|Mở tabs; lọc/tìm/trang; làm mới|Dòng giao dịch, tổng, trạng thái đúng; cash không mang nhầm mã giao dịch bank
H|P1|Nhật ký webhook|Callback đã xử lý, trùng, không hợp lệ|Tìm event theo mã; xem kết quả xử lý|Có dấu vết phù hợp; không đếm event trùng thành tiền nhận mới
H|P0|Admin xem raw payload|ADMIN; event tồn tại|Mở drawer raw payload|Chỉ payload event đúng; không lộ server API key hoặc secret cấu hình
U|P0|Manager không xem raw|MANAGER-only; biết ID event|Thử drawer và API raw trực tiếp|Không hiện raw; API chặn dù đọc được danh sách/tổng|A
U|P1|ID không tồn tại và filter sai|Event/payment UUID không tồn tại; query sai|Thử điều khiển và request|Không tìm thấy/validation phù hợp; không dùng bản ghi trước thay thế|A
U|P1|Lỗi tải trong monitor|API500/timeout|Mở trang; retry/refresh|Thông báo lỗi; không hiển thị dữ liệu cũ như số liệu vừa tải|N
H|P1|Đối chiếu chuỗi giao dịch|Một booking gồm partial, full, excess refund, cash nếu có|So booking, payment, webhook, refund, dashboard|Mỗi khoản tiền được tính đúng một lần; không có nút reprocess/retry giả nếu UI chưa hỗ trợ
""")

group("DASH", "Dashboard và đối soát dòng tiền", "ADMIN / MANAGER", "/dashboard", "Bộ tiền fixture có mốc UTC và expenseDate rõ; không dùng tổng tiền booking làm tiền đã thu.", r"""
H|P0|Tiền thực thu và hoàn thành công|Nhận2m; refund SUCCESS300k; refund PENDING200k|Chọn khoảng chứa ngày nhận/hoàn|Thực thu2m; hoàn thực300k; thuần1.7m; không trừ pending hoặc cộng booking chưa trả
H|P0|Chi phí và số còn sau bảo trì|Net1.7m; chi phí bảo trì hiện hành500k|Mở dashboard và chi tiết|Còn1.2m; nhãn không gọi là lợi nhuận kế toán đầy đủ
B|P0|Biên khoảng báo cáo|from00:00; to00:00; giao dịch sát hai biên|Chọn khoảng và đối chiếu API|Bao gồm from, loại to theo [from,to) trong giờ khách sạn; UI ngày cuối =to-1|A
B|P1|Khoảng tối đa và sai|366/367 ngày; ngày đảo; thiếu ngày|Lọc từng mẫu|Đúng giới hạn366; từ chối khoảng sai; không trả dữ liệu tùy tiện
H|P0|Hoàn ngày khác ngày nhận|Nhận trong tháng1; refund thành công tháng2|Xem từng tháng|Thu ở ngày nhận; hoàn ở ngày hoàn thực tế; không sửa ngược báo cáo thành cash giả
H|P0|Sửa chi phí không cộng dồn|Chi phí đổi1m→1.5m; có history1m|Xem dashboard và lịch sử|Chỉ tổng hiện hành1.5m theo expenseDate; history không bị cộng thêm
B|P1|Dòng tiền âm và không dữ liệu|Khoảng chỉ có refund; khoảng rỗng|Xem số/charts|Hiện âm hợp lệ; rỗng có0/empty theo thiết kế; không lỗi biểu đồ
H|P1|Thống kê booking theo trạng thái|Nhiều status có bộ đếm chuẩn|So tổng, status và chart|Đúng định nghĩa bộ đếm backend; không trộn tổng booking với số phòng hoặc tiền thực thu
U|P1|Lỗi báo cáo và chi phí chưa ghi|Giả lập500; completed ticket chưa có cost|Mở/retry; xem cảnh báo|Không báo thành công giả; hiện thiếu chi phí, không ngầm xem đó là chi phí0 đã xác nhận|N
""")

group("AI", "Trợ lý AI trên web", "Khách đã đăng nhập; ẩn danh kiểm tra chặn", "Widget AI trên các trang có hiển thị", "Dịch vụ AI/RAG sandbox hoạt động; dữ liệu booking A/B riêng.", r"""
U|P1|Khách ẩn danh dùng AI|Chưa đăng nhập|Mở widget; thử gửi API trực tiếp|Widget mời đăng nhập; API yêu cầu xác thực; không đọc booking riêng|A
H|P1|Hỏi chính sách và nguồn|Đã đăng nhập; policy được nạp|Hỏi giờ nhận/trả, hủy, dọn phòng|Trả lời phù hợp policy hiện hành; có nguồn khi có; không bịa chính sách mới|E
H|P1|Streaming câu trả lời|Câu hỏi hợp lệ|Gửi; quan sát từng phần; đóng/mở widget|Không lặp đoạn, mất chữ hoặc chặn toàn trang; không cho gửi chồng vô hạn|E
B|P1|Tin nhắn rỗng và độ dài|Trống; whitespace; 2000/2001 ký tự|Thử gửi bằng UI và request|Không gửi rỗng; giới hạn UI2000; API xử lý dữ liệu ngoài biên an toàn|A
H|P0|AI gợi ý đặt phòng|Action CONTINUE_BOOKING có hạng/ngày/khách|Nhấn action|Mở booking với đúng tham số; backend recheck tồn/giá; AI không tự đặt hoặc thanh toán
H|P0|Action hủy/dịch vụ/thanh toán|Action REVIEW_CANCELLATION/SELECT_ADDONS/VIEW_PAYMENT|Nhấn từng action trên fixture thuộc A|Đúng màn hình/booking/payment; vẫn cần quyền và xác nhận nghiệp vụ
U|P0|Hỏi booking người khác|A hỏi mã B; action chứa ID B|Gửi câu hỏi và thử action|Không lộ PII/trạng thái/tiền của B; backend chặn truy cập dù AI trả link|E
U|P0|Prompt yêu cầu vượt quyền|Yêu cầu tự hoàn tiền, đổi role, bỏ qua policy hoặc lấy secret|Gửi prompt kiểm thử trong sandbox|Không thực thi tác vụ ngoài quyền; không lộ token/khóa; mọi hành động cần quy trình thật|E
U|P1|AI timeout, lỗi hoặc rate limit|Provider500/SSE đứt/429|Gửi; dùng Thử lại khi có|Lỗi rõ; retry không tạo booking/payment; không hiển thị thành công giả|E
U|P0|Đổi tài khoản và dữ liệu nhạy cảm|A có hội thoại; logout rồi login B cùng browser|Mở widget; thử conversationId cũ|B không đọc được thông tin riêng của A; cần ghi lỗi nếu lịch sử bị dùng chéo|S
""")

group("SEC", "Kiểm soát truy cập và tính toàn vẹn", "Từng vai trò; hai khách A/B", "Các URL bảo vệ và API tương ứng", "Chỉ thử ở môi trường UAT được phép; dùng DevTools với dữ liệu fixture.", r"""
U|P0|IDOR trên tất cả tài nguyên riêng|Booking/payment/refund/conversation của B|A thay ID/mã trong URL, query, body|Không đọc/sửa được B; không chỉ ẩn nút ở frontend|A
U|P0|JWT sửa hoặc hết hạn|Token đổi role/sub/signature; token hết hạn|Gọi API bảo vệ|401/403; không tin role trong localStorage; không vượt quyền|A
U|P0|Gọi API khi nút đã ẩn|Tài khoản thấp quyền; biết endpoint nhân viên|Gửi CRUD/check-in/out/refund/cost trực tiếp|Backend kiểm quyền độc lập UI; không mutation khi bị chặn|A
U|P0|Giả mạo giá, tổng, trạng thái|Thêm totalAmount, paidAmount, status, collector, timestamps vào request khách|Gửi tạo/sửa/thanh toán|Server quyết định tiền/trạng thái/người thao tác; không nhận số liệu đặc quyền của client|A
U|P1|XSS lưu và phản chiếu|Chuỗi <img src=x onerror=alert(1)> trong tên/ghi chú/chat/tìm kiếm|Lưu và xem ở trang khách/nhân viên|Hiển thị text an toàn; không thực thi script hoặc hỏng trang
U|P1|Chuỗi truy vấn bất thường|Dấu nháy, SQL-like, Unicode, chuỗi dài trong tìm/mã|Nhập/gửi thử ở sandbox|Không lỗi500/lộ SQL/stacktrace; validation hoặc kết quả rỗng hợp lệ|A
U|P0|Rò rỉ qua URL, log và bộ nhớ dùng chung|Payment link/token, mail reset, profile|Kiểm URL, console, lỗi network, Back sau logout|Không lộ secret hệ thống; không tái dùng dữ liệu riêng sau đổi tài khoản; token khách được xử lý như thông tin truy cập|A
C|P0|Refresh token dùng lại hoặc hai phiên|Refresh đã rotate; request đồng thời|Thử replay token cũ và refresh đồng thời theo policy|Không cấp phiên trái phép; web không refresh vô hạn; lỗi rõ|A
U|P1|Rate limit theo IP không bị giả header|Môi trường test với trusted proxy được cấu hình|Thử đổi X-Forwarded-For tùy ý từ nguồn không tin cậy|Không dễ vượt limiter chỉ bằng header giả; không làm khóa người dùng khác|A
U|P1|Không để lỗi hạ tầng thành thao tác thành công|DB/Redis/mail/storage/AI test lỗi có kiểm soát|Chạy ca lỗi tích hợp; xem web và audit|Không báo thu/hoàn/nhận/trả thành công khi transaction thất bại; không lộ secret|E
""")

group("UX", "Trải nghiệm, lỗi mạng và tương thích", "Mọi vai trò phù hợp màn hình", "Toàn bộ trang có trong ma trận", "Chrome/Edge/Firefox/Safari nếu có; desktop và mobile; Network DevTools.", r"""
H|P1|Responsive các luồng chính|360/390/768/1440px|Đi từ tìm phòng→booking→QR; mở bảng nhân viên|Đọc/nhấn được ngày, số tiền, CTA; không mất cột hành động; bảng có cách cuộn phù hợp
H|P1|Bàn phím và focus|Không dùng chuột|Tab/Shift+Tab/Enter/Escape qua menu, form, modal|Focus thấy được; label đúng; modal giữ/trả focus; không gửi form ngoài ý muốn
H|P1|Thông báo lỗi có thể sửa|Form sai nhiều trường|Submit; sửa từng trường; gửi lại|Lỗi cạnh trường; không xóa toàn bộ dữ liệu đã nhập; spinner kết thúc
U|P1|Mạng chậm và double-click|Slow3G/latency cao|Tạo/sửa/thu/hoàn/check-in trên fixture|Có loading/disable phù hợp; không nhân đôi thao tác; retry đọc lại trạng thái|N
C|P1|Request tìm kiếm về sai thứ tự|Query A chậm; đổi ngay sang B|Tìm hai khoảng liên tiếp|Kết quả B không bị phản hồi A ghi đè; ngày header, tồn, giá cùng query|N
U|P1|Offline và phục hồi|Mở form có dữ liệu; mất mạng|Submit khi offline; nối lại; thử lại|Lỗi rõ; không giả thành công; phục hồi đúng trạng thái backend|N
U|P1|401/403/404/409/429/500|Giả lập từng status tại API phù hợp|Mở trang/thao tác|Thông báo và hành vi đúng lỗi; không logout với mọi500; không retry vô hạn409/429|N
H|P2|Định dạng Việt Nam và giá trị dài|Tên dài có dấu; VND lớn; ngày qua năm|Xem thẻ, modal, bảng, QR, dashboard|dd/MM/yyyy và VND nhất quán; không cắt mất số tiền hoặc nhầm ngày/tháng
U|P2|Ảnh/API phụ lỗi không phá luồng chính|Ảnh404; add-on500; AI lỗi|Mở/tìm/đặt theo điều kiện trang|Fallback/lỗi cục bộ rõ; không crash toàn trang hoặc gửi thiếu dịch vụ mà không báo|N
H|P1|Refresh/Back/đổi tài khoản|Đang ở bước payment hoặc modal nghiệp vụ|F5; Back/Forward; logout/login vai trò khác|Không replay mutation; quyền và cache được cập nhật; không hiện dữ liệu phiên cũ
""")

ROLES = {
    "ANON": "Ẩn danh", "GUEST": "Khách roles=[]", "RECEPTIONIST": "Lễ tân",
    "HOUSEKEEPER": "Buồng phòng", "MANAGER": "Quản lý", "ADMIN": "Admin",
}
MATRIX = [
    ("/; /room-types; /room-types/:roomTypeId; /rooms/search", tuple(ROLES), "Công khai; hạng/ngày vẫn phải hợp lệ."),
    ("/bookings/new; /payments/:paymentId/result", tuple(ROLES), "Route công khai; dữ liệu booking/payment yêu cầu đúng chủ hoặc booking access token hợp lệ."),
    ("/login; /register; /forgot-password; /reset-password", tuple(ROLES), "Công khai; reset vẫn cần token hợp lệ; phiên đã đăng nhập có thể được điều hướng theo UI."),
    ("/account/profile", tuple(r for r in ROLES if r != "ANON"), "Chỉ hồ sơ của chính tài khoản; nhân viên chỉ phần cá nhân."),
    ("/account/bookings; /account/bookings/:bookingCode", ("GUEST",), "Khách roles=[]; chỉ booking thuộc mình. Lối vào hồ sơ section=bookings cũng phải kiểm quyền."),
    ("/dashboard", ("ADMIN", "MANAGER"), "Dòng tiền và bảo trì, không phải lợi nhuận kế toán đầy đủ."),
    ("/front-desk/check-in", ("RECEPTIONIST",), "Không tự cấp check-in cho ADMIN-only/MANAGER-only."),
    ("/front-desk/arrivals/today", ("ADMIN", "MANAGER", "RECEPTIONIST"), "Quyền đọc danh sách không đồng nghĩa quyền check-in."),
    ("/front-desk/departures/today", ("ADMIN", "MANAGER", "RECEPTIONIST"), "Cả ba role được checkout thường và trả sớm."),
    ("/housekeeping", ("HOUSEKEEPER",), "Chuyển DIRTY→CLEANING→AVAILABLE."),
    ("/maintenance", ("MANAGER",), "Tạo/tiến hành/hoàn tất ticket và chi phí."),
    ("/rooms/maintenance", ("MANAGER",), "URL cũ redirect /maintenance; vẫn giữ cùng guard."),
    ("/management/rooms", ("ADMIN", "MANAGER"), "CRUD phòng phải thỏa trạng thái và ràng buộc."),
    ("/management/room-types", ("ADMIN", "MANAGER"), "CRUD hạng, ảnh, tiện nghi hiện có."),
    ("/add-on-services", ("ADMIN", "MANAGER"), "Quản trị dịch vụ; danh mục active để chọn khi đặt là công khai."),
    ("/price-rules", ("ADMIN", "MANAGER"), "Quy tắc giá và lịch lễ."),
    ("/payment-monitoring/payments", ("ADMIN", "MANAGER"), "Danh sách giao dịch/tổng; không tự gán thanh toán thành công."),
    ("/payment-monitoring/webhook-events", ("ADMIN", "MANAGER"), "Manager đọc danh sách, không raw payload."),
    ("/payment-monitoring/refunds", ("MANAGER",), "ADMIN-only không có quyền duyệt hoàn theo cấu hình hiện tại."),
    ("Raw payload trong /payment-monitoring/webhook-events", ("ADMIN",), "Kiểm cả drawer và API raw; MANAGER bị từ chối."),
]

rbac_rows = []
for route, allowed, note in MATRIX[3:]:
    allowed_text = ", ".join(ROLES[r] for r in allowed)
    denied_text = ", ".join(ROLES[r] for r in ROLES if r not in allowed)
    rbac_rows.append(
        f"H|P0|Quyền cho phép: {route}|Phiên riêng cho {allowed_text}|"
        f"Lần lượt mở {route} từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ|"
        f"Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. {note}|A"
    )
    rbac_rows.append(
        f"U|P0|Quyền từ chối: {route}|Phiên riêng cho {denied_text}|"
        f"Mở {route} bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn|"
        "Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm|A"
    )
group("RBAC", "Ma trận quyền theo trang", "Mỗi tài khoản chỉ một role thử; khách roles=[]", "URL cụ thể trong tên ca", "Tách browser profile cho từng role; không dùng tài khoản đa quyền để kết luận về quyền đơn.", "\n".join(rbac_rows))

FIXTURES = [
    ("ENV", "FE/BE/DB đúng phiên bản; hotel policy; mail/bank/MinIO/AI test", "Người vận hành UAT", "Ghi commit SHA, URL, config hiệu lực, timezone, giờ server; không ghi secret", "Mỗi lần chạy"),
    ("ACCT", "Ẩn danh; khách A/B; R/H/M/A mỗi tài khoản một role", "Người cấp tài khoản UAT", "Khách roles=[]; thêm LOCKED/INACTIVE; không dùng dữ liệu khách thật", "AUTH, RBAC, SEC"),
    ("D", "Ngày và instant khách sạn", "Backend Clock fixture", "Đọc GET /api/v1/stay-policy; default GMT+7,14:00,12:00,06:00,cutoff 23:59:59", "TIME; mọi ca biên giờ"),
    ("INV", "Hạng UAT-A có3 phòng; UAT-B có2 phòng; bố trí booking theo từng đêm", "QA + người cấp dữ liệu", "Tạo riêng; ghi room IDs, roomType IDs, bookings, expiredAt, trạng thái; không thay inventory đang vận hành", "INV, BOOK, CIN, EXT"),
    ("LAST", "Hạng/khoảng còn đúng1 phòng", "QA", "Hai khách cùng khoảng; dùng hai browser profile; mỗi lần chạy cần kỳ/fixture mới", "Tranh phòng cuối"),
    ("G", "Tên/email/điện thoại/giấy tờ giả hợp lệ", "QA", "Mail sandbox do nhóm kiểm soát; CCCD là dữ liệu giả, không sao chép khách thật", "BOOK, PROFILE"),
    ("BKG", "Pending còn hạn/hết hạn/partial; confirmed; checked-in; overdue; checked-out; cancelled", "QA + backend fixture", "Mỗi case có booking riêng; không tái dùng booking đã hủy/checkout làm tiền điều kiện ca khác", "Booking và vận hành"),
    ("MONEY", "Bank callback incoming; account nhận test; payment code; event/providerTransactionId", "Người giữ bank sandbox", "Dùng hotel-booking/http/sepay-webhook.http; khóa chỉ trong môi trường riêng; không dán vào báo cáo", "PAY, MON, REF"),
    ("PRICE", "Giá1m/đêm; rule10%/20%; add-on200k; booking snapshot", "ADMIN/MANAGER UAT", "Đây là giá fixture, không phải giá catalog thật; dùng hạng riêng", "PRICE, ADD, EXT"),
    ("EARLY", "U=2m; nợ300k; phí200k; bank nhận refund test", "QA + R/M/A", "Credit1.6m; refund1.1m; cần booking đang ở và snapshot đúng", "EARLY, REF"),
    ("COST", "Ticket pending/in-progress/completed; chi phí revision0/n", "MANAGER UAT", "Hai manager mở cùng revision; chuẩn bị ticket completed chưa cost", "MAINT, DASH"),
    ("MEDIA", "JPEG/PNG thật;0byte;>5MB;>25MP; ảnh giả;21ảnh", "QA", "File an toàn, không dữ liệu cá nhân; không dùng malware", "RTYPE"),
    ("FAULT", "Offline/Slow3G/500/429/SSE mất kết nối; lỗi dịch vụ ngoài", "DevTools/proxy/backend fixture", "Chỉ môi trường UAT; không dừng DB/Redis hoặc thay đồng hồ hệ điều hành môi trường đang phục vụ", "Unhappy tích hợp"),
]

E2E = [
    ("E2E-01", "P0", "Ẩn danh→ngân hàng→lễ tân→buồng phòng", "Kỳ ở hợp lệ; đủ tiền ban đầu; phòng sẵn sàng; Clock tới ngày nhận/trả",
     "1. Tìm ngày/khách và nhấn tên hạng kiểm đúng chi tiết. 2. Chọn phòng, nhập G, tạo pending. 3. Tạo QR, gửi callback sandbox đủ tiền. 4. Xác minh confirmed. 5. Lễ tân tra mã và check-in. 6. Đến hạn trả, checkout. 7. Buồng phòng bắt đầu/hoàn tất dọn. 8. Tìm và nhận khách mới trên phòng đó.",
     "Một booking/một lần thu; pending→confirmed→checked-in→checked-out; available→occupied→dirty→cleaning→available. Nhánh lỗi: chưa đủ tiền không nhận; chưa dọn không giao.", "NAV-002; BOOK-001; PAY-002; CIN-001; COUT-001; HOUSE-008"),
    ("E2E-02", "P0", "Khách đăng nhập→dịch vụ→snapshot→lịch sử", "A có tài khoản; phòng3m; dịch vụ200k x2",
     "1. Đăng nhập A. 2. Tìm và đặt kèm dịch vụ. 3. Xem tổng3.4m và thanh toán test. 4. Mở lịch sử và chi tiết. 5. Manager đổi giá/ngừng dịch vụ. 6. Mở lại booking cũ; tạo booking mới.",
     "Chỉ A xem booking; snapshot cũ không đổi; đặt mới dùng catalog hiện hành. Nhánh lỗi: B nhập mã A bị chặn.", "AUTH-007; ADD-001; ADD-007; HIST-001; HIST-004"),
    ("E2E-03", "P0", "Hai khách→giữ phòng cuối→hết hạn", "Còn1phòng; hai phiên A/B; Clock tại expiredAt",
     "1. A/B mở cùng kết quả. 2. Cùng tạo pending. 3. Người thắng không trả tiền; thử sửa pending sát hạn. 4. Tới đúng hạn tìm lại trước scheduler. 5. B đặt lại. 6. Gửi tiền cho QR cũ của A.",
     "Chỉ một hold tại một thời điểm; sửa không gia hạn; đúng expiredAt tồn được trả; tiền muộn không hồi sinh booking cũ, đi đối soát hoàn.", "INV-004; INV-013; BOOK-010; PAY-006; PAY-013"),
    ("E2E-04", "P0", "Thanh toán partial→full→callback lặp→dư", "Payment test tổng1m; các transaction IDs riêng",
     "1. Chuyển400k. 2. Xem chưa confirmed. 3. Chuyển600k. 4. Replay callback600k. 5. Dùng fixture riêng chuyển dư200k. 6. Mở monitor và refund.",
     "Cộng tiền một lần mỗi giao dịch; đủ mới confirmed; dư thành refund pending. Nhánh lỗi: API key sai/account sai/outgoing không được xác nhận.", "PAY-003; PAY-004; PAY-005; PAY-008; PAY-009; MON-007"),
    ("E2E-05", "P0", "Hủy booking→duyệt hoàn→dashboard", "Ba confirmed đã thanh toán; còn48h,24h,<24h; bank sandbox",
     "1. Xem quote hủy từng mốc. 2. Cung cấp bank và hủy ca được phép. 3. Xem refund pending. 4. Manager đối soát chuyển sandbox rồi xác nhận chứng từ. 5. Xem cash dashboard trước/sau. 6. Thử replay duyệt và admin-only truy cập.",
     "48h hoàn100%,24h hoàn50%,<24h từ chối; pending chưa trừ cash thực; một lần hoàn. Nhánh partial-pending và nhiều payment cần đối soát riêng.", "CANCEL-002; CANCEL-003; CANCEL-004; CANCEL-005; REF-002; REF-004; DASH-005"),
    ("E2E-06", "P0", "Nhận sớm→đến muộn qua ngày", "Booking confirmed D và D-1; phòng AVAILABLE; Clock kiểm soát",
     "1. D13:59 nhận không lý do rồi có lý do. 2. Fixture khác nhận đúng 14:00. 3. D05:59:59 nhận booking D-1. 4. Fixture khác thử đúng 06:00. 5. So ngày booking và timestamp.",
     "Nhận sớm cần lý do; đúng 14 không cần; đến muộn trước 06 được, đúng 06 bị chặn; không đổi ngày/giá booking gốc.", "TIME-008; TIME-009; TIME-010; TIME-011; CIN-008"),
    ("E2E-07", "P0", "Gia hạn→trả QR hoặc thu cash", "Hai checked-in của A; giá đêm mới400k; còn đủ tồn",
     "1. A gia hạn thêm2đêm. 2. Đối chiếu+800k, giữ phòng/snapshot cũ. 3. Ca1 trả QR khoản thêm rồi checkout. 4. Ca2 để nợ500k, thêm phí150k và thu cash650k lúc checkout. 5. So payment và dashboard.",
     "Status đang ở giữ nguyên; không thu lại tiền cũ; cash và bank không cộng trùng. Nhánh lỗi: ngày không tiến/hết tồn/phòng đã gán người sau đều chặn.", "EXT-002; EXT-004; EXT-005; EXT-006; PAY-016; COUT-002; COUT-010"),
    ("E2E-08", "P0", "Trả sớm→bù nợ→refund hoặc thu thêm", "Ba fixture credit>nợ, =nợ, <nợ; ngày thực tế D",
     "1. Mở trả sớm và đọc quote từng đêm. 2. CaU2m,nợ300k,phí200k nhập bank để refund1.1m. 3. Ca credit800k=nợ800k không thu/hoàn. 4. Ca credit800k,nợ1.2m thu400k. 5. Kiểm phòng DIRTY và trạng thái tiền. 6. Thử ngày hôm qua và submit lặp.",
     "20% phí/80%credit cho đêm chưa dùng; add-on không nằm trong U; chỉ xử lý ngày hôm nay; một checkout và đúng khoản thu/hoàn.", "EARLY-001; EARLY-002; EARLY-003; EARLY-004; EARLY-005; EARLY-009"),
    ("E2E-09", "P0", "Khách quá hạn→tồn tương lai→trả muộn→dọn", "Checked-in chưa trả sau 12:00; có booking kế tiếp",
     "1. Tìm tồn trước khi checkout. 2. Để qua deadline/nửa đêm. 3. Thử checkout thiếu ghi chú. 4. Ghi lý do/phí nếu có, thu đúng dư nợ. 5. Dọn phòng và tìm lại. 6. Chỉ nhận khách tiếp khi AVAILABLE.",
     "Không tự giải phóng tồn vì ngày đặt đã kết thúc; cần ghi chú; không tự cộng đêm; checkout ra DIRTY, chưa giao ngay.", "TIME-013; TIME-014; INV-010; COUT-006; HOUSE-008"),
    ("E2E-10", "P0", "Quản lý bảo trì→chi phí→dashboard", "Phòng AVAILABLE; managerM1/M2; giá trị cost fixture",
     "1. M1 tạo ticket, kiểm MAINTENANCE và tồn giảm. 2. Bắt đầu xử lý. 3. Ghi cost1m. 4. M1/M2 cùng mở revision và sửa khác nhau. 5. Xử lý409; lưu tổng1.5m. 6. Hoàn tất ticket; kiểm AVAILABLE và dashboard/history.",
     "Một ticket mở; đúng thứ tự trạng thái; revision chống ghi đè; dashboard1.5m không2.5m; lịch sử giữ1m. Admin-only bị chặn route bảo trì.", "MAINT-001; MAINT-004; MAINT-007; MAINT-011; MAINT-012; DASH-006"),
    ("E2E-11", "P1", "Admin/manager quản trị catalog→giá khách", "Hạng/phòng/dịch vụ UAT; ảnh hợp lệ và giả; rule riêng",
     "1. Tạo hạng, upload ảnh, chọn tiện nghi có sẵn. 2. Tạo phòng AVAILABLE thuộc hạng. 3. Thêm dịch vụ và rule/weekend/lịch lễ. 4. Khách tìm và cộng giá từng đêm. 5. Tạo booking. 6. Đổi giá và ngừng bán. 7. Kiểm snapshot cũ; thử xóa tài nguyên có FK.",
     "Giá mới phản ánh catalog/rule; snapshot cũ giữ nguyên; ảnh giả/giá âm/hạng trùng bị chặn; không xóa lịch sử booking.", "RTYPE-001; RTYPE-007; ROOM-001; ADD-005; PRICE-004; HOL-004; RTYPE-011"),
    ("E2E-12", "P0", "Tài khoản→reset→phân quyền→logout", "A/B; mail sandbox; R/H/M/Admin mỗi role riêng",
     "1. Đăng ký A và login. 2. Sửa hồ sơ. 3. Reset mật khẩu qua mail; dùng lại token cũ. 4. Chạy mọi dòng ma trận quyền bằng menu/URL/API. 5. Thử A đọc booking B. 6. Logout và Back/F5; login B cùng browser.",
     "Token reset dùng một lần; quyền backend độc lập UI; không rò cache/PII giữa tài khoản. Không nhầm admin-only được check-in/maintenance/refund.", "AUTH-001; RESET-001; RESET-005; PROFILE-002; SEC-001; RBAC-*; UX-010"),
    ("E2E-13", "P1", "AI→action thật→lỗi dịch vụ", "A đăng nhập; RAG/provider test; booking A/B",
     "1. Hỏi policy giờ và hủy; xem nguồn. 2. Nhấn gợi ý đặt với ngày/hạng. 3. Đi tiếp luồng booking có backend recheck. 4. Thử action hủy/payment của A và ID B. 5. Giả lập SSE/provider lỗi rồi retry.",
     "AI không tự thu/hoàn/đặt; action đúng route và vẫn kiểm quyền; lỗi không phát sinh giao dịch; không đọc dữ liệu B.", "AI-002; AI-005; AI-006; AI-007; AI-009"),
    ("E2E-14", "P0", "Hai phiên và lỗi phản hồi sau mutation", "Fixture cuối phòng/cùng phòng nhận/cùng checkout/cùng refund",
     "1. Lần lượt chạy tranh hold, nhận cùng phòng, checkout cùng booking, duyệt cùng refund. 2. Mỗi luồng giả lập web mất phản hồi sau commit. 3. Refresh/tra lại trước retry. 4. So phòng, booking, payment, refund và dashboard.",
     "Mỗi tài nguyên chỉ một kết quả hợp lệ; không double charge/refund/assign; web khôi phục theo backend. Mọi lỗi500 phải có trạng thái cuối đối soát được.", "INV-013; CIN-009; CIN-010; COUT-009; REF-005; CIN-012; SEC-010"),
]


def md_cell(value):
    return str(value).replace("|", "\\|").replace("\n", "<br>")


def md_table(headers, rows):
    return "\n".join([
        "| " + " | ".join(map(md_cell, headers)) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
        *("| " + " | ".join(map(md_cell, row)) + " |" for row in rows),
    ])


def write_markdown():
    parts = [
        "# Checklist kiểm thử web HANBAY",
        "Chỉ kiểm phần vừa sửa: [16 luồng FE](stay-time-fe-checklist.md). Rule và biên chính xác: [thời gian/tồn phòng](../23-stay-time-and-inventory.md).",
        "Nguồn: `build-web-test-plan.py`. Phạm vi đối chiếu mã nguồn nhánh main ngày 03/11/2026; xem [hướng dẫn](web-uat-guide.md) trước khi chạy.",
        f"**{len(CASES)} ca / {len(GROUPS)} nhóm; toàn bộ trạng thái ban đầu: Chưa chạy.** Đây là kịch bản kỳ vọng, không phải biên bản xác nhận ứng dụng đã vượt qua các ca.",
        "P0: dữ liệu/quyền/tiền/tồn phòng hoặc luồng cốt lõi; P1: chức năng chính; P2: trình bày/ít ảnh hưởng. Happy / Unhappy / Biên / Đồng thời là loại tình huống, không phải trạng thái kết quả.",
        "Các bước ngăn bằng dấu chấm phẩy được chạy theo thứ tự; các bộ dữ liệu ngăn bằng dấu chấm phẩy hoặc dấu / phải thử đủ từng mẫu bằng fixture độc lập. Khi một mẫu lỗi, ghi rõ mẫu đó trong kết quả thực tế.",
        md_table(["Mã nhóm", "Nhóm", "Số ca", "Vai trò", "Trang"], [
            (code, name, sum(c["ID"].startswith(code + "-") for c in CASES), role, route)
            for code, name, role, route in GROUPS
        ]),
    ]
    for code, name, role, route in GROUPS:
        subset = [c for c in CASES if c["ID"].startswith(code + "-")]
        parts.extend([f"\n## {code} — {name}", f"**Vai trò:** {role}. **Trang:** `{route}`.", f"**Chuẩn bị chung:** {subset[0]['Chuẩn bị chung']}"])
        for case in subset:
            parts.extend([
                f"<a id=\"{case['ID'].lower()}\"></a>",
                f"\n### {case['ID']} — {case['Tên ca']}",
                f"{case['Loại']} · {case['Ưu tiên']} · {case['Cách chạy']} · **Chưa chạy**",
                f"- **Dữ liệu/điều kiện:** {case['Dữ liệu / điều kiện riêng']}",
                f"- **Thao tác:** {case['Thao tác']}",
                f"- **Mong đợi:** {case['Kết quả mong đợi']}",
            ])
    (OUT / "web-test-cases.md").write_text("\n\n".join(parts) + "\n", encoding="utf-8")
    flow_parts = ["# Quy trình kiểm thử xuyên suốt trên web", "14 quy trình; mỗi quy trình cần fixture độc lập. Trạng thái: **Chưa chạy**. Đọc [hướng dẫn](web-uat-guide.md) và [checklist chi tiết](web-test-cases.md) để chạy đủ các biến thể."]
    for flow_id, priority, name, setup, steps, expected, references in E2E:
        flow_parts.extend([f"## {flow_id} — {name}", f"**Ưu tiên:** {priority}. **Chuẩn bị:** {setup}", steps, f"**Mong đợi và nhánh lỗi:** {expected}", "**Ca tham chiếu:** " + "; ".join(f"[{ref}](web-test-cases.md#{ref.lower()})" if ref != "RBAC-*" else "RBAC-* (chạy toàn bộ nhóm quyền)" for ref in references.split("; "))])
    (OUT / "web-e2e-flows.md").write_text("\n\n".join(flow_parts) + "\n", encoding="utf-8")


def col_name(number):
    result = ""
    while number:
        number, remainder = divmod(number - 1, 26)
        result = chr(65 + remainder) + result
    return result


NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
ET.register_namespace("", NS)
ET.register_namespace("r", REL)


def xml_bytes(element):
    return ET.tostring(element, encoding="utf-8", xml_declaration=True)


def sheet_xml(rows, widths, status_column=None):
    root = ET.Element(f"{{{NS}}}worksheet")
    view = ET.SubElement(ET.SubElement(root, f"{{{NS}}}sheetViews"), f"{{{NS}}}sheetView", workbookViewId="0")
    ET.SubElement(view, f"{{{NS}}}pane", ySplit="1", topLeftCell="A2", activePane="bottomLeft", state="frozen")
    columns = ET.SubElement(root, f"{{{NS}}}cols")
    for index, width in enumerate(widths, 1):
        ET.SubElement(columns, f"{{{NS}}}col", min=str(index), max=str(index), width=str(width), customWidth="1")
    data = ET.SubElement(root, f"{{{NS}}}sheetData")
    for row_index, values in enumerate(rows, 1):
        row = ET.SubElement(data, f"{{{NS}}}row", r=str(row_index), ht="32" if row_index == 1 else "95", customHeight="1")
        for column, value in enumerate(values, 1):
            style = "1" if row_index == 1 else ("2" if row_index % 2 == 0 else "0")
            cell = ET.SubElement(row, f"{{{NS}}}c", r=f"{col_name(column)}{row_index}", s=style, t="inlineStr")
            text_node = ET.SubElement(ET.SubElement(cell, f"{{{NS}}}is"), f"{{{NS}}}t")
            text_node.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
            text_node.text = str(value)
    ET.SubElement(root, f"{{{NS}}}autoFilter", ref=f"A1:{col_name(len(rows[0]))}{len(rows)}")
    if status_column:
        validations = ET.SubElement(root, f"{{{NS}}}dataValidations", count="1")
        validation = ET.SubElement(validations, f"{{{NS}}}dataValidation", type="list", allowBlank="0", showErrorMessage="1", errorTitle="Trạng thái không hợp lệ", error="Chọn một trạng thái trong danh sách.", sqref=f"{col_name(status_column)}2:{col_name(status_column)}{len(rows)}")
        ET.SubElement(validation, f"{{{NS}}}formula1").text = '"Chưa chạy,Đạt,Lỗi,Bị chặn,Không áp dụng"'
    return xml_bytes(root)


def write_xlsx(tables):
    content_ns = "http://schemas.openxmlformats.org/package/2006/content-types"
    types = ET.Element("Types", xmlns=content_ns)
    ET.SubElement(types, "Default", Extension="rels", ContentType="application/vnd.openxmlformats-package.relationships+xml")
    ET.SubElement(types, "Default", Extension="xml", ContentType="application/xml")
    ET.SubElement(types, "Override", PartName="/xl/workbook.xml", ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml")
    ET.SubElement(types, "Override", PartName="/xl/styles.xml", ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml")
    workbook = ET.Element(f"{{{NS}}}workbook")
    sheets = ET.SubElement(workbook, f"{{{NS}}}sheets")
    links = ET.Element("Relationships", xmlns=PKG_REL)
    outer_links = ET.Element("Relationships", xmlns=PKG_REL)
    ET.SubElement(outer_links, "Relationship", Id="rId1", Type=REL + "/officeDocument", Target="xl/workbook.xml")
    with ZipFile(OUT / "web-test-cases.xlsx", "w", ZIP_DEFLATED) as archive:
        for index, (name, rows, widths, status_column) in enumerate(tables, 1):
            ET.SubElement(sheets, f"{{{NS}}}sheet", name=name, sheetId=str(index), attrib={f"{{{REL}}}id": f"rId{index}"})
            ET.SubElement(links, "Relationship", Id=f"rId{index}", Type=REL + "/worksheet", Target=f"worksheets/sheet{index}.xml")
            ET.SubElement(types, "Override", PartName=f"/xl/worksheets/sheet{index}.xml", ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml")
            archive.writestr(f"xl/worksheets/sheet{index}.xml", sheet_xml(rows, widths, status_column))
        ET.SubElement(links, "Relationship", Id=f"rId{len(tables)+1}", Type=REL + "/styles", Target="styles.xml")
        archive.writestr("[Content_Types].xml", xml_bytes(types))
        archive.writestr("_rels/.rels", xml_bytes(outer_links))
        archive.writestr("xl/workbook.xml", xml_bytes(workbook))
        archive.writestr("xl/_rels/workbook.xml.rels", xml_bytes(links))
        archive.writestr("xl/styles.xml", f'''<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="{NS}">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>
<fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF17243D"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF0F5FB"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>''')


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="Regenerate generated artifacts; clears recorded results in those files.")
    args = parser.parse_args()
    output_names = ("web-test-cases.csv", "web-test-cases.md", "web-test-cases.xlsx", "web-coverage.md", "web-e2e-flows.md")
    existing = [name for name in output_names if (OUT / name).exists()]
    if existing and not args.force:
        parser.error("Refusing to overwrite potential test results: " + ", ".join(existing) + ". Copy completed files elsewhere before using --force.")
    ids = [c["ID"] for c in CASES]
    assert len(ids) == len(set(ids)) and len(ids) >= 300
    for e2e in E2E:
        for reference in e2e[-1].split("; "):
            assert reference in ids or reference == "RBAC-*", (e2e[0], reference)
    headers = list(CASES[0])
    with (OUT / "web-test-cases.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers)
        writer.writeheader()
        writer.writerows(CASES)
    write_markdown()
    coverage = []
    for code, name, _, _ in GROUPS:
        subset = [c for c in CASES if c["ID"].startswith(code + "-")]
        coverage.append((code, name, len(subset), *[sum(c["Loại"] == kind for c in subset) for kind in KINDS.values()], *[sum(c["Ưu tiên"] == p for c in subset) for p in ("P0", "P1", "P2")]))
    coverage_headers = ["Mã nhóm", "Nhóm", "Tổng", "Happy", "Unhappy", "Biên", "Đồng thời", "P0", "P1", "P2"]
    (OUT / "web-coverage.md").write_text("# Phạm vi checklist web\n\n" + md_table(coverage_headers, coverage) + f"\n\nTổng {len(CASES)} ca. Chưa chạy toàn bộ ca trong checklist này; số đếm là phạm vi thiết kế, không phải số ca đạt.\n", encoding="utf-8")
    matrix_rows = [["Trang / chức năng", *ROLES.values(), "Ghi chú"]]
    for route, allowed, note in MATRIX:
        matrix_rows.append([route, *("Cho phép" if role in allowed else "Không" for role in ROLES), note])
    tables = [
        ("HuongDan", [["Mục", "Nội dung"], ["Phạm vi", f"{len(CASES)} ca, {len(GROUPS)} nhóm, {len(E2E)} quy trình E2E; đối chiếu main ngày 03/11/2026."], ["Trạng thái", "Tất cả Chưa chạy. Đây là checklist, không phải kết quả thực thi."], ["Bắt đầu", "Chỉ phần sửa: đọc stay-time-fe-checklist.md (16 luồng). Toàn web: web-uat-guide.md; cấp fixture riêng rồi lọc Checklist theo nhóm/P0/vai trò."], ["Quyền", "Xem MaTranQuyen; tài khoản đơn role; route công khai không đồng nghĩa được xem mọi booking/payment."], ["Kết quả", "Chọn Trạng thái trong Checklist; ghi mẫu dữ liệu, thực tế, bằng chứng và mã lỗi; lưu bản chạy riêng theo ngày/build."], ["Biên thời gian", "14:00 bắt đầu nhận,19:55 vẫn đặt hôm nay trước cutoff 23:59:59; expiredAt hạn trả riêng. Biên cần Clock backend test; thiếu fixture ghi Bị chặn."], ["Tích hợp", "Bank/mail/AI/storage dùng sandbox hoặc giả lập; không chuyển tiền/gửi dữ liệu thật để chạy checklist."], ["Nguồn", "Kịch bản nằm trong build-web-test-plan.py. Chạy lại --force sẽ ghi đè kết quả trong file đầu ra; sao lưu bản đã chạy trước."], ["Dạng file", "XLSX dùng text, bộ lọc, cố định hàng đầu, dropdown trạng thái; CSV UTF-8 BOM cho Excel; Markdown để đọc đầy đủ."], ["Giới hạn", "Các kỳ vọng là điều cần kiểm, chưa bảo đảm mọi ca đã đạt; ghi lỗi/điểm cần chốt policy, xem guide."], ["Ký hiệu", "Happy/Unhappy/Biên/Đồng thời là loại ca; P0 cao nhất. Một ca có nhiều mẫu phải chạy đủ từng mẫu."]], [24, 120], None),
        ("Checklist", [headers, *[[c[h] for h in headers] for c in CASES]], [15, 55, 32, 15, 12, 40, 55, 60, 60, 65, 75, 35, 20, 65, 40, 20], headers.index("Trạng thái") + 1),
        ("MaTranQuyen", matrix_rows, [65, 18, 20, 18, 18, 18, 18, 90], None),
        ("DuLieu", [["Mã", "Fixture", "Người chuẩn bị", "Yêu cầu", "Dùng cho"], *FIXTURES], [18, 65, 35, 100, 40], None),
        ("QuyTrinhE2E", [["ID", "Ưu tiên", "Quy trình / vai trò", "Chuẩn bị", "Các bước", "Mong đợi / nhánh lỗi", "Case tham chiếu"], *E2E], [18, 12, 60, 70, 120, 110, 80], None),
        ("PhamVi", [coverage_headers, *coverage], [16, 45, 12, 12, 12, 12, 12, 12, 12, 12], None),
    ]
    write_xlsx(tables)
    print(f"Generated {len(CASES)} cases / {len(GROUPS)} groups / {len(E2E)} E2E flows.")
    print("Types:", dict(Counter(c["Loại"] for c in CASES)))
    print("Priority:", dict(Counter(c["Ưu tiên"] for c in CASES)))
    print("All execution statuses: Chưa chạy.")


if __name__ == "__main__":
    main()
