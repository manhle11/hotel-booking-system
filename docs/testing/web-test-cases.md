# Checklist kiểm thử web HANBAY

Chỉ kiểm phần vừa sửa: [16 luồng FE](stay-time-fe-checklist.md). Rule và biên chính xác: [thời gian/tồn phòng](../23-stay-time-and-inventory.md).

Nguồn: `build-web-test-plan.py`. Phạm vi đối chiếu mã nguồn nhánh main ngày 03/11/2026; xem [hướng dẫn](web-uat-guide.md) trước khi chạy.

**334 ca / 30 nhóm; toàn bộ trạng thái ban đầu: Chưa chạy.** Đây là kịch bản kỳ vọng, không phải biên bản xác nhận ứng dụng đã vượt qua các ca.

P0: dữ liệu/quyền/tiền/tồn phòng hoặc luồng cốt lõi; P1: chức năng chính; P2: trình bày/ít ảnh hưởng. Happy / Unhappy / Biên / Đồng thời là loại tình huống, không phải trạng thái kết quả.

Các bước ngăn bằng dấu chấm phẩy được chạy theo thứ tự; các bộ dữ liệu ngăn bằng dấu chấm phẩy hoặc dấu / phải thử đủ từng mẫu bằng fixture độc lập. Khi một mẫu lỗi, ghi rõ mẫu đó trong kết quả thực tế.

| Mã nhóm | Nhóm | Số ca | Vai trò | Trang |
| --- | --- | --- | --- | --- |
| NAV | Điều hướng công khai | 7 | Mọi người | /; /room-types; /rooms/search |
| AUTH | Đăng ký và đăng nhập | 16 | Khách; từng vai trò nhân viên | /register; /login |
| RESET | Quên và đặt lại mật khẩu | 8 | Khách | /forgot-password; /reset-password |
| PROFILE | Hồ sơ cá nhân | 6 | Khách và nhân viên đã đăng nhập | /account/profile |
| CAT | Danh mục và chi tiết hạng phòng | 8 | Khách ẩn danh hoặc đăng nhập | /room-types; /room-types/:roomTypeId |
| TIME | Ngày lưu trú và mốc thời gian | 16 | Khách; lễ tân | /rooms/search; /bookings/new; trang nhận/trả phòng |
| INV | Tồn phòng theo từng đêm | 14 | Khách; người đối soát | /rooms/search |
| BOOK | Tạo và sửa booking đang giữ | 15 | Khách ẩn danh; khách A | /bookings/new |
| ADD | Dịch vụ bổ sung | 9 | Khách; ADMIN/MANAGER khi quản trị | /bookings/new; /add-on-services |
| PAY | Thanh toán QR và callback ngân hàng | 19 | Chủ booking hoặc token ẩn danh đúng | /bookings/new; /payments/:paymentId/result |
| HIST | Lịch sử đặt phòng của khách | 7 | Khách A roles=[] | /account/bookings; /account/bookings/:bookingCode; /account/profile?section=bookings |
| CANCEL | Hủy booking và tính hoàn | 12 | Khách sở hữu booking | /account/bookings; /account/profile?section=bookings |
| EXT | Gia hạn lưu trú | 10 | Khách là bookedBy của booking | /account/bookings; /account/profile?section=bookings |
| ARR | Danh sách khách đến | 6 | RECEPTIONIST; ADMIN/MANAGER chỉ quyền được cấp | /front-desk/arrivals/today |
| CIN | Nhận phòng | 12 | RECEPTIONIST | /front-desk/check-in |
| COUT | Trả phòng thông thường và thu tiền | 12 | RECEPTIONIST / MANAGER / ADMIN | /front-desk/departures/today |
| EARLY | Trả phòng sớm | 10 | RECEPTIONIST / MANAGER / ADMIN | /front-desk/departures/today |
| HOUSE | Buồng phòng và vòng đời phòng | 8 | HOUSEKEEPER | /housekeeping |
| ROOM | Quản lý phòng vật lý | 10 | ADMIN / MANAGER | /management/rooms |
| RTYPE | Quản lý hạng phòng và ảnh | 12 | ADMIN / MANAGER | /management/room-types |
| PRICE | Quy tắc giá và cách tính tiền đêm | 12 | ADMIN / MANAGER; khách đối chiếu giá | /price-rules; /rooms/search |
| HOL | Lịch ngày lễ | 6 | ADMIN / MANAGER | /price-rules |
| MAINT | Bảo trì và chi phí | 12 | MANAGER | /maintenance; /rooms/maintenance |
| REF | Duyệt hoàn tiền thủ công | 7 | MANAGER | /payment-monitoring/refunds |
| MON | Giám sát thanh toán và webhook | 7 | ADMIN / MANAGER; raw payload chỉ ADMIN | /payment-monitoring/payments; /payment-monitoring/webhook-events |
| DASH | Dashboard và đối soát dòng tiền | 9 | ADMIN / MANAGER | /dashboard |
| AI | Trợ lý AI trên web | 10 | Khách đã đăng nhập; ẩn danh kiểm tra chặn | Widget AI trên các trang có hiển thị |
| SEC | Kiểm soát truy cập và tính toàn vẹn | 10 | Từng vai trò; hai khách A/B | Các URL bảo vệ và API tương ứng |
| UX | Trải nghiệm, lỗi mạng và tương thích | 10 | Mọi vai trò phù hợp màn hình | Toàn bộ trang có trong ma trận |
| RBAC | Ma trận quyền theo trang | 34 | Mỗi tài khoản chỉ một role thử; khách roles=[] | URL cụ thể trong tên ca |


## NAV — Điều hướng công khai

**Vai trò:** Mọi người. **Trang:** `/; /room-types; /rooms/search`.

**Chuẩn bị chung:** Chưa đăng nhập; FE/BE đang chạy.

<a id="nav-001"></a>


### NAV-001 — Trang chủ và menu

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có hạng phòng ACTIVE

- **Thao tác:** Mở /; dùng logo và từng menu khách

- **Mong đợi:** Trang đúng URL; không mất nội dung hoặc báo lỗi console

<a id="nav-002"></a>


### NAV-002 — Tên hạng phòng mở đúng chi tiết

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Kết quả có ít nhất hai hạng

- **Thao tác:** Nhấn lần lượt tên từng hạng; dùng Back quay lại

- **Mong đợi:** Đi đến /room-types/{đúng ID}; tên, mô tả, tiện nghi thuộc đúng hạng

<a id="nav-003"></a>


### NAV-003 — Mở liên kết tên bằng tab mới

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có kết quả tìm kiếm

- **Thao tác:** Ctrl/Cmd+click tên; thử Tab rồi Enter

- **Mong đợi:** Liên kết hoạt động như link; không tự tạo booking

<a id="nav-004"></a>


### NAV-004 — Nút chọn phòng

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đã chọn ngày và số khách

- **Thao tác:** Nhấn Chọn phòng trên một thẻ

- **Mong đợi:** /bookings/new nhận đúng hạng, ngày, người lớn, trẻ em

<a id="nav-005"></a>


### NAV-005 — URL không tồn tại

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đường dẫn /khong-ton-tai

- **Thao tác:** Nhập URL trực tiếp và tải lại

- **Mong đợi:** Hiện trang 404; có thể quay về trang hợp lệ

<a id="nav-006"></a>


### NAV-006 — Deep link và tải lại

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một URL chi tiết hợp lệ

- **Thao tác:** Dán URL vào tab mới; F5

- **Mong đợi:** Trang tải đúng; không lỗi định tuyến phía hosting

<a id="nav-007"></a>


### NAV-007 — ID chi tiết sai

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** UUID sai định dạng và UUID không tồn tại

- **Thao tác:** Mở /room-types/{id} cho từng mẫu

- **Mong đợi:** Thông báo không tìm thấy/lỗi hợp lệ; không hiển thị hạng khác


## AUTH — Đăng ký và đăng nhập

**Vai trò:** Khách; từng vai trò nhân viên. **Trang:** `/register; /login`.

**Chuẩn bị chung:** Tài khoản thử riêng; không dùng thông tin cá nhân thật.

<a id="auth-001"></a>


### AUTH-001 — Đăng ký khách hợp lệ

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Email/điện thoại mới; mật khẩu 8–72 ký tự

- **Thao tác:** Điền đủ; xác nhận mật khẩu; gửi một lần; đăng nhập

- **Mong đợi:** Tạo một tài khoản khách roles=[]; đăng nhập được; không có quyền nhân viên

<a id="auth-002"></a>


### AUTH-002 — Thiếu trường đăng ký

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Lần lượt bỏ tên, email, điện thoại, mật khẩu

- **Thao tác:** Gửi từng mẫu; sửa trường lỗi

- **Mong đợi:** Chỉ rõ trường thiếu; không tạo tài khoản một phần

<a id="auth-003"></a>


### AUTH-003 — Biên tên đăng ký

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên 1, 2, 150, 151 ký tự; chỉ khoảng trắng

- **Thao tác:** Thử từng mẫu với dữ liệu còn lại hợp lệ

- **Mong đợi:** Chấp nhận 2–150 sau chuẩn hóa; các mẫu sai bị chặn

<a id="auth-004"></a>


### AUTH-004 — Email không hợp lệ hoặc trùng

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Email sai; email của khách A; khác hoa/thường

- **Thao tác:** Đăng ký từng mẫu

- **Mong đợi:** Sai định dạng bị từ chối; không tạo trùng tài khoản theo email chuẩn hóa

<a id="auth-005"></a>


### AUTH-005 — Số điện thoại đăng ký

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** 0xxxxxxxxx hợp lệ; +84 tương ứng; ký tự lạ; trùng

- **Thao tác:** Gửi từng mẫu và đăng nhập lại

- **Mong đợi:** Số VN hợp lệ được chuẩn hóa; mẫu sai/trùng có lỗi; không sinh hai tài khoản cùng số

<a id="auth-006"></a>


### AUTH-006 — Biên mật khẩu

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** 7, 8, 72, 73 ký tự; xác nhận khác

- **Thao tác:** Đăng ký từng mẫu

- **Mong đợi:** 8–72 và xác nhận khớp được nhận; không tự cắt mật khẩu; không giả định bắt buộc hoa/thường/số

<a id="auth-007"></a>


### AUTH-007 — Đăng nhập bằng email và điện thoại

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách A ACTIVE

- **Thao tác:** Đăng nhập bằng email; logout; đăng nhập bằng điện thoại

- **Mong đợi:** Đúng tài khoản, header và trang đến; mật khẩu không lộ

<a id="auth-008"></a>


### AUTH-008 — Sai thông tin đăng nhập

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Sai mật khẩu; định danh không tồn tại

- **Thao tác:** Thử từng mẫu dưới ngưỡng rate limit

- **Mong đợi:** Không cấp phiên; thông báo an toàn, không phân biệt tài khoản tồn tại

<a id="auth-009"></a>


### AUTH-009 — Tài khoản bị khóa hoặc vô hiệu

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Fixture INACTIVE và LOCKED

- **Thao tác:** Đăng nhập với mật khẩu đúng

- **Mong đợi:** Bị từ chối; không vào trang bảo vệ

<a id="auth-010"></a>


### AUTH-010 — Ghi nhớ đăng nhập

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách A; hai cấu hình checkbox

- **Thao tác:** Đăng nhập; đóng/mở phiên trình duyệt theo từng cấu hình

- **Mong đợi:** Thời gian lưu phiên phù hợp lựa chọn; không nhầm tài khoản giữa phiên

<a id="auth-011"></a>


### AUTH-011 — Đăng xuất

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đã đăng nhập; mở trang bảo vệ

- **Thao tác:** Đăng xuất; Back; tải lại trang bảo vệ

- **Mong đợi:** Phiên phía web bị xóa; dữ liệu riêng không còn truy cập được

<a id="auth-012"></a>


### AUTH-012 — Hết hạn access và refresh token

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Fixture access hết hạn; sau đó refresh hết hạn

- **Thao tác:** Mở trang bảo vệ; tiếp tục sau khi refresh hết hạn

- **Mong đợi:** Access được refresh hợp lệ; refresh không hợp lệ buộc đăng nhập; không lặp vô hạn

<a id="auth-013"></a>


### AUTH-013 — Nhiều request cùng gặp 401

Đồng thời · P0 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Access hết hạn; refresh còn hiệu lực

- **Thao tác:** Mở màn hình tải nhiều API cùng lúc

- **Mong đợi:** Refresh được điều phối; không logout nhầm hoặc tạo vòng lặp request

<a id="auth-014"></a>


### AUTH-014 — Chặn dò mật khẩu

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Cấu hình thử: 5 lần sai/cặp tài khoản-IP trong 900s

- **Thao tác:** Sai tới ngưỡng; thử tiếp và thử Enter

- **Mong đợi:** 429 và thời gian chờ; nút/Enter không gửi liên tục; không khóa toàn tài khoản từ mọi IP

<a id="auth-015"></a>


### AUTH-015 — Hết thời gian khóa tạm

Biên · P1 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có Retry-After; đồng hồ thử kiểm soát

- **Thao tác:** Đợi hết hạn; focus lại; đăng nhập đúng

- **Mong đợi:** Đếm ngược đúng; cho thử lại đúng hạn; đăng nhập đúng reset bộ đếm liên quan

<a id="auth-016"></a>


### AUTH-016 — Đường dẫn sau đăng nhập không an toàn

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** from=//evil.example; URL ngoài; trang không có quyền

- **Thao tác:** Mở login với từng đường dẫn và đăng nhập

- **Mong đợi:** Chỉ chuyển đến đường dẫn nội bộ có quyền; không open redirect


## RESET — Quên và đặt lại mật khẩu

**Vai trò:** Khách. **Trang:** `/forgot-password; /reset-password`.

**Chuẩn bị chung:** Mail test/sandbox; tài khoản A; token mới riêng cho mỗi ca.

<a id="reset-001"></a>


### RESET-001 — Khôi phục mật khẩu thành công

Happy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Email A; token còn hạn

- **Thao tác:** Yêu cầu email; mở link; nhập mật khẩu mới khớp; đăng nhập lại

- **Mong đợi:** Mật khẩu mới dùng được; mật khẩu cũ không dùng được; token bị tiêu thụ

<a id="reset-002"></a>


### RESET-002 — Email không tồn tại

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Email mới chưa đăng ký

- **Thao tác:** Gửi yêu cầu giống tài khoản có thật

- **Mong đợi:** Phản hồi không tiết lộ tồn tại tài khoản; không lộ token

<a id="reset-003"></a>


### RESET-003 — Email trống hoặc sai

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Trống; abc; email quá dài

- **Thao tác:** Gửi từng mẫu

- **Mong đợi:** Thông báo validation; không gửi yêu cầu hợp lệ

<a id="reset-004"></a>


### RESET-004 — Token thiếu, sai hoặc hết hạn

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Không token; token sửa; token hết hạn

- **Thao tác:** Mở reset; thử đổi mật khẩu

- **Mong đợi:** Không đổi được mật khẩu; có hướng dẫn xin link mới

<a id="reset-005"></a>


### RESET-005 — Token đã sử dụng

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đã reset thành công

- **Thao tác:** Mở lại link cũ; gửi lại

- **Mong đợi:** Bị từ chối; không đổi mật khẩu lần hai

<a id="reset-006"></a>


### RESET-006 — Hai tab dùng cùng token

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một token hợp lệ

- **Thao tác:** Gửi hai mật khẩu mới từ hai tab cùng lúc

- **Mong đợi:** Chỉ một lần tiêu thụ thành công; không nhận cả hai

<a id="reset-007"></a>


### RESET-007 — Validation mật khẩu mới

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ngắn/quá dài/không khớp/chỉ khoảng trắng

- **Thao tác:** Gửi từng mẫu rồi sửa hợp lệ

- **Mong đợi:** Không đổi khi sai; mẫu hợp lệ thành công theo policy thực tế

<a id="reset-008"></a>


### RESET-008 — Giới hạn yêu cầu và lỗi mail

Unhappy · P1 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Gửi vượt cấu hình; giả lập mail timeout

- **Thao tác:** Gửi liên tiếp; quan sát; thử lại sau hạn

- **Mong đợi:** Có 429/lỗi phù hợp; không báo đã đổi mật khẩu; không lộ cấu hình mail


## PROFILE — Hồ sơ cá nhân

**Vai trò:** Khách và nhân viên đã đăng nhập. **Trang:** `/account/profile`.

**Chuẩn bị chung:** Khách A/B có số điện thoại khác nhau.

<a id="profile-001"></a>


### PROFILE-001 — Đọc hồ sơ đúng người

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Lần lượt khách A và nhân viên

- **Thao tác:** Mở hồ sơ

- **Mong đợi:** Đúng tên, email, số điện thoại; nhân viên không có lịch sử đặt phòng cá nhân như khách

<a id="profile-002"></a>


### PROFILE-002 — Cập nhật hồ sơ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên/số mới hợp lệ

- **Thao tác:** Sửa; lưu; F5

- **Mong đợi:** Dữ liệu tồn tại; header/sidebar đồng bộ

<a id="profile-003"></a>


### PROFILE-003 — Biên dữ liệu hồ sơ

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên 1/2/100/101; số sai/8–15 chữ số

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Tên 2–100 hợp lệ; số theo schema hồ sơ; lỗi không ghi đè dữ liệu cũ

<a id="profile-004"></a>


### PROFILE-004 — Số điện thoại đã thuộc người khác

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Điện thoại khách B

- **Thao tác:** Sửa hồ sơ A bằng số B

- **Mong đợi:** Bị từ chối; cả hai hồ sơ giữ đúng dữ liệu

<a id="profile-005"></a>


### PROFILE-005 — Không có thay đổi

Happy · P2 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Mở form không sửa

- **Thao tác:** Nhấn lưu

- **Mong đợi:** Thông báo phù hợp; không làm mất dữ liệu

<a id="profile-006"></a>


### PROFILE-006 — Can thiệp email và quyền

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Thêm email/roles/status vào request bằng DevTools

- **Thao tác:** Gửi cập nhật vượt quyền; mở lại hồ sơ

- **Mong đợi:** Không đổi quyền/trạng thái hoặc email qua form chỉ sửa tên/số


## CAT — Danh mục và chi tiết hạng phòng

**Vai trò:** Khách ẩn danh hoặc đăng nhập. **Trang:** `/room-types; /room-types/:roomTypeId`.

**Chuẩn bị chung:** Có hạng ACTIVE/INACTIVE; ảnh và tiện nghi thử.

<a id="cat-001"></a>


### CAT-001 — Danh mục công khai

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhiều hạng đang hoạt động

- **Thao tác:** Mở danh mục; phân trang nếu có

- **Mong đợi:** Đúng tên, ảnh, giá từ, sức chứa; không lẫn phòng vật lý với hạng phòng

<a id="cat-002"></a>


### CAT-002 — Ẩn hạng không hoạt động

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hạng INACTIVE

- **Thao tác:** Tìm hạng; mở link cũ; thử đặt

- **Mong đợi:** Không cho tạo booking mới với hạng đã ngừng bán; lỗi rõ ràng

<a id="cat-003"></a>


### CAT-003 — Chi tiết đầy đủ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hạng có mô tả, diện tích, giường, ảnh, tiện nghi

- **Thao tác:** Mở chi tiết; xem các khối nội dung

- **Mong đợi:** Các khối cùng một hạng; ảnh không méo; không thiếu dữ liệu bắt buộc

<a id="cat-004"></a>


### CAT-004 — Đặt từ chi tiết

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khoảng ngày còn phòng; khách trong sức chứa

- **Thao tác:** Chọn ngày/số khách; xem giá; tiếp tục

- **Mong đợi:** Giá và ngày chuyển đúng sang booking; chưa tự nhận phòng vật lý

<a id="cat-005"></a>


### CAT-005 — Chưa chọn đủ ngày

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chỉ một ngày hoặc chưa chọn

- **Thao tác:** Thử tiếp tục đặt

- **Mong đợi:** Chặn hoặc hướng dẫn chọn đủ; không tạo booking lỗi

<a id="cat-006"></a>


### CAT-006 — Quá sức chứa tại chi tiết

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Vượt người lớn hoặc trẻ em

- **Thao tác:** Chọn khách vượt giới hạn; thử tiếp tục

- **Mong đợi:** Báo không đáp ứng; backend cũng từ chối nếu vượt UI

<a id="cat-007"></a>


### CAT-007 — Không có ảnh hoặc ảnh lỗi

Unhappy · P2 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hạng không ảnh; URL ảnh 404

- **Thao tác:** Mở danh mục và chi tiết

- **Mong đợi:** Có fallback; vẫn đọc và thao tác được

<a id="cat-008"></a>


### CAT-008 — API danh mục lỗi hoặc rỗng

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Giả lập danh sách rỗng rồi 500

- **Thao tác:** Mở trang; dùng thử lại khi có

- **Mong đợi:** Trạng thái rỗng/lỗi rõ; không giữ thẻ hạng từ request trước


## TIME — Ngày lưu trú và mốc thời gian

**Vai trò:** Khách; lễ tân. **Trang:** `/rooms/search; /bookings/new; trang nhận/trả phòng`.

**Chuẩn bị chung:** D = ngày khách sạn từ /api/v1/stay-policy; Clock backend kiểm soát cho các mốc giờ.

<a id="time-001"></a>


### TIME-001 — Khoảng lưu trú theo đêm

Happy · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** 05/11/2026 → 13/11/2026; Clock trước ngày nhận

- **Thao tác:** Tìm kiếm; mở chi tiết; booking; xem tổng

- **Mong đợi:** 8 đêm ở mọi màn hình; đêm 05–12/11; trả 13/11; không tính 7 vì 14:00→12:00

<a id="time-002"></a>


### TIME-002 — Một đêm tối thiểu

Biên · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** D+1 → D+2

- **Thao tác:** Tìm và đặt

- **Mong đợi:** 1 đêm; tính tiền một đêm

<a id="time-003"></a>


### TIME-003 — Ngày bằng nhau hoặc đảo ngược

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** D+2→D+2; D+3→D+1

- **Thao tác:** Chọn UI; can thiệp query/request

- **Mong đợi:** Từ chối khoảng sai; không tự sửa ngầm rồi tính tiền khác

<a id="time-004"></a>


### TIME-004 — Ngày nhận trong quá khứ

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** D-1→D+1

- **Thao tác:** Thử đặt mới qua UI và request

- **Mong đợi:** Không tạo mới booking quá khứ

<a id="time-005"></a>


### TIME-005 — Đổi tháng, năm và năm nhuận

Biên · P1 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** 30/11→02/12; 31/12→02/01; 28/02→01/03 năm nhuận

- **Thao tác:** Tìm kiếm với Clock phù hợp

- **Mong đợi:** Lần lượt 2, 2, 2 đêm; không phụ thuộc số giờ thực

<a id="time-006"></a>


### TIME-006 — Đặt hôm nay sau giờ nhận

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** D lúc 19:55; D→D+2; còn tồn

- **Thao tác:** Tìm và tạo pending; xem kỳ ở và expiredAt

- **Mong đợi:** Được đặt trước cutoff 23:59:59 dù qua14:00;2 đêm; expiredAt là hạn trả tiền riêng, không tự CHECKED_IN

<a id="time-007"></a>


### TIME-007 — Biên cutoff đặt cùng ngày

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** 23:59:58; 23:59:59; 00:00 ngày mới

- **Thao tác:** Gửi tạo booking cho ngày vừa kết thúc tại từng mốc

- **Mong đợi:** Trước23:59:59 được nếu đủ điều kiện; đúng23:59:59 chặn nhận hôm nay; qua00 ngày cũ bị chặn; ngày tương lai vẫn xem xét

<a id="time-008"></a>


### TIME-008 — Nhận sớm trước 14:00

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** D lúc 13:59:59; phòng AVAILABLE

- **Thao tác:** Nhận phòng có và không có lý do

- **Mong đợi:** Thiếu lý do bị chặn; đủ lý do và không xung đột được nhận

<a id="time-009"></a>


### TIME-009 — Nhận đúng 14:00

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** D lúc 14:00:00

- **Thao tác:** Check-in booking hợp lệ không nhập lý do sớm

- **Mong đợi:** Thành công; không còn bị coi là nhận sớm

<a id="time-010"></a>


### TIME-010 — Khách đến muộn qua nửa đêm

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking nhận D-1; hiện D lúc 05:59:59; trả sau D

- **Thao tác:** Mở khách đến; check-in

- **Mong đợi:** Có thể nhận; giữ ngày đặt ban đầu, không dời giá/ngày

<a id="time-011"></a>


### TIME-011 — Hết cửa sổ đến muộn

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking nhận D-1; hiện D lúc 06:00:00

- **Thao tác:** Tìm và cố check-in

- **Mong đợi:** Không còn đủ điều kiện đến muộn; không giao phòng

<a id="time-012"></a>


### TIME-012 — Đến trước ngày đặt hoặc sau khi hết kỳ ở

Unhappy · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** NhậnD+1 nhưng hiệnD; hoặc nhậnD-2 nhưng hiệnD; hoặc trả<D

- **Thao tác:** Thử check-in

- **Mong đợi:** Từ chối; không coi như ngoại lệ nhận sớm trong cùng ngày

<a id="time-013"></a>


### TIME-013 — Biên trả phòng 12:00

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đến hạn D; lúc 12:00:00 rồi 12:00:01

- **Thao tác:** Thử checkout không ghi chú ở từng mốc

- **Mong đợi:** Đúng 12:00 được; sau 12:00 cần ghi chú xử lý muộn; không tự cộng đêm

<a id="time-014"></a>


### TIME-014 — Không tự checkout hoặc giải phóng phòng

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách CHECKED_IN quá hạn; qua nửa đêm

- **Thao tác:** Chưa thao tác checkout; xem phòng và tìm ngày mới

- **Mong đợi:** Vẫn đang ở và chiếm tồn; chỉ checkout thực tế mới chuyển DIRTY

<a id="time-015"></a>


### TIME-015 — Múi giờ và đồng hồ máy khách sai

Biên · P1 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Browser UTC/US; chỉnh giờ máy khách lệch; server GMT+7

- **Thao tác:** Mở tìm kiếm và nhận/trả phòng

- **Mong đợi:** Ngày/mốc khách sạn theo policy; không đổi quy tắc vì giờ máy khách

<a id="time-016"></a>


### TIME-016 — Thời điểm thực tế và policy

Happy · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhận/trả thử có Clock biết trước

- **Thao tác:** Đối chiếu policy; làm thủ tục; xem thời gian thực tế nếu có

- **Mong đợi:** Lưu checked_in_at/checked_out_at đúng instant UTC; hiển thị theo giờ khách sạn; dữ liệu cũ không bịa giờ


## INV — Tồn phòng theo từng đêm

**Vai trò:** Khách; người đối soát. **Trang:** `/rooms/search`.

**Chuẩn bị chung:** Fixture INV biệt lập; đối chiếu chỉ đọc DB/API theo hướng dẫn, không sửa số trên UI.

<a id="inv-001"></a>


### INV-001 — Đếm theo đúng hạng

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hai hạng có số phòng vật lý khác nhau

- **Thao tác:** Tìm cùng khoảng; so thẻ với dữ liệu chuẩn

- **Mong đợi:** Mỗi thẻ dùng phòng thuộc đúng roomTypeId; không lấy tổng toàn khách sạn

<a id="inv-002"></a>


### INV-002 — Trừ booking CONFIRMED

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** 3 phòng bán được; 1 booking trùng một đêm

- **Thao tác:** Tìm khoảng có đêm đó

- **Mong đợi:** Còn 2; không chỉ đếm trạng thái AVAILABLE hiện tại

<a id="inv-003"></a>


### INV-003 — Giữ chỗ PENDING còn hạn

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** 3 phòng; 1 pending chưa expiredAt

- **Thao tác:** Tìm khoảng trùng

- **Mong đợi:** Còn 2 trong thời gian giữ chỗ

<a id="inv-004"></a>


### INV-004 — PENDING hết hạn đúng instant

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** 3 phòng; pending với expiredAt biết trước

- **Thao tác:** Tìm trước hạn rồi đúng hạn, chưa chạy scheduler

- **Mong đợi:** Đến hạn không giữ chỗ nữa; số tăng 1 dù status chưa được job đổi

<a id="inv-005"></a>


### INV-005 — Booking không chiếm tồn

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** CANCELLED; EXPIRED; CHECKED_OUT

- **Thao tác:** Tìm khoảng từng booking

- **Mong đợi:** Không trừ các trạng thái này khỏi tồn

<a id="inv-006"></a>


### INV-006 — Lấy tồn nhỏ nhất từng đêm

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** 3 phòng; đêm1 bận2, đêm2 bận1

- **Thao tác:** Tìm cả hai đêm

- **Mong đợi:** Còn 1; không cộng gộp tất cả booking giao nhau rồi trả 0

<a id="inv-007"></a>


### INV-007 — Ngày trả không chiếm đêm mới

Biên · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking A trả D+2; B nhận D+2

- **Thao tác:** Tìm/đặt B từ D+2

- **Mong đợi:** Không xung đột ngày theo [nhận,trả); nhận thực tế vẫn cần phòng đã dọn

<a id="inv-008"></a>


### INV-008 — MAINTENANCE và OUT_OF_SERVICE

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đưa phòng vào hai trạng thái không bán được

- **Thao tác:** Tìm lại cùng khoảng

- **Mong đợi:** Trừ khỏi nguồn phòng bán được; không giới thiệu đó là phòng sẵn sàng

<a id="inv-009"></a>


### INV-009 — Phòng DIRTY/CLEANING cho ngày tương lai

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có phòng đang dọn, không booking/maintenance chặn kỳ tương lai

- **Thao tác:** Tìm kỳ tương lai; thử giao phòng hôm nay

- **Mong đợi:** Không đồng nhất tồn bán theo ngày với khả năng giao chìa khóa; check-in chỉ phòng AVAILABLE

<a id="inv-010"></a>


### INV-010 — Khách ở quá ngày trả

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một CHECKED_IN đã quá hạn chưa checkout

- **Thao tác:** Tìm kỳ tương lai

- **Mong đợi:** Booking quá hạn vẫn chặn tồn đến khi xử lý; không bán giả một phòng

<a id="inv-011"></a>


### INV-011 — Hết phòng và số không âm

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tất cả tồn đã bị giữ; thêm fixture dữ liệu thiếu tồn

- **Thao tác:** Tìm kiếm và mở link đặt cũ

- **Mong đợi:** Không hiện thẻ còn phòng sai; tạo booking bị chặn; số không âm

<a id="inv-012"></a>


### INV-012 — Phân trang sau lọc tồn

Happy · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Trang đầu dữ liệu có hạng hết phòng; nhiều hạng còn phòng phía sau

- **Thao tác:** Tìm và chuyển trang

- **Mong đợi:** Không trả trang rỗng giả; tổng/phân trang tính theo hạng đủ điều kiện

<a id="inv-013"></a>


### INV-013 — Hai khách tranh phòng cuối

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chỉ còn 1 phòng; hai browser độc lập

- **Thao tác:** Cùng gửi tạo pending cho cùng đêm

- **Mong đợi:** Chỉ một booking giữ được phòng; bên còn lại báo hết; không overbooking

<a id="inv-014"></a>


### INV-014 — Thay đổi tồn khi đang xem kết quả

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** A mở kết quả; B giữ phòng cuối/manager bảo trì

- **Thao tác:** A chọn phòng sau thay đổi

- **Mong đợi:** Backend kiểm tra lại; không tin số cache; refresh phản ánh tồn mới


## BOOK — Tạo và sửa booking đang giữ

**Vai trò:** Khách ẩn danh; khách A. **Trang:** `/bookings/new`.

**Chuẩn bị chung:** Hạng còn phòng; G là thông tin khách thử; không dùng CCCD thật.

<a id="book-001"></a>


### BOOK-001 — Booking ẩn danh

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ngày hợp lệ; G có tên, email, giấy tờ

- **Thao tác:** Chọn hạng → thông tin → tiếp tục thanh toán

- **Mong đợi:** Một booking PENDING; mã, tổng tiền, hạn giữ và access token phục vụ truy cập riêng

<a id="book-002"></a>


### BOOK-002 — Booking khi đăng nhập

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách A; dữ liệu trong sức chứa

- **Thao tác:** Tạo booking; mở lịch sử A

- **Mong đợi:** Booking thuộc A; dữ liệu tự điền vẫn sửa được theo form

<a id="book-003"></a>


### BOOK-003 — Thông tin bắt buộc thiếu

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Lần lượt thiếu tên/email/giấy tờ; chỉ khoảng trắng

- **Thao tác:** Gửi từng mẫu

- **Mong đợi:** Báo đúng trường; chưa tạo booking

<a id="book-004"></a>


### BOOK-004 — Email và số điện thoại sai

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Email sai; nếu nhập phone thì không đủ 10 số bắt đầu 0

- **Thao tác:** Thử gửi

- **Mong đợi:** Validation rõ; không chấp nhận dữ liệu sai qua UI

<a id="book-005"></a>


### BOOK-005 — Sức chứa độc lập

Biên · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Bằng/vượt maxAdults, maxChildren và maxGuests

- **Thao tác:** Gửi từng mẫu; kể cả sửa request

- **Mong đợi:** Bằng giới hạn được nhận; vượt bất kỳ giới hạn nào bị từ chối

<a id="book-006"></a>


### BOOK-006 — Số khách không hợp lệ

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Adults=0/-1; children=-1; số lẻ; null

- **Thao tác:** Thử query và request

- **Mong đợi:** Từ chối; không tạo booking hoặc giá sai

<a id="book-007"></a>


### BOOK-007 — Hạng mất hiệu lực giữa chừng

Unhappy · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đang mở form; manager ngừng bán hạng

- **Thao tác:** Gửi form cũ

- **Mong đợi:** Bị từ chối; không giữ phòng hạng đã ngừng bán

<a id="book-008"></a>


### BOOK-008 — Nhấn tiếp tục nhiều lần

Đồng thời · P0 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Mạng chậm; form hợp lệ

- **Thao tác:** Double-click hoặc Enter liên tiếp

- **Mong đợi:** UI ngăn submit lặp; kiểm tra chỉ một booking từ một lần thao tác

<a id="book-009"></a>


### BOOK-009 — Quay lại sửa thông tin pending

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Pending còn hạn của A/token đúng

- **Thao tác:** Từ bước thanh toán quay lại sửa; gửi lại

- **Mong đợi:** Sửa cùng booking; không tạo thêm giữ chỗ; tổng và snapshot cập nhật

<a id="book-010"></a>


### BOOK-010 — Sửa pending không gia hạn giữ chỗ

Biên · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Pending sắp hết hạn

- **Thao tác:** Sửa tên/dịch vụ; xem expiredAt

- **Mong đợi:** Hạn giữ gốc không tăng; hết hạn vẫn chặn thanh toán

<a id="book-011"></a>


### BOOK-011 — Sửa pending đã hết hạn

Unhappy · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** expiredAt ≤ serverNow

- **Thao tác:** Bấm quay lại/sửa rồi gửi

- **Mong đợi:** Bị từ chối; không tái giữ tồn bằng booking cũ

<a id="book-012"></a>


### BOOK-012 — Sửa booking của người khác

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** A dùng booking B; token thiếu/sai

- **Thao tác:** Gửi update qua DevTools

- **Mong đợi:** 404/403 phù hợp; không lộ nội dung hoặc sửa booking B

<a id="book-013"></a>


### BOOK-013 — Sửa sau khi đã confirmed

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Callback đã xác nhận thanh toán

- **Thao tác:** Gửi lại request sửa pending cũ

- **Mong đợi:** Không cho sửa theo luồng pending; tiền và snapshot cũ không bị ghi đè

<a id="book-014"></a>


### BOOK-014 — Thay đổi tổng khi đang có QR

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Pending sửa thêm/bớt dịch vụ làm đổi tổng

- **Thao tác:** Sửa; yêu cầu QR lại; xem QR cũ

- **Mong đợi:** Khoản cần trả đúng tổng mới; payment không còn phù hợp được xử lý lại; QR cũ không xác nhận sai số tiền

<a id="book-015"></a>


### BOOK-015 — Query đầu vào giả mạo

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** roomTypeId sai; ngày rác; khách NaN/quá lớn

- **Thao tác:** Mở /bookings/new với từng query

- **Mong đợi:** Hướng dẫn chọn lại/lỗi có kiểm soát; không crash, không tạo dữ liệu rác


## ADD — Dịch vụ bổ sung

**Vai trò:** Khách; ADMIN/MANAGER khi quản trị. **Trang:** `/bookings/new; /add-on-services`.

**Chuẩn bị chung:** Có dịch vụ ACTIVE/INACTIVE; giá fixture 200.000đ/đơn vị.

<a id="add-001"></a>


### ADD-001 — Chọn số lượng dịch vụ

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking phòng 3.000.000đ; dịch vụ 200.000đ x2

- **Thao tác:** Chọn dịch vụ; xem tổng; tạo booking

- **Mong đợi:** Dịch vụ 400.000đ; tổng 3.400.000đ; không nhân thêm số đêm nếu giá là theo đơn vị

<a id="add-002"></a>


### ADD-002 — Bỏ chọn dịch vụ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đã chọn x2

- **Thao tác:** Bỏ chọn hoặc đưa lượng về trạng thái bỏ chọn của UI

- **Mong đợi:** Tổng giảm đúng 400.000đ; không còn dòng dịch vụ đó

<a id="add-003"></a>


### ADD-003 — Số lượng sai hoặc dịch vụ lạ

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Âm, số lẻ, rất lớn; addOnId không tồn tại/inactive

- **Thao tác:** Gửi từng mẫu qua request

- **Mong đợi:** Từ chối dữ liệu không hợp lệ; không tràn số hoặc cộng dịch vụ ẩn

<a id="add-004"></a>


### ADD-004 — Danh mục công khai chỉ bán dịch vụ active

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một active và một inactive

- **Thao tác:** Mở chọn dịch vụ

- **Mong đợi:** Chỉ active có thể chọn mới

<a id="add-005"></a>


### ADD-005 — Tạo dịch vụ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Code mới; tên 2–150; giá ≥0

- **Thao tác:** Admin/manager tạo; tìm lại; mở form booking

- **Mong đợi:** Hiện đúng dữ liệu và giá; quyền khác không có thao tác tạo

<a id="add-006"></a>


### ADD-006 — Dịch vụ trùng hoặc thiếu trường

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Code trùng; tên rỗng/1/151; giá âm

- **Thao tác:** Thử lưu từng mẫu

- **Mong đợi:** Lỗi rõ; không ghi một phần hoặc tạo trùng

<a id="add-007"></a>


### ADD-007 — Đổi giá không đổi booking cũ

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking đã có snapshot; dịch vụ tăng giá

- **Thao tác:** Sửa giá; mở booking cũ và tạo booking mới

- **Mong đợi:** Booking cũ giữ giá đã chốt; booking mới dùng giá mới

<a id="add-008"></a>


### ADD-008 — Ngừng bán và mở bán lại

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Dịch vụ active

- **Thao tác:** Đổi trạng thái; tìm/lọc; mở lại form khách

- **Mong đợi:** Không bán mới khi inactive; dữ liệu booking lịch sử vẫn có

<a id="add-009"></a>


### ADD-009 — Tìm, lọc, sắp xếp, phân trang dịch vụ

Happy · P2 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhiều dịch vụ; bộ lọc có/không kết quả

- **Thao tác:** Thử từng điều khiển; đổi lọc khi ở trang cuối

- **Mong đợi:** Dữ liệu, tổng, trang đúng; không bịa thao tác xóa nếu UI không có


## PAY — Thanh toán QR và callback ngân hàng

**Vai trò:** Chủ booking hoặc token ẩn danh đúng. **Trang:** `/bookings/new; /payments/:paymentId/result`.

**Chuẩn bị chung:** Bank sandbox/fixture webhook; không chuyển tiền thật; lưu booking/payment/providerTransactionId riêng.

<a id="pay-001"></a>


### PAY-001 — Tạo yêu cầu QR

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Pending còn hạn; chưa trả tiền

- **Thao tác:** Chuyển bước thanh toán; tạo QR

- **Mong đợi:** Đúng tài khoản nhận, nội dung mã payment, số còn thiếu và hạn thanh toán

<a id="pay-002"></a>


### PAY-002 — Thanh toán đủ trong hạn

Happy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Payment pending; booking còn hạn

- **Thao tác:** Gửi callback hợp lệ đủ tiền; chờ web cập nhật

- **Mong đợi:** Payment thành công; booking CONFIRMED; paidAmount đúng; không tự CHECKED_IN

<a id="pay-003"></a>


### PAY-003 — Thanh toán nhiều phần

Happy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tổng 1.000.000đ; lần đầu 400.000đ

- **Thao tác:** Gửi lần1; xem web; gửi lần2 600.000đ với mã giao dịch khác

- **Mong đợi:** Lần1 còn thiếu 600.000đ; chỉ sau đủ tổng mới CONFIRMED; không cộng trùng

<a id="pay-004"></a>


### PAY-004 — Callback trùng

Đồng thời · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một giao dịch đã nhận

- **Thao tác:** Gửi lại cùng event/providerTransactionId nhiều lần

- **Mong đợi:** Một lần cộng tiền; không nhân payment/refund/dashboard

<a id="pay-005"></a>


### PAY-005 — Chuyển dư

Happy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Còn thiếu 1.000.000đ; chuyển 1.200.000đ

- **Thao tác:** Gửi callback; xem booking và danh sách refund

- **Mong đợi:** Booking đủ tiền; thừa 200.000đ thành refund PENDING; chưa được coi là đã hoàn

<a id="pay-006"></a>


### PAY-006 — Tiền đến sau hạn

Unhappy · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Pending đã hết hạn chính xác

- **Thao tác:** Gửi tiền đúng mã sau hạn

- **Mong đợi:** Không hồi sinh booking; tạo xử lý hoàn tiền chờ duyệt theo policy

<a id="pay-007"></a>


### PAY-007 — Tiền đến sau hủy

Unhappy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking CANCELLED; payment cũ

- **Thao tác:** Gửi callback đến muộn

- **Mong đợi:** Booking vẫn hủy; tiền được đối soát/refund, không CONFIRMED lại

<a id="pay-008"></a>


### PAY-008 — Webhook sai xác thực

Unhappy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** API key sai hoặc thiếu

- **Thao tác:** Gửi callback; quan sát web và monitor

- **Mong đợi:** Không cộng tiền/xác nhận; không lộ khóa; phản hồi lỗi phù hợp

<a id="pay-009"></a>


### PAY-009 — Sai chiều tiền hoặc tài khoản nhận

Unhappy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Outgoing; bank account không đúng cấu hình

- **Thao tác:** Gửi từng callback

- **Mong đợi:** Không dùng để xác nhận booking; ghi nhận/loại theo handler, không cộng paidAmount

<a id="pay-010"></a>


### PAY-010 — Mã thanh toán thiếu hoặc lạ

Unhappy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nội dung thiếu mã; mã không tồn tại

- **Thao tác:** Gửi callback

- **Mong đợi:** Không gán tùy tiện cho booking khác; có dấu vết đối soát phù hợp

<a id="pay-011"></a>


### PAY-011 — Không có quyền xem payment

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** A dùng payment B; ẩn danh thiếu/sai token

- **Thao tác:** Mở result và gọi status qua DevTools

- **Mong đợi:** Không lộ booking/khách/QR của B; không đổi trạng thái

<a id="pay-012"></a>


### PAY-012 — Tải lại và mở result trực tiếp

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Payment hợp lệ và quyền đúng

- **Thao tác:** F5; mở tab mới; quay lại từ bước đặt

- **Mong đợi:** Trạng thái backend là nguồn chính; không coi tải lại là giao dịch mới

<a id="pay-013"></a>


### PAY-013 — Hạn QR không vượt hold

Biên · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking chỉ còn giữ 2 phút

- **Thao tác:** Tạo QR; yêu cầu lại trước hết hạn

- **Mong đợi:** Hạn payment ≤ expiredAt booking; không kéo dài hold

<a id="pay-014"></a>


### PAY-014 — Yêu cầu lại cùng số tiền

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Payment pending còn hạn, tổng không đổi

- **Thao tác:** Nhấn yêu cầu lại/thực hiện lại request hợp lệ

- **Mong đợi:** Tái dùng yêu cầu còn phù hợp; không tạo nghĩa vụ thu tiền trùng

<a id="pay-015"></a>


### PAY-015 — Không có số dư cần thanh toán

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking đã đủ tiền; hoặc trạng thái không cho trả

- **Thao tác:** Thử tạo payment request

- **Mong đợi:** Không tạo QR số tiền 0/âm hoặc trả lại booking đã hoàn tất

<a id="pay-016"></a>


### PAY-016 — Trả thêm tiền gia hạn

Happy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking CONFIRMED hoặc CHECKED_IN có nợ mới

- **Thao tác:** Tạo QR; gửi đủ tiền còn thiếu

- **Mong đợi:** PaidAmount tăng đúng; giữ nguyên trạng thái đang ở/đã xác nhận; không tạo booking mới

<a id="pay-017"></a>


### PAY-017 — Callback cạnh tranh với hết hạn

Đồng thời · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một hold sắp hết hạn; Clock kiểm soát

- **Thao tác:** Gửi callback sát expiredAt cùng tác vụ hết hạn

- **Mong đợi:** Kết quả nhất quán theo thời điểm xử lý; không vừa bán lại tồn vừa xác nhận cùng hold

<a id="pay-018"></a>


### PAY-018 — Mất mạng khi chờ thanh toán

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Payment đang pending; callback vẫn tới backend

- **Thao tác:** Ngắt mạng web; nối lại/tải lại

- **Mong đợi:** Đọc lại trạng thái thành công thực tế; không yêu cầu trả lần hai

<a id="pay-019"></a>


### PAY-019 — Hết hạn không thanh toán

Unhappy · P1 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Không callback tới hết hạn

- **Thao tác:** Theo dõi countdown và thử tiếp tục

- **Mong đợi:** Thông báo hết hạn; không còn giữ phòng; hướng dẫn đặt lại, không giả thành công


## HIST — Lịch sử đặt phòng của khách

**Vai trò:** Khách A roles=[]. **Trang:** `/account/bookings; /account/bookings/:bookingCode; /account/profile?section=bookings`.

**Chuẩn bị chung:** Có booking A/B và nhiều trạng thái.

<a id="hist-001"></a>


### HIST-001 — Lịch sử đúng chủ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** A có nhiều booking

- **Thao tác:** Mở hai lối vào lịch sử

- **Mong đợi:** Chỉ booking thuộc A; số tiền/ngày/hạng/trạng thái nhất quán

<a id="hist-002"></a>


### HIST-002 — Chi tiết và deep link mã booking

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Mã thuộc A

- **Thao tác:** Nhấn xem; dán link; F5

- **Mong đợi:** Mở đúng chi tiết; không mất quyền sau refresh hợp lệ

<a id="hist-003"></a>


### HIST-003 — Lọc và phân trang lịch sử

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhiều trạng thái, nhiều trang

- **Thao tác:** Lọc trạng thái; chuyển trang; xóa bộ lọc

- **Mong đợi:** Tổng và dữ liệu đúng; reset trang phù hợp; có empty state

<a id="hist-004"></a>


### HIST-004 — Xem mã booking người khác

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Mã B; mã không tồn tại

- **Thao tác:** A nhập trực tiếp URL/request

- **Mong đợi:** Không lộ dữ liệu B; hiển thị không tìm thấy/không quyền

<a id="hist-005"></a>


### HIST-005 — Nút hành động theo trạng thái

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, EXPIRED

- **Thao tác:** Mở từng booking

- **Mong đợi:** Chỉ hiển thị/hỗ trợ hủy, sửa, gia hạn, trả tiền theo trạng thái thực; backend kiểm lại

<a id="hist-006"></a>


### HIST-006 — Booking ẩn danh và lịch sử tài khoản

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đặt ẩn danh rồi đăng nhập A

- **Thao tác:** Mở lịch sử; thử xem bằng mã không token

- **Mong đợi:** Không tự suy diễn quyền chỉ vì cùng email; chỉ hiện nếu thực tế đã liên kết hợp lệ

<a id="hist-007"></a>


### HIST-007 — Lỗi tải và dữ liệu đổi ở tab khác

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tab B cập nhật booking; tab A đang xem

- **Thao tác:** Focus/tải lại A; giả lập 500 rồi thử lại

- **Mong đợi:** Dữ liệu mới thay thế dữ liệu cũ; lỗi không làm mất bộ lọc hoặc lộ booking khác


## CANCEL — Hủy booking và tính hoàn

**Vai trò:** Khách sở hữu booking. **Trang:** `/account/bookings; /account/profile?section=bookings`.

**Chuẩn bị chung:** Clock kiểm soát; giờ nhận theo policy; booking có giao dịch SUCCESS khi cần hoàn.

<a id="cancel-001"></a>


### CANCEL-001 — Hủy pending chưa trả tiền

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Pending của A còn hạn

- **Thao tác:** Mở hủy; xác nhận; tìm tồn lại

- **Mong đợi:** CANCELLED; giải phóng giữ chỗ; không tạo hoàn tiền giả

<a id="cancel-002"></a>


### CANCEL-002 — Pending đã nhận một phần tiền

Unhappy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Pending có tiền nhận nhưng chưa đủ

- **Thao tác:** Xem cảnh báo/chính sách; hủy; đối soát giao dịch

- **Mong đợi:** Không báo đã hoàn tự động; khoản nhận một phần cần đối soát theo giới hạn hiện tại

<a id="cancel-003"></a>


### CANCEL-003 — Hủy trước hoặc đúng 48 giờ

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Confirmed; total 2.000.000đ; còn 48h và 48h+1s

- **Thao tác:** Mở hủy; nhập bank hợp lệ; xác nhận

- **Mong đợi:** Refund dự kiến 2.000.000đ; tạo PENDING; booking hủy

<a id="cancel-004"></a>


### CANCEL-004 — Hủy dưới 48 giờ và đúng 24 giờ

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Còn 47h59m59s rồi 24h

- **Thao tác:** Hủy fixture riêng từng mốc

- **Mong đợi:** Hoàn 50% tổng theo policy; không làm tròn giờ thành 100%

<a id="cancel-005"></a>


### CANCEL-005 — Hủy dưới 24 giờ

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Còn 23h59m59s

- **Thao tác:** Mở và thử gửi hủy

- **Mong đợi:** Bị từ chối; không đổi booking hoặc tạo refund

<a id="cancel-006"></a>


### CANCEL-006 — Thiếu thông tin ngân hàng khi có hoàn

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Confirmed đủ điều kiện; thiếu tên ngân hàng/chủ/số TK

- **Thao tác:** Gửi từng mẫu

- **Mong đợi:** Chặn thiếu trường; không tạo refund thiếu đích nhận

<a id="cancel-007"></a>


### CANCEL-007 — Trạng thái không được hủy

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** CHECKED_IN, CHECKED_OUT, CANCELLED, EXPIRED

- **Thao tác:** Thử nút/DevTools gửi hủy

- **Mong đợi:** Backend từ chối; không đổi trạng thái hay hoàn lần nữa

<a id="cancel-008"></a>


### CANCEL-008 — Hủy booking người khác

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách A dùng booking B

- **Thao tác:** Gửi hủy với bank của A

- **Mong đợi:** Không được phép; dữ liệu B không đổi

<a id="cancel-009"></a>


### CANCEL-009 — Hai tab hủy cùng booking

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Confirmed đủ điều kiện

- **Thao tác:** Xác nhận đồng thời từ hai tab

- **Mong đợi:** Một lần chuyển trạng thái; tối đa một nghĩa vụ refund cho lần hủy

<a id="cancel-010"></a>


### CANCEL-010 — Sửa số tiền hoàn trên request

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Backend tính được 50%; client gửi 100%

- **Thao tác:** Can thiệp request và gửi

- **Mong đợi:** Số hoàn do backend quyết định; không nhận giá trị client giả mạo

<a id="cancel-011"></a>


### CANCEL-011 — Đóng modal hủy

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Mở modal chưa xác nhận

- **Thao tác:** Đóng/Cancel rồi mở lại

- **Mong đợi:** Booking không thay đổi; không phát sinh refund

<a id="cancel-012"></a>


### CANCEL-012 — Hủy sau gia hạn hoặc nhiều giao dịch

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Total và paidAmount khác nhau; nhiều payment SUCCESS

- **Thao tác:** Xem quote và đối chiếu từng giao dịch trước khi xác nhận

- **Mong đợi:** Phát hiện hoàn vượt tiền thực nhận/hoàn sai payment là lỗi nghiệp vụ cần xử lý; không ghi Pass chỉ vì API 200


## EXT — Gia hạn lưu trú

**Vai trò:** Khách là bookedBy của booking. **Trang:** `/account/bookings; /account/profile?section=bookings`.

**Chuẩn bị chung:** Booking CONFIRMED/CHECKED_IN của A; có kỳ gia hạn còn và hết phòng.

<a id="ext-001"></a>


### EXT-001 — Gia hạn booking confirmed

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ngày trả D+3; xin thêm tới D+5 còn tồn

- **Thao tác:** Mở gia hạn; chọn ngày; xác nhận

- **Mong đợi:** Ngày trả D+5; thêm 2 đêm theo giá hiện tại; vẫn CONFIRMED

<a id="ext-002"></a>


### EXT-002 — Gia hạn khi đang ở

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** CHECKED_IN có phòng gán không xung đột

- **Thao tác:** Gia hạn tới ngày hợp lệ

- **Mong đợi:** Giữ đúng phòng đang ở; vẫn CHECKED_IN; nợ tăng đúng

<a id="ext-003"></a>


### EXT-003 — Ngày gia hạn không tiến lên

Biên · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ngày mới bằng/trước ngày trả hiện tại

- **Thao tác:** Thử chọn và gửi

- **Mong đợi:** Bị từ chối; không tạo đêm giá 0/âm

<a id="ext-004"></a>


### EXT-004 — Hết tồn một đêm trong đoạn thêm

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chỉ một đêm thêm bị hết phòng

- **Thao tác:** Yêu cầu gia hạn nhiều đêm

- **Mong đợi:** Từ chối toàn bộ; không cập nhật dở ngày/giá

<a id="ext-005"></a>


### EXT-005 — Phòng đang ở xung đột booking kế tiếp

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Còn phòng khác cùng hạng nhưng phòng hiện tại đã gán khách sau

- **Thao tác:** Gia hạn

- **Mong đợi:** Bị từ chối; không tự chuyển sang phòng khác hoặc đẩy booking kế tiếp

<a id="ext-006"></a>


### EXT-006 — Giữ snapshot cũ, tính giá mới cho đêm thêm

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Giá cũ khác giá hiện tại; thêm 2 đêm x400.000đ

- **Thao tác:** Đổi giá rồi gia hạn

- **Mong đợi:** Đêm cũ không đổi; thêm 800.000đ; tổng/nợ khớp

<a id="ext-007"></a>


### EXT-007 — Booking không được gia hạn

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** PENDING, EXPIRED, CANCELLED, CHECKED_OUT

- **Thao tác:** Gửi gia hạn

- **Mong đợi:** Từ chối mọi trạng thái không hợp lệ

<a id="ext-008"></a>


### EXT-008 — Không phải chủ booking

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** A gia hạn B; hoặc nhân viên không phải bookedBy

- **Thao tác:** Gửi request trực tiếp

- **Mong đợi:** Không được gia hạn thay qua endpoint chỉ cho chủ; không suy diễn nhân viên được mọi quyền

<a id="ext-009"></a>


### EXT-009 — Hai yêu cầu gia hạn cùng lúc

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hai tab cùng booking; ngày mới khác nhau

- **Thao tác:** Gửi đồng thời

- **Mong đợi:** Không mất cập nhật/nhân đôi đêm; kết quả giá và ngày là một lịch hợp lệ

<a id="ext-010"></a>


### EXT-010 — Thanh toán khoản gia hạn

Happy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có nợ mới; hoặc đã đủ tiền sau QR

- **Thao tác:** Trả QR; hoặc để thu đúng dư nợ khi checkout

- **Mong đợi:** Không thu lại tiền cũ; status lưu trú được giữ; số còn thiếu giảm đúng


## ARR — Danh sách khách đến

**Vai trò:** RECEPTIONIST; ADMIN/MANAGER chỉ quyền được cấp. **Trang:** `/front-desk/arrivals/today`.

**Chuẩn bị chung:** D từ server; booking đến hôm nay và hôm qua sát cutoff.

<a id="arr-001"></a>


### ARR-001 — Danh sách và thống kê khách đến

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có nhiều trạng thái/ngày đến

- **Thao tác:** Mở; tìm/lọc/phân trang theo UI

- **Mong đợi:** Đúng phạm vi và tổng; không lẫn khách tương lai với khách đến hôm nay

<a id="arr-002"></a>


### ARR-002 — Bổ sung khách đến muộn hôm qua

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** D trước 06:00 rồi đúng 06:00

- **Thao tác:** Mở danh sách ở hai mốc

- **Mong đợi:** Chỉ bổ sung booking hôm qua đủ điều kiện trong cửa sổ muộn

<a id="arr-003"></a>


### ARR-003 — Từ danh sách sang check-in

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Lễ tân; booking hợp lệ

- **Thao tác:** Ghi/sao chép mã từ danh sách; mở menu Check-in; nhập mã và tìm phòng

- **Mong đợi:** Đúng mã và cửa sổ; nhận qua màn hình riêng, không giả định có nút check-in trên bảng khách đến

<a id="arr-004"></a>


### ARR-004 — Admin/manager không tự có quyền nhận phòng

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** ADMIN hoặc MANAGER chỉ có role đó

- **Thao tác:** Mở danh sách; thử hành động và URL check-in

- **Mong đợi:** Đọc được danh sách; không vượt quyền check-in riêng của lễ tân

<a id="arr-005"></a>


### ARR-005 — Sau khi đã nhận phòng

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Lễ tân nhận ở tab khác

- **Thao tác:** Refresh danh sách

- **Mong đợi:** Trạng thái/thống kê cập nhật; không còn gợi ý nhận lần hai

<a id="arr-006"></a>


### ARR-006 — Không có khách đến hoặc API lỗi

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Fixture rỗng; sau đó 500

- **Thao tác:** Mở và thử lại

- **Mong đợi:** Empty/error rõ; không hiện dữ liệu ngày trước như dữ liệu hôm nay


## CIN — Nhận phòng

**Vai trò:** RECEPTIONIST. **Trang:** `/front-desk/check-in`.

**Chuẩn bị chung:** Booking đủ tiền CONFIRMED; phòng cùng hạng AVAILABLE; đúng cửa sổ giờ.

<a id="cin-001"></a>


### CIN-001 — Nhận phòng chuẩn

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Mã booking hợp lệ; từ 14:00 ngày nhận

- **Thao tác:** Tra mã; tìm phòng khả dụng; chọn phòng; xác nhận

- **Mong đợi:** Booking CHECKED_IN, gán đúng phòng; phòng OCCUPIED; ghi thời điểm thực tế

<a id="cin-002"></a>


### CIN-002 — Mã rỗng hoặc không tồn tại

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Trống; mã giả

- **Thao tác:** Tra từng mẫu

- **Mong đợi:** Validation/không tìm thấy; không hiển thị nhầm khách trước

<a id="cin-003"></a>


### CIN-003 — Booking chưa thanh toán đủ

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** PENDING có/không tiền một phần

- **Thao tác:** Tra và cố nhận

- **Mong đợi:** Không nhận phòng; hướng dẫn xử lý thanh toán

<a id="cin-004"></a>


### CIN-004 — Nhận lại hoặc booking đã kết thúc

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** CHECKED_IN/CHECKED_OUT/CANCELLED/EXPIRED

- **Thao tác:** Gửi nhận phòng lần nữa

- **Mong đợi:** Không đổi phòng/ghi lại giờ; không bàn giao hai phòng

<a id="cin-005"></a>


### CIN-005 — Chọn phòng sai hạng

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** RoomId của hạng khác

- **Thao tác:** Sửa request chọn phòng

- **Mong đợi:** Backend từ chối dù phòng AVAILABLE

<a id="cin-006"></a>


### CIN-006 — Chọn phòng không sẵn sàng

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** OCCUPIED, DIRTY, CLEANING, MAINTENANCE, OUT_OF_SERVICE

- **Thao tác:** Thử chọn/gửi từng trạng thái

- **Mong đợi:** Không được nhận; chỉ AVAILABLE hợp lệ và không xung đột

<a id="cin-007"></a>


### CIN-007 — Phòng được gán booking xung đột

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** AVAILABLE hiện tại nhưng có lịch gán xung đột kỳ ở

- **Thao tác:** Tìm phòng; gửi roomId trực tiếp

- **Mong đợi:** Không cấp phòng xung đột; không dựa riêng status hiện tại

<a id="cin-008"></a>


### CIN-008 — Lý do nhận sớm

Biên · P1 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Trước 14:00; lý do trắng/500/501 ký tự

- **Thao tác:** Gửi từng mẫu

- **Mong đợi:** Cần lý do có nội dung ≤500; lỗi không thay trạng thái

<a id="cin-009"></a>


### CIN-009 — Hai lễ tân lấy cùng phòng

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hai booking cùng hạng; một phòng AVAILABLE

- **Thao tác:** Hai phiên nhận cùng roomId

- **Mong đợi:** Chỉ một lần chiếm phòng; bên còn lại báo xung đột

<a id="cin-010"></a>


### CIN-010 — Hai lễ tân nhận cùng booking

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một booking; hai phòng khác nhau

- **Thao tác:** Xác nhận đồng thời

- **Mong đợi:** Chỉ một phòng được gán/OCCUPIED cho booking; phòng còn lại không bị giữ nhầm

<a id="cin-011"></a>


### CIN-011 — Thiếu/sai quyền check-in

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách, housekeeping, ADMIN-only, MANAGER-only

- **Thao tác:** Mở URL và gửi API bằng phiên tương ứng

- **Mong đợi:** 403/redirect đúng; không thay booking/phòng

<a id="cin-012"></a>


### CIN-012 — Mất phản hồi sau khi đã xử lý

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Backend thành công nhưng web timeout

- **Thao tác:** Tải lại/tra lại rồi thử thao tác

- **Mong đợi:** Hiển thị trạng thái đã nhận; không gán thêm hoặc ghi thời điểm lần hai


## COUT — Trả phòng thông thường và thu tiền

**Vai trò:** RECEPTIONIST / MANAGER / ADMIN. **Trang:** `/front-desk/departures/today`.

**Chuẩn bị chung:** Booking CHECKED_IN; phòng OCCUPIED; đến ngày trả D; số dư từ backend.

<a id="cout-001"></a>


### COUT-001 — Checkout đã đủ tiền, không phí

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Dư nợ 0; phí 0

- **Thao tác:** Mở trả phòng; xác nhận

- **Mong đợi:** CHECKED_OUT; phòng DIRTY; ghi giờ thực tế; không tạo phiếu thu 0/thu trùng

<a id="cout-002"></a>


### COUT-002 — Thu dư nợ và phí phát sinh

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Dư nợ 500.000đ; phí 150.000đ có lý do

- **Thao tác:** Nhập thu đúng 650.000đ; xác nhận

- **Mong đợi:** Phiếu CASH SUCCESS 650.000đ, người thu/giờ; tổng và paidAmount khớp; checkout thành công

<a id="cout-003"></a>


### COUT-003 — Thu thiếu hoặc thừa

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Cần 650.000đ; nhập 649.999/650.001

- **Thao tác:** Thử xác nhận mỗi mẫu

- **Mong đợi:** Không checkout hoặc ghi phiếu thu sai; yêu cầu đúng số cần thu

<a id="cout-004"></a>


### COUT-004 — Tiền âm, số lẻ hoặc quá giới hạn

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phí/thu=-1, 1.5, >9.999.999.999

- **Thao tác:** Thử UI và request

- **Mong đợi:** Validation backend; không làm tròn ngầm hoặc tràn số

<a id="cout-005"></a>


### COUT-005 — Có phí nhưng thiếu lý do

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phí >0; lý do trống/độ dài quá giới hạn

- **Thao tác:** Thử gửi

- **Mong đợi:** Chặn trước cập nhật; giữ nguyên trạng thái và số tiền

<a id="cout-006"></a>


### COUT-006 — Trả muộn cần ghi chú

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Sau 12:00 ngày trả; hoặc đã quá ngày

- **Thao tác:** Thử thiếu ghi chú rồi điền lý do

- **Mong đợi:** Thiếu bị chặn; có lý do được xử lý; không tự thu thêm một đêm

<a id="cout-007"></a>


### COUT-007 — Checkout sai luồng ngày

Unhappy · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chưa đến ngày trả và chọn luồng thường

- **Thao tác:** Thử thao tác

- **Mong đợi:** Phải dùng trả sớm để tính credit; không bỏ qua chính sách hoàn bằng luồng thường

<a id="cout-008"></a>


### COUT-008 — Booking/phòng không hợp lệ

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking CONFIRMED hoặc đã checkout; room không OCCUPIED

- **Thao tác:** Gửi checkout

- **Mong đợi:** Từ chối; không tạo thu/đổi phòng sai

<a id="cout-009"></a>


### COUT-009 — Hai nhân viên checkout cùng booking

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking còn dư nợ

- **Thao tác:** Xác nhận đồng thời từ hai phiên

- **Mong đợi:** Một checkout và một phiếu thu; không nhân đôi thu tiền

<a id="cout-010"></a>


### COUT-010 — Khách trả QR lúc nhân viên thu tiền

Đồng thời · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Cùng khoản dư nợ; callback đến trong lúc mở modal

- **Thao tác:** Gửi callback rồi submit form cũ

- **Mong đợi:** Recheck dư nợ; không vừa thu bank vừa thu cash cho cùng khoản

<a id="cout-011"></a>


### COUT-011 — Tính nguyên tử khi lỗi ghi phiếu thu

Happy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Giả lập lỗi transaction trong môi trường test

- **Thao tác:** Thực hiện checkout có tiền mặt

- **Mong đợi:** Rollback đồng bộ booking/phòng/phiếu thu; không báo thành công một phần

<a id="cout-012"></a>


### COUT-012 — Đủ ba vai trò được checkout

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ba fixture độc lập; R, M, A

- **Thao tác:** Lần lượt đăng nhập và trả phòng

- **Mong đợi:** Tất cả được phép theo quyền; các vai trò khác bị chặn


## EARLY — Trả phòng sớm

**Vai trò:** RECEPTIONIST / MANAGER / ADMIN. **Trang:** `/front-desk/departures/today`.

**Chuẩn bị chung:** Booking CHECKED_IN; ngày D trước ngày trả dự kiến; room OCCUPIED.

<a id="early-001"></a>


### EARLY-001 — Quote trả sớm theo đêm chưa dùng

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ngày thực tế D; các đêm snapshot rõ

- **Thao tác:** Mở trả sớm; xem bảng tính trước xác nhận

- **Mong đợi:** Chỉ đêm stayDate ≥D chưa dùng được tính; phí 20%, credit 80%; add-on không được hoàn theo đêm

<a id="early-002"></a>


### EARLY-002 — Credit lớn hơn nợ và phí

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** U=2.000.000đ; nợ300.000đ; phí200.000đ

- **Thao tác:** Nhập bank hợp lệ; xác nhận

- **Mong đợi:** Credit1.600.000đ; refund PENDING1.100.000đ; không thu cash; CHECKED_OUT/DIRTY

<a id="early-003"></a>


### EARLY-003 — Credit nhỏ hơn nợ và phí

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** U=1.000.000đ; tổng nợ+phí1.200.000đ

- **Thao tác:** Thu đúng400.000đ; xác nhận

- **Mong đợi:** Không refund; một phiếu thu400.000đ; checkout thành công

<a id="early-004"></a>


### EARLY-004 — Credit bằng khoản cần bù

Biên · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Credit800.000đ; nợ+phí800.000đ

- **Thao tác:** Xác nhận không thu thêm

- **Mong đợi:** Không thu/hoàn 0; checkout thành công

<a id="early-005"></a>


### EARLY-005 — Ngày thực tế không phải hôm nay

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chọn ngày quá khứ/tương lai bằng request

- **Thao tác:** Gửi execute trả sớm

- **Mong đợi:** Từ chối; không lùi hoặc ghi trước giờ thực tế

<a id="early-006"></a>


### EARLY-006 — Ngoài khoảng trả sớm

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ngày <checkIn hoặc ≥checkOut dự kiến

- **Thao tác:** Gửi quote/execute theo validation

- **Mong đợi:** Không áp dụng trả sớm sai khoảng; không hoàn đêm không tồn tại

<a id="early-007"></a>


### EARLY-007 — Thiếu bank khi thực có refund

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Quote refund>0; thiếu trường nhận tiền

- **Thao tác:** Gửi xác nhận

- **Mong đợi:** Từ chối; không tạo refund thiếu đích nhận

<a id="early-008"></a>


### EARLY-008 — Sai số tiền thu hoặc phí

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Quote cần thu400.000đ; nhập khác; phí âm/lẻ

- **Thao tác:** Gửi xác nhận

- **Mong đợi:** Backend tính lại và từ chối số sai; không tin quote/client cũ

<a id="early-009"></a>


### EARLY-009 — Quote cũ hoặc gửi hai lần

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Quote xong có giao dịch mới; hoặc hai tab

- **Thao tác:** Gửi form cũ/đồng thời

- **Mong đợi:** Tính lại theo dữ liệu hiện tại; một checkout; không tạo hai khoản thu/hoàn

<a id="early-010"></a>


### EARLY-010 — Đối soát sau trả sớm

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Checkout sớm thành công

- **Thao tác:** Xem lịch sử booking, phòng, refund/phiếu thu, dashboard

- **Mong đợi:** Các số khớp công thức; refund pending chưa trừ dòng tiền thực đến khi hoàn SUCCESS


## HOUSE — Buồng phòng và vòng đời phòng

**Vai trò:** HOUSEKEEPER. **Trang:** `/housekeeping`.

**Chuẩn bị chung:** Có phòng DIRTY, CLEANING, AVAILABLE, OCCUPIED.

<a id="house-001"></a>


### HOUSE-001 — Bắt đầu dọn

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng DIRTY vừa checkout

- **Thao tác:** Chọn bắt đầu dọn; tải lại

- **Mong đợi:** DIRTY→CLEANING; không làm AVAILABLE ngay

<a id="house-002"></a>


### HOUSE-002 — Hoàn tất dọn

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng CLEANING

- **Thao tác:** Nhấn hoàn tất; lễ tân tìm phòng lại

- **Mong đợi:** CLEANING→AVAILABLE; phòng xuất hiện trong phòng có thể giao khi không có xung đột

<a id="house-003"></a>


### HOUSE-003 — Chuyển trạng thái sai

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** AVAILABLE/OCCUPIED/MAINTENANCE; hoặc DIRTY→AVAILABLE trực tiếp

- **Thao tác:** Thử thao tác và API

- **Mong đợi:** Từ chối bước nhảy không hợp lệ; không đẩy khách đang ở ra khỏi phòng

<a id="house-004"></a>


### HOUSE-004 — Hai người cùng bắt đầu dọn

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một phòng DIRTY

- **Thao tác:** Hai phiên nhấn bắt đầu

- **Mong đợi:** Một chuyển trạng thái; bên còn lại nhận dữ liệu mới/xung đột; không ghi hai phiên làm việc sai

<a id="house-005"></a>


### HOUSE-005 — Hoàn tất trên dữ liệu cũ

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng đã được phiên khác hoàn tất

- **Thao tác:** Submit lại từ tab cũ

- **Mong đợi:** Không đổi trạng thái trái phép hoặc tác động booking khác

<a id="house-006"></a>


### HOUSE-006 — Danh sách, bộ lọc và làm mới

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có phòng hai trạng thái

- **Thao tác:** Mở; lọc/tìm theo UI; refresh

- **Mong đợi:** Số phòng, trạng thái, tổng thống nhất; empty state đúng

<a id="house-007"></a>


### HOUSE-007 — Vai trò khác mở buồng phòng

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách; lễ tân; manager-only; admin-only

- **Thao tác:** Mở URL và gọi thao tác

- **Mong đợi:** Không có quyền theo cấu hình hiện tại; không suy diễn cấp trên tự có quyền này

<a id="house-008"></a>


### HOUSE-008 — Vòng quay trong cùng ngày

Happy · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách cũ trả12:00; khách mới đến14:00

- **Thao tác:** Checkout→bắt đầu dọn→hoàn tất→check-in khách mới

- **Mong đợi:** Chỉ nhận khách mới sau AVAILABLE; giữ lịch theo ngày và giờ bàn giao hợp lệ


## ROOM — Quản lý phòng vật lý

**Vai trò:** ADMIN / MANAGER. **Trang:** `/management/rooms`.

**Chuẩn bị chung:** Dữ liệu UAT riêng; không xóa phòng có lịch sử thật.

<a id="room-001"></a>


### ROOM-001 — Tạo phòng mới

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Số phòng mới; hạng hợp lệ; tầng hợp lệ

- **Thao tác:** Tạo; tìm lại; mở chi tiết

- **Mong đợi:** Một phòng đúng hạng; mặc định AVAILABLE; tồn liên quan cập nhật

<a id="room-002"></a>


### ROOM-002 — Trùng số phòng

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Số đã tồn tại

- **Thao tác:** Tạo hoặc đổi một phòng khác về số này

- **Mong đợi:** Báo trùng; không tạo bản sao hoặc đổi nhầm phòng

<a id="room-003"></a>


### ROOM-003 — Định dạng số và tầng

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Số1–30 ký tự A-Z/a-z/0-9/_/-; mẫu sai; tầng -10/200 và ngoài biên

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Chấp nhận đúng biên; từ chối ký tự/độ dài/tầng không hợp lệ

<a id="room-004"></a>


### ROOM-004 — Sửa phòng AVAILABLE

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng không có ràng buộc ngăn sửa

- **Thao tác:** Sửa tầng/ghi chú hợp lệ; F5

- **Mong đợi:** Lưu đúng; không tự đổi hạng/trạng thái ngoài yêu cầu

<a id="room-005"></a>


### ROOM-005 — Sửa hoặc xóa phòng chưa AVAILABLE

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Lần lượt OCCUPIED/DIRTY/CLEANING/MAINTENANCE/OOS

- **Thao tác:** Thử thao tác qua UI và request

- **Mong đợi:** Backend chặn; không làm mất phòng đang phục vụ

<a id="room-006"></a>


### ROOM-006 — Đổi hạng khi hạng cũ có nhu cầu đang hiệu lực

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng AVAILABLE nhưng hạng có booking active

- **Thao tác:** Yêu cầu đổi hạng

- **Mong đợi:** Không làm hụt tồn của booking đã bán; giữ quan hệ cũ khi bị chặn

<a id="room-007"></a>


### ROOM-007 — Xóa phòng thử không có lịch sử

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** AVAILABLE; không FK/demand chặn

- **Thao tác:** Xóa; xác nhận; tìm lại

- **Mong đợi:** Phòng biến mất; tổng và tồn giảm đúng một

<a id="room-008"></a>


### ROOM-008 — Xóa phòng có ràng buộc lịch sử

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng có booking/tham chiếu

- **Thao tác:** Yêu cầu xóa

- **Mong đợi:** Lỗi nghiệp vụ rõ; không xóa dây chuyền dữ liệu booking

<a id="room-009"></a>


### ROOM-009 — Ghi chú và dữ liệu trống

Biên · P2 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ghi chú2000/2001; thiếu số/hạng; tầng số lẻ

- **Thao tác:** Thử lưu

- **Mong đợi:** Đúng giới hạn; dữ liệu sai không ghi một phần

<a id="room-010"></a>


### ROOM-010 — Tìm kiếm, lọc, phân trang, đồng bộ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhiều hạng/trạng thái; tạo/sửa ở tab khác

- **Thao tác:** Thử bộ lọc; về cuối trang; refresh/focus

- **Mong đợi:** Tổng và hàng đúng dữ liệu DB; không tự seed thêm phòng khi khởi động lại backend


## RTYPE — Quản lý hạng phòng và ảnh

**Vai trò:** ADMIN / MANAGER. **Trang:** `/management/room-types`.

**Chuẩn bị chung:** Amenity có sẵn; MinIO test hoạt động; ảnh JPG/PNG thử không có dữ liệu riêng.

<a id="rtype-001"></a>


### RTYPE-001 — Tạo hạng đầy đủ

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Code mới; giá/sức chứa/diện tích hợp lệ; tiện nghi

- **Thao tác:** Tạo; mở catalog và chi tiết khách

- **Mong đợi:** Một hạng đúng dữ liệu; ACTIVE bán được khi có phòng vật lý

<a id="rtype-002"></a>


### RTYPE-002 — Mã hạng trùng hoặc sai

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Trùng; chữ thường/ký tự ngoài A-Z0-9_-; >50

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Không tạo trùng; lỗi định dạng rõ

<a id="rtype-003"></a>


### RTYPE-003 — Biên văn bản

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên150/151; shortDesc500/501; desc10000/10001; giường100/101

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Chấp nhận đúng tối đa; không cắt ngầm dữ liệu

<a id="rtype-004"></a>


### RTYPE-004 — Biên giá và sức chứa

Biên · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Giá0/âm; adults1/100/101; child0/100/101; maxGuests

- **Thao tác:** Thử lưu từng tổ hợp

- **Mong đợi:** Giá không âm; adults≤maxGuests≤adults+children; giới hạn số đúng

<a id="rtype-005"></a>


### RTYPE-005 — Diện tích và tiện nghi

Biên · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** 0.01/9999.99 và ngoài biên; amenityId sai

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Diện tích đúng giới hạn; không nhận tiện nghi không tồn tại

<a id="rtype-006"></a>


### RTYPE-006 — Tải ảnh hợp lệ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** JPEG/PNG thực, ≤5MB, ≤25MP

- **Thao tác:** Upload; chọn/sắp ảnh nếu UI hỗ trợ; lưu; mở khách

- **Mong đợi:** Ảnh lưu bền; thumbnail/ảnh đầu đúng; không lẫn ảnh hạng khác

<a id="rtype-007"></a>


### RTYPE-007 — Giả định dạng hoặc ảnh hỏng

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Text đổi đuôi .jpg; SVG/GIF; MIME giả; file hỏng

- **Thao tác:** Upload từng mẫu

- **Mong đợi:** Kiểm nội dung thực; chỉ JPEG/PNG hợp lệ; không lưu dữ liệu giả ảnh

<a id="rtype-008"></a>


### RTYPE-008 — Giới hạn upload

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** File0 byte; >5MB; >25MP; ảnh thứ21

- **Thao tác:** Thử từng giới hạn

- **Mong đợi:** Từ chối đúng giới hạn; không mất ảnh đã lưu trước đó

<a id="rtype-009"></a>


### RTYPE-009 — Kho ảnh lỗi và URL giả

Unhappy · P1 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** MinIO timeout; tự gửi URL ảnh mới không thuộc kho

- **Thao tác:** Lưu/upload với fixture

- **Mong đợi:** Báo lỗi có kiểm soát; không ghi URL ngoài tùy ý vào hạng mới

<a id="rtype-010"></a>


### RTYPE-010 — Sửa giá và ngừng bán

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có booking cũ snapshot

- **Thao tác:** Đổi thông tin/giá; INACTIVE; mở booking cũ và tìm mới

- **Mong đợi:** Booking cũ giữ giá; hạng ngừng bán không được đặt mới

<a id="rtype-011"></a>


### RTYPE-011 — Xóa hạng có ràng buộc

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có phòng/booking/quy tắc giá tham chiếu

- **Thao tác:** Yêu cầu xóa

- **Mong đợi:** Chặn theo ràng buộc; không xóa dữ liệu lịch sử

<a id="rtype-012"></a>


### RTYPE-012 — Hạng không có ràng buộc và danh sách

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hạng UAT trống; nhiều kết quả

- **Thao tác:** Xóa fixture trống nếu UI có; tìm/lọc/trang

- **Mong đợi:** Danh sách và tổng đúng; không áp dụng xóa cho hạng đang vận hành


## PRICE — Quy tắc giá và cách tính tiền đêm

**Vai trò:** ADMIN / MANAGER; khách đối chiếu giá. **Trang:** `/price-rules; /rooms/search`.

**Chuẩn bị chung:** Hạng thử giá gốc1.000.000đ; rule riêng, không sửa giá hạng đang dùng thật.

<a id="price-001"></a>


### PRICE-001 — Giá gốc không có rule

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hai đêm không có rule áp dụng

- **Thao tác:** Tìm; xem chi tiết từng đêm; tạo booking

- **Mong đợi:** 1.000.000đ/đêm, tổng2.000.000đ; VND không có phần thập phân

<a id="price-002"></a>


### PRICE-002 — Cuối tuần tăng giá

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** WEEKEND +10%; thứ bảy/chủ nhật

- **Thao tác:** Tạo rule; tìm kỳ gồm ngày thường/cuối tuần

- **Mong đợi:** Chỉ T7/CN đủ ngày hiệu lực thành1.100.000đ; không áp T6 như weekend

<a id="price-003"></a>


### PRICE-003 — Mùa tăng/giảm và khuyến mãi

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** SEASONAL +20%; PROMOTION giảm10% cùng đêm

- **Thao tác:** Tìm ngày đủ điều kiện

- **Mong đợi:** Chọn modifier mùa rồi promotion:1.000.000×1.2×0.9=1.080.000đ

<a id="price-004"></a>


### PRICE-004 — Ưu tiên loại rule, không cộng mọi phụ phí

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** HOLIDAY/WEEKEND/SEASONAL cùng áp dụng; holiday active

- **Thao tác:** Tìm ngày trùng cả ba

- **Mong đợi:** Chọn một modifier HOLIDAY > WEEKEND > SEASONAL; sau đó một promotion tốt nhất; không cộng cả ba

<a id="price-005"></a>


### PRICE-005 — Cùng loại rule và promotion tốt nhất

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Hai rule cùng loại; khác priority; hai promotion

- **Thao tác:** Tìm; xem applied rules

- **Mong đợi:** Modifier theo priority và tie-break backend; promotion có mức giảm lớn nhất; kết quả lặp lại ổn định

<a id="price-006"></a>


### PRICE-006 — Ngày hiệu lực rule và ngày trả

Biên · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** startDate=endDate một ngày; booking trả đúng ngày đó

- **Thao tác:** So đêm trước, trong, sau ngày hiệu lực

- **Mong đợi:** Rule gồm cả start/end cho stayDate; ngày trả không phải đêm tính phí

<a id="price-007"></a>


### PRICE-007 — Giới hạn giá trị điều chỉnh

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phần trăm0/100/101; số tiền âm; priority0/1000/1001

- **Thao tác:** Tạo/sửa từng mẫu

- **Mong đợi:** 0–100%; số tiền không âm; priority0–1000; lỗi không lưu rule

<a id="price-008"></a>


### PRICE-008 — Hướng điều chỉnh không hợp lệ

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** PROMOTION tăng; WEEKEND/HOLIDAY giảm; BASE qua API

- **Thao tác:** Thử form và can thiệp request

- **Mong đợi:** Từ chối hướng sai; BASE không quản lý qua API này

<a id="price-009"></a>


### PRICE-009 — Rule thiếu trường hoặc ngày đảo

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên1/151; thiếu hạng/loại/ngày; start>end

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Validation đúng; không phát sinh rule nửa vời

<a id="price-010"></a>


### PRICE-010 — Giảm lớn hơn giá và làm tròn

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Promotion fixed>giá; giá lẻ nhân %

- **Thao tác:** Tìm và đặt

- **Mong đợi:** Giá cuối không âm; giảm bị chặn ở tiền hiện có; chia phần trăm theo số nguyên VND

<a id="price-011"></a>


### PRICE-011 — Sửa/ngừng rule không đổi snapshot cũ

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking đã tạo trước thay đổi

- **Thao tác:** Sửa/tắt rule; tìm mới và xem booking cũ

- **Mong đợi:** Giá tìm mới cập nhật; đêm của booking cũ giữ giá đã chốt

<a id="price-012"></a>


### PRICE-012 — Tổng các đêm và bộ lọc quản trị

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Kỳ nhiều loại đêm; nhiều rule

- **Thao tác:** Mở popover giá; cộng từng đêm; lọc/sắp/trang rule

- **Mong đợi:** Tổng đêm bằng tổng phòng; rule active/hạng/ngày đúng; không lẫn giá add-on


## HOL — Lịch ngày lễ

**Vai trò:** ADMIN / MANAGER. **Trang:** `/price-rules`.

**Chuẩn bị chung:** Mục quản lý lịch ngày lễ; rule HOLIDAY cho hạng thử.

<a id="hol-001"></a>


### HOL-001 — Tạo và sửa lịch lễ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên2–150; start≤end

- **Thao tác:** Tạo; tìm lại; sửa khoảng ngày

- **Mong đợi:** Lưu đúng; hiển thị trạng thái active

<a id="hol-002"></a>


### HOL-002 — Lễ một ngày và biên hai đầu

Biên · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** start=end; sau đó dải3 ngày

- **Thao tác:** Tìm từng stayDate quanh dải

- **Mong đợi:** Ngày đầu/cuối được coi ngày lễ; ngoài dải không áp dụng

<a id="hol-003"></a>


### HOL-003 — Lịch lễ không hợp lệ

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên trống/1/151; thiếu ngày; start>end

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Lỗi rõ; không tạo lịch sai

<a id="hol-004"></a>


### HOL-004 — Tắt lịch lễ

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có rule HOLIDAY và lịch lễ active

- **Thao tác:** Tắt lịch; tìm lại ngày trong dải

- **Mong đợi:** Không áp HOLIDAY chỉ vì rule có khoảng ngày; cần lịch lễ active

<a id="hol-005"></a>


### HOL-005 — Tìm, lọc và phân trang lễ

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhiều lịch active/inactive

- **Thao tác:** Thử các điều khiển có trên UI

- **Mong đợi:** Dữ liệu/tổng đúng; đổi lọc không kẹt trang rỗng giả

<a id="hol-006"></a>


### HOL-006 — Quyền và trường API không hỗ trợ

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khách gọi CRUD; thêm field tùy ý vào request manager

- **Thao tác:** Thử mỗi mẫu

- **Mong đợi:** Khách bị chặn; trường không được hỗ trợ bị validation; không tự có lịch lặp hàng năm nếu chưa triển khai


## MAINT — Bảo trì và chi phí

**Vai trò:** MANAGER. **Trang:** `/maintenance; /rooms/maintenance`.

**Chuẩn bị chung:** Phòng UAT AVAILABLE; Clock D; hai tài khoản manager để thử revision.

<a id="maint-001"></a>


### MAINT-001 — Tạo yêu cầu bảo trì

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng AVAILABLE; title/description hợp lệ

- **Thao tác:** Tạo ticket

- **Mong đợi:** Ticket PENDING; phòng MAINTENANCE; giảm tồn bán; cùng transaction

<a id="maint-002"></a>


### MAINT-002 — Phòng không phù hợp hoặc ticket trùng

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phòng OCCUPIED/DIRTY/CLEANING/OOS; phòng đã có ticket mở

- **Thao tác:** Thử tạo

- **Mong đợi:** Từ chối; không đổi sai trạng thái hoặc tạo hai ticket mở

<a id="maint-003"></a>


### MAINT-003 — Biên nội dung ticket

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tiêu đề trống/150/151; mô tả2000/2001

- **Thao tác:** Tạo/sửa theo UI

- **Mong đợi:** Nhận đúng giới hạn; lỗi không đổi phòng

<a id="maint-004"></a>


### MAINT-004 — Vòng đời yêu cầu

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ticket PENDING

- **Thao tác:** Bắt đầu→IN_PROGRESS; hoàn tất→COMPLETED

- **Mong đợi:** Chuyển đúng thứ tự; hoàn tất trả phòng về AVAILABLE

<a id="maint-005"></a>


### MAINT-005 — Chuyển trạng thái sai

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** PENDING→COMPLETED; COMPLETED→IN_PROGRESS; lặp bước

- **Thao tác:** Thử UI/request

- **Mong đợi:** Backend chặn; không mở lại hoặc hoàn tất hai lần

<a id="maint-006"></a>


### MAINT-006 — Kết thúc bảo trì qua API cũ khi còn ticket

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có ticket đang mở

- **Thao tác:** Gọi thao tác kết thúc bảo trì trực tiếp/legacy

- **Mong đợi:** Không bỏ qua ticket; phải dùng vòng đời yêu cầu

<a id="maint-007"></a>


### MAINT-007 — Ghi chi phí lần đầu

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** IN_PROGRESS/COMPLETED; amount1.000.000; revision0

- **Thao tác:** Ghi ngày hợp lệ và ghi chú; lưu

- **Mong đợi:** Một tổng chi phí; có người ghi/giờ/history; dashboard phản ánh ngày chi phí

<a id="maint-008"></a>


### MAINT-008 — Biên tiền và ghi chú chi phí

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** 0 có lý do; âm/lẻ; >999.999.999.999; note trống/>2000

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** 0 có giải thích hợp lệ; số nguyên trong biên; dữ liệu sai bị chặn

<a id="maint-009"></a>


### MAINT-009 — Biên ngày chi phí

Biên · P0 · Web + Clock máy chủ kiểm soát · **Chưa chạy**

- **Dữ liệu/điều kiện:** Trước ngày tạo; ngày tạo; D; D+1

- **Thao tác:** Lưu từng mẫu

- **Mong đợi:** Chỉ ngày tạo≤expenseDate≤D; ngày theo múi giờ khách sạn

<a id="maint-010"></a>


### MAINT-010 — Ghi chi phí khi ticket chưa bắt đầu

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ticket PENDING

- **Thao tác:** Gửi lưu chi phí

- **Mong đợi:** Bị từ chối; không hạch toán công việc chưa được xử lý

<a id="maint-011"></a>


### MAINT-011 — Hai manager sửa cùng revision

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Cùng revision hiện tại; tổng mới khác nhau

- **Thao tác:** Hai phiên cùng lưu

- **Mong đợi:** Một thành công; stale revision409; không lost update; lịch sử bất biến

<a id="maint-012"></a>


### MAINT-012 — Sửa tổng chi phí và cảnh báo thiếu phí

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tổng cũ1m; đổi1.5m; ticket khác completed chưa có cost

- **Thao tác:** Lưu sửa; xem dashboard/history; mở URL legacy

- **Mong đợi:** Dashboard tính1.5m, không2.5m; có cảnh báo thiếu chi phí; legacy redirect đúng


## REF — Duyệt hoàn tiền thủ công

**Vai trò:** MANAGER. **Trang:** `/payment-monitoring/refunds`.

**Chuẩn bị chung:** Refund PENDING từ hủy/tiền dư/trả sớm; bank sandbox, không chuyển thật.

<a id="ref-001"></a>


### REF-001 — Danh sách và chi tiết hoàn

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có refund PENDING/SUCCESS

- **Thao tác:** Lọc/tìm/trang; mở bản ghi

- **Mong đợi:** Đúng booking, nguồn tiền, số tiền, đích nhận; PENDING chưa được hiểu là đã trả khách

<a id="ref-002"></a>


### REF-002 — Xác nhận sau đối soát ngoài hệ thống

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Có chứng từ chuyển tiền sandbox thực sự

- **Thao tác:** Nhập mã tham chiếu/người xử lý theo form; xác nhận

- **Mong đợi:** Refund SUCCESS một lần; lưu dấu vết; dashboard trừ đúng ngày hoàn thành

<a id="ref-003"></a>


### REF-003 — Thiếu chứng từ/thông tin bắt buộc

Unhappy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Thiếu mã tham chiếu theo form

- **Thao tác:** Thử xác nhận

- **Mong đợi:** Validation rõ; không giả rằng đã có giao dịch ngân hàng

<a id="ref-004"></a>


### REF-004 — Duyệt lại hoặc số tiền bị sửa

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Refund đã SUCCESS; request đổi amount

- **Thao tác:** Thử gửi lại/sửa payload

- **Mong đợi:** Không hoàn hai lần; không nhận số tiền client trái bản ghi

<a id="ref-005"></a>


### REF-005 — Hai manager cùng duyệt

Đồng thời · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một refund PENDING

- **Thao tác:** Xác nhận đồng thời

- **Mong đợi:** Một kết quả thành công; một dấu vết hoàn tiền, không nhân đôi dòng tiền

<a id="ref-006"></a>


### REF-006 — Admin-only và vai trò khác duyệt refund

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** ADMIN-only, lễ tân, buồng phòng, khách

- **Thao tác:** Mở URL/gửi confirm

- **Mong đợi:** Không có quyền; MANAGER là role được cấp hiện tại

<a id="ref-007"></a>


### REF-007 — Đã tạo refund nhưng chưa chuyển khoản

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Refund PENDING; chưa có chứng từ ngoài

- **Thao tác:** Theo dõi web/đối chiếu dashboard

- **Mong đợi:** Giữ pending; không tự mô tả hệ thống đã chuyển tiền hoặc có callback hoàn tự động


## MON — Giám sát thanh toán và webhook

**Vai trò:** ADMIN / MANAGER; raw payload chỉ ADMIN. **Trang:** `/payment-monitoring/payments; /payment-monitoring/webhook-events`.

**Chuẩn bị chung:** Có PAYMENT/REFUND, nhiều trạng thái và webhook hợp lệ/không hợp lệ.

<a id="mon-001"></a>


### MON-001 — Theo dõi giao dịch và số liệu

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhiều giao dịch, có cash/QR/refund

- **Thao tác:** Mở tabs; lọc/tìm/trang; làm mới

- **Mong đợi:** Dòng giao dịch, tổng, trạng thái đúng; cash không mang nhầm mã giao dịch bank

<a id="mon-002"></a>


### MON-002 — Nhật ký webhook

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Callback đã xử lý, trùng, không hợp lệ

- **Thao tác:** Tìm event theo mã; xem kết quả xử lý

- **Mong đợi:** Có dấu vết phù hợp; không đếm event trùng thành tiền nhận mới

<a id="mon-003"></a>


### MON-003 — Admin xem raw payload

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** ADMIN; event tồn tại

- **Thao tác:** Mở drawer raw payload

- **Mong đợi:** Chỉ payload event đúng; không lộ server API key hoặc secret cấu hình

<a id="mon-004"></a>


### MON-004 — Manager không xem raw

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** MANAGER-only; biết ID event

- **Thao tác:** Thử drawer và API raw trực tiếp

- **Mong đợi:** Không hiện raw; API chặn dù đọc được danh sách/tổng

<a id="mon-005"></a>


### MON-005 — ID không tồn tại và filter sai

Unhappy · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Event/payment UUID không tồn tại; query sai

- **Thao tác:** Thử điều khiển và request

- **Mong đợi:** Không tìm thấy/validation phù hợp; không dùng bản ghi trước thay thế

<a id="mon-006"></a>


### MON-006 — Lỗi tải trong monitor

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** API500/timeout

- **Thao tác:** Mở trang; retry/refresh

- **Mong đợi:** Thông báo lỗi; không hiển thị dữ liệu cũ như số liệu vừa tải

<a id="mon-007"></a>


### MON-007 — Đối chiếu chuỗi giao dịch

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Một booking gồm partial, full, excess refund, cash nếu có

- **Thao tác:** So booking, payment, webhook, refund, dashboard

- **Mong đợi:** Mỗi khoản tiền được tính đúng một lần; không có nút reprocess/retry giả nếu UI chưa hỗ trợ


## DASH — Dashboard và đối soát dòng tiền

**Vai trò:** ADMIN / MANAGER. **Trang:** `/dashboard`.

**Chuẩn bị chung:** Bộ tiền fixture có mốc UTC và expenseDate rõ; không dùng tổng tiền booking làm tiền đã thu.

<a id="dash-001"></a>


### DASH-001 — Tiền thực thu và hoàn thành công

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhận2m; refund SUCCESS300k; refund PENDING200k

- **Thao tác:** Chọn khoảng chứa ngày nhận/hoàn

- **Mong đợi:** Thực thu2m; hoàn thực300k; thuần1.7m; không trừ pending hoặc cộng booking chưa trả

<a id="dash-002"></a>


### DASH-002 — Chi phí và số còn sau bảo trì

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Net1.7m; chi phí bảo trì hiện hành500k

- **Thao tác:** Mở dashboard và chi tiết

- **Mong đợi:** Còn1.2m; nhãn không gọi là lợi nhuận kế toán đầy đủ

<a id="dash-003"></a>


### DASH-003 — Biên khoảng báo cáo

Biên · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** from00:00; to00:00; giao dịch sát hai biên

- **Thao tác:** Chọn khoảng và đối chiếu API

- **Mong đợi:** Bao gồm from, loại to theo [from,to) trong giờ khách sạn; UI ngày cuối =to-1

<a id="dash-004"></a>


### DASH-004 — Khoảng tối đa và sai

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** 366/367 ngày; ngày đảo; thiếu ngày

- **Thao tác:** Lọc từng mẫu

- **Mong đợi:** Đúng giới hạn366; từ chối khoảng sai; không trả dữ liệu tùy tiện

<a id="dash-005"></a>


### DASH-005 — Hoàn ngày khác ngày nhận

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhận trong tháng1; refund thành công tháng2

- **Thao tác:** Xem từng tháng

- **Mong đợi:** Thu ở ngày nhận; hoàn ở ngày hoàn thực tế; không sửa ngược báo cáo thành cash giả

<a id="dash-006"></a>


### DASH-006 — Sửa chi phí không cộng dồn

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chi phí đổi1m→1.5m; có history1m

- **Thao tác:** Xem dashboard và lịch sử

- **Mong đợi:** Chỉ tổng hiện hành1.5m theo expenseDate; history không bị cộng thêm

<a id="dash-007"></a>


### DASH-007 — Dòng tiền âm và không dữ liệu

Biên · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Khoảng chỉ có refund; khoảng rỗng

- **Thao tác:** Xem số/charts

- **Mong đợi:** Hiện âm hợp lệ; rỗng có0/empty theo thiết kế; không lỗi biểu đồ

<a id="dash-008"></a>


### DASH-008 — Thống kê booking theo trạng thái

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Nhiều status có bộ đếm chuẩn

- **Thao tác:** So tổng, status và chart

- **Mong đợi:** Đúng định nghĩa bộ đếm backend; không trộn tổng booking với số phòng hoặc tiền thực thu

<a id="dash-009"></a>


### DASH-009 — Lỗi báo cáo và chi phí chưa ghi

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Giả lập500; completed ticket chưa có cost

- **Thao tác:** Mở/retry; xem cảnh báo

- **Mong đợi:** Không báo thành công giả; hiện thiếu chi phí, không ngầm xem đó là chi phí0 đã xác nhận


## AI — Trợ lý AI trên web

**Vai trò:** Khách đã đăng nhập; ẩn danh kiểm tra chặn. **Trang:** `Widget AI trên các trang có hiển thị`.

**Chuẩn bị chung:** Dịch vụ AI/RAG sandbox hoạt động; dữ liệu booking A/B riêng.

<a id="ai-001"></a>


### AI-001 — Khách ẩn danh dùng AI

Unhappy · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chưa đăng nhập

- **Thao tác:** Mở widget; thử gửi API trực tiếp

- **Mong đợi:** Widget mời đăng nhập; API yêu cầu xác thực; không đọc booking riêng

<a id="ai-002"></a>


### AI-002 — Hỏi chính sách và nguồn

Happy · P1 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đã đăng nhập; policy được nạp

- **Thao tác:** Hỏi giờ nhận/trả, hủy, dọn phòng

- **Mong đợi:** Trả lời phù hợp policy hiện hành; có nguồn khi có; không bịa chính sách mới

<a id="ai-003"></a>


### AI-003 — Streaming câu trả lời

Happy · P1 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Câu hỏi hợp lệ

- **Thao tác:** Gửi; quan sát từng phần; đóng/mở widget

- **Mong đợi:** Không lặp đoạn, mất chữ hoặc chặn toàn trang; không cho gửi chồng vô hạn

<a id="ai-004"></a>


### AI-004 — Tin nhắn rỗng và độ dài

Biên · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Trống; whitespace; 2000/2001 ký tự

- **Thao tác:** Thử gửi bằng UI và request

- **Mong đợi:** Không gửi rỗng; giới hạn UI2000; API xử lý dữ liệu ngoài biên an toàn

<a id="ai-005"></a>


### AI-005 — AI gợi ý đặt phòng

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Action CONTINUE_BOOKING có hạng/ngày/khách

- **Thao tác:** Nhấn action

- **Mong đợi:** Mở booking với đúng tham số; backend recheck tồn/giá; AI không tự đặt hoặc thanh toán

<a id="ai-006"></a>


### AI-006 — Action hủy/dịch vụ/thanh toán

Happy · P0 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Action REVIEW_CANCELLATION/SELECT_ADDONS/VIEW_PAYMENT

- **Thao tác:** Nhấn từng action trên fixture thuộc A

- **Mong đợi:** Đúng màn hình/booking/payment; vẫn cần quyền và xác nhận nghiệp vụ

<a id="ai-007"></a>


### AI-007 — Hỏi booking người khác

Unhappy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** A hỏi mã B; action chứa ID B

- **Thao tác:** Gửi câu hỏi và thử action

- **Mong đợi:** Không lộ PII/trạng thái/tiền của B; backend chặn truy cập dù AI trả link

<a id="ai-008"></a>


### AI-008 — Prompt yêu cầu vượt quyền

Unhappy · P0 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Yêu cầu tự hoàn tiền, đổi role, bỏ qua policy hoặc lấy secret

- **Thao tác:** Gửi prompt kiểm thử trong sandbox

- **Mong đợi:** Không thực thi tác vụ ngoài quyền; không lộ token/khóa; mọi hành động cần quy trình thật

<a id="ai-009"></a>


### AI-009 — AI timeout, lỗi hoặc rate limit

Unhappy · P1 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** Provider500/SSE đứt/429

- **Thao tác:** Gửi; dùng Thử lại khi có

- **Mong đợi:** Lỗi rõ; retry không tạo booking/payment; không hiển thị thành công giả

<a id="ai-010"></a>


### AI-010 — Đổi tài khoản và dữ liệu nhạy cảm

Unhappy · P0 · Web + hai phiên độc lập · **Chưa chạy**

- **Dữ liệu/điều kiện:** A có hội thoại; logout rồi login B cùng browser

- **Thao tác:** Mở widget; thử conversationId cũ

- **Mong đợi:** B không đọc được thông tin riêng của A; cần ghi lỗi nếu lịch sử bị dùng chéo


## SEC — Kiểm soát truy cập và tính toàn vẹn

**Vai trò:** Từng vai trò; hai khách A/B. **Trang:** `Các URL bảo vệ và API tương ứng`.

**Chuẩn bị chung:** Chỉ thử ở môi trường UAT được phép; dùng DevTools với dữ liệu fixture.

<a id="sec-001"></a>


### SEC-001 — IDOR trên tất cả tài nguyên riêng

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Booking/payment/refund/conversation của B

- **Thao tác:** A thay ID/mã trong URL, query, body

- **Mong đợi:** Không đọc/sửa được B; không chỉ ẩn nút ở frontend

<a id="sec-002"></a>


### SEC-002 — JWT sửa hoặc hết hạn

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Token đổi role/sub/signature; token hết hạn

- **Thao tác:** Gọi API bảo vệ

- **Mong đợi:** 401/403; không tin role trong localStorage; không vượt quyền

<a id="sec-003"></a>


### SEC-003 — Gọi API khi nút đã ẩn

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tài khoản thấp quyền; biết endpoint nhân viên

- **Thao tác:** Gửi CRUD/check-in/out/refund/cost trực tiếp

- **Mong đợi:** Backend kiểm quyền độc lập UI; không mutation khi bị chặn

<a id="sec-004"></a>


### SEC-004 — Giả mạo giá, tổng, trạng thái

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Thêm totalAmount, paidAmount, status, collector, timestamps vào request khách

- **Thao tác:** Gửi tạo/sửa/thanh toán

- **Mong đợi:** Server quyết định tiền/trạng thái/người thao tác; không nhận số liệu đặc quyền của client

<a id="sec-005"></a>


### SEC-005 — XSS lưu và phản chiếu

Unhappy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Chuỗi <img src=x onerror=alert(1)> trong tên/ghi chú/chat/tìm kiếm

- **Thao tác:** Lưu và xem ở trang khách/nhân viên

- **Mong đợi:** Hiển thị text an toàn; không thực thi script hoặc hỏng trang

<a id="sec-006"></a>


### SEC-006 — Chuỗi truy vấn bất thường

Unhappy · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Dấu nháy, SQL-like, Unicode, chuỗi dài trong tìm/mã

- **Thao tác:** Nhập/gửi thử ở sandbox

- **Mong đợi:** Không lỗi500/lộ SQL/stacktrace; validation hoặc kết quả rỗng hợp lệ

<a id="sec-007"></a>


### SEC-007 — Rò rỉ qua URL, log và bộ nhớ dùng chung

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Payment link/token, mail reset, profile

- **Thao tác:** Kiểm URL, console, lỗi network, Back sau logout

- **Mong đợi:** Không lộ secret hệ thống; không tái dùng dữ liệu riêng sau đổi tài khoản; token khách được xử lý như thông tin truy cập

<a id="sec-008"></a>


### SEC-008 — Refresh token dùng lại hoặc hai phiên

Đồng thời · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Refresh đã rotate; request đồng thời

- **Thao tác:** Thử replay token cũ và refresh đồng thời theo policy

- **Mong đợi:** Không cấp phiên trái phép; web không refresh vô hạn; lỗi rõ

<a id="sec-009"></a>


### SEC-009 — Rate limit theo IP không bị giả header

Unhappy · P1 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Môi trường test với trusted proxy được cấu hình

- **Thao tác:** Thử đổi X-Forwarded-For tùy ý từ nguồn không tin cậy

- **Mong đợi:** Không dễ vượt limiter chỉ bằng header giả; không làm khóa người dùng khác

<a id="sec-010"></a>


### SEC-010 — Không để lỗi hạ tầng thành thao tác thành công

Unhappy · P1 · Web + dịch vụ ngoài giả lập/test · **Chưa chạy**

- **Dữ liệu/điều kiện:** DB/Redis/mail/storage/AI test lỗi có kiểm soát

- **Thao tác:** Chạy ca lỗi tích hợp; xem web và audit

- **Mong đợi:** Không báo thu/hoàn/nhận/trả thành công khi transaction thất bại; không lộ secret


## UX — Trải nghiệm, lỗi mạng và tương thích

**Vai trò:** Mọi vai trò phù hợp màn hình. **Trang:** `Toàn bộ trang có trong ma trận`.

**Chuẩn bị chung:** Chrome/Edge/Firefox/Safari nếu có; desktop và mobile; Network DevTools.

<a id="ux-001"></a>


### UX-001 — Responsive các luồng chính

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** 360/390/768/1440px

- **Thao tác:** Đi từ tìm phòng→booking→QR; mở bảng nhân viên

- **Mong đợi:** Đọc/nhấn được ngày, số tiền, CTA; không mất cột hành động; bảng có cách cuộn phù hợp

<a id="ux-002"></a>


### UX-002 — Bàn phím và focus

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Không dùng chuột

- **Thao tác:** Tab/Shift+Tab/Enter/Escape qua menu, form, modal

- **Mong đợi:** Focus thấy được; label đúng; modal giữ/trả focus; không gửi form ngoài ý muốn

<a id="ux-003"></a>


### UX-003 — Thông báo lỗi có thể sửa

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Form sai nhiều trường

- **Thao tác:** Submit; sửa từng trường; gửi lại

- **Mong đợi:** Lỗi cạnh trường; không xóa toàn bộ dữ liệu đã nhập; spinner kết thúc

<a id="ux-004"></a>


### UX-004 — Mạng chậm và double-click

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Slow3G/latency cao

- **Thao tác:** Tạo/sửa/thu/hoàn/check-in trên fixture

- **Mong đợi:** Có loading/disable phù hợp; không nhân đôi thao tác; retry đọc lại trạng thái

<a id="ux-005"></a>


### UX-005 — Request tìm kiếm về sai thứ tự

Đồng thời · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Query A chậm; đổi ngay sang B

- **Thao tác:** Tìm hai khoảng liên tiếp

- **Mong đợi:** Kết quả B không bị phản hồi A ghi đè; ngày header, tồn, giá cùng query

<a id="ux-006"></a>


### UX-006 — Offline và phục hồi

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Mở form có dữ liệu; mất mạng

- **Thao tác:** Submit khi offline; nối lại; thử lại

- **Mong đợi:** Lỗi rõ; không giả thành công; phục hồi đúng trạng thái backend

<a id="ux-007"></a>


### UX-007 — 401/403/404/409/429/500

Unhappy · P1 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Giả lập từng status tại API phù hợp

- **Thao tác:** Mở trang/thao tác

- **Mong đợi:** Thông báo và hành vi đúng lỗi; không logout với mọi500; không retry vô hạn409/429

<a id="ux-008"></a>


### UX-008 — Định dạng Việt Nam và giá trị dài

Happy · P2 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Tên dài có dấu; VND lớn; ngày qua năm

- **Thao tác:** Xem thẻ, modal, bảng, QR, dashboard

- **Mong đợi:** dd/MM/yyyy và VND nhất quán; không cắt mất số tiền hoặc nhầm ngày/tháng

<a id="ux-009"></a>


### UX-009 — Ảnh/API phụ lỗi không phá luồng chính

Unhappy · P2 · Web + giả lập mạng · **Chưa chạy**

- **Dữ liệu/điều kiện:** Ảnh404; add-on500; AI lỗi

- **Thao tác:** Mở/tìm/đặt theo điều kiện trang

- **Mong đợi:** Fallback/lỗi cục bộ rõ; không crash toàn trang hoặc gửi thiếu dịch vụ mà không báo

<a id="ux-010"></a>


### UX-010 — Refresh/Back/đổi tài khoản

Happy · P1 · Web · **Chưa chạy**

- **Dữ liệu/điều kiện:** Đang ở bước payment hoặc modal nghiệp vụ

- **Thao tác:** F5; Back/Forward; logout/login vai trò khác

- **Mong đợi:** Không replay mutation; quyền và cache được cập nhật; không hiện dữ liệu phiên cũ


## RBAC — Ma trận quyền theo trang

**Vai trò:** Mỗi tài khoản chỉ một role thử; khách roles=[]. **Trang:** `URL cụ thể trong tên ca`.

**Chuẩn bị chung:** Tách browser profile cho từng role; không dùng tài khoản đa quyền để kết luận về quyền đơn.

<a id="rbac-001"></a>


### RBAC-001 — Quyền cho phép: /account/profile

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Khách roles=[], Lễ tân, Buồng phòng, Quản lý, Admin

- **Thao tác:** Lần lượt mở /account/profile từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Chỉ hồ sơ của chính tài khoản; nhân viên chỉ phần cá nhân.

<a id="rbac-002"></a>


### RBAC-002 — Quyền từ chối: /account/profile

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh

- **Thao tác:** Mở /account/profile bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-003"></a>


### RBAC-003 — Quyền cho phép: /account/bookings; /account/bookings/:bookingCode

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Khách roles=[]

- **Thao tác:** Lần lượt mở /account/bookings; /account/bookings/:bookingCode từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Khách roles=[]; chỉ booking thuộc mình. Lối vào hồ sơ section=bookings cũng phải kiểm quyền.

<a id="rbac-004"></a>


### RBAC-004 — Quyền từ chối: /account/bookings; /account/bookings/:bookingCode

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Lễ tân, Buồng phòng, Quản lý, Admin

- **Thao tác:** Mở /account/bookings; /account/bookings/:bookingCode bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-005"></a>


### RBAC-005 — Quyền cho phép: /dashboard

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý

- **Thao tác:** Lần lượt mở /dashboard từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Dòng tiền và bảo trì, không phải lợi nhuận kế toán đầy đủ.

<a id="rbac-006"></a>


### RBAC-006 — Quyền từ chối: /dashboard

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng

- **Thao tác:** Mở /dashboard bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-007"></a>


### RBAC-007 — Quyền cho phép: /front-desk/check-in

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Lễ tân

- **Thao tác:** Lần lượt mở /front-desk/check-in từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Không tự cấp check-in cho ADMIN-only/MANAGER-only.

<a id="rbac-008"></a>


### RBAC-008 — Quyền từ chối: /front-desk/check-in

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Buồng phòng, Quản lý, Admin

- **Thao tác:** Mở /front-desk/check-in bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-009"></a>


### RBAC-009 — Quyền cho phép: /front-desk/arrivals/today

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý, Lễ tân

- **Thao tác:** Lần lượt mở /front-desk/arrivals/today từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Quyền đọc danh sách không đồng nghĩa quyền check-in.

<a id="rbac-010"></a>


### RBAC-010 — Quyền từ chối: /front-desk/arrivals/today

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Buồng phòng

- **Thao tác:** Mở /front-desk/arrivals/today bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-011"></a>


### RBAC-011 — Quyền cho phép: /front-desk/departures/today

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý, Lễ tân

- **Thao tác:** Lần lượt mở /front-desk/departures/today từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Cả ba role được checkout thường và trả sớm.

<a id="rbac-012"></a>


### RBAC-012 — Quyền từ chối: /front-desk/departures/today

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Buồng phòng

- **Thao tác:** Mở /front-desk/departures/today bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-013"></a>


### RBAC-013 — Quyền cho phép: /housekeeping

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Buồng phòng

- **Thao tác:** Lần lượt mở /housekeeping từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Chuyển DIRTY→CLEANING→AVAILABLE.

<a id="rbac-014"></a>


### RBAC-014 — Quyền từ chối: /housekeeping

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Quản lý, Admin

- **Thao tác:** Mở /housekeeping bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-015"></a>


### RBAC-015 — Quyền cho phép: /maintenance

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Quản lý

- **Thao tác:** Lần lượt mở /maintenance từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Tạo/tiến hành/hoàn tất ticket và chi phí.

<a id="rbac-016"></a>


### RBAC-016 — Quyền từ chối: /maintenance

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng, Admin

- **Thao tác:** Mở /maintenance bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-017"></a>


### RBAC-017 — Quyền cho phép: /rooms/maintenance

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Quản lý

- **Thao tác:** Lần lượt mở /rooms/maintenance từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. URL cũ redirect /maintenance; vẫn giữ cùng guard.

<a id="rbac-018"></a>


### RBAC-018 — Quyền từ chối: /rooms/maintenance

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng, Admin

- **Thao tác:** Mở /rooms/maintenance bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-019"></a>


### RBAC-019 — Quyền cho phép: /management/rooms

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý

- **Thao tác:** Lần lượt mở /management/rooms từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. CRUD phòng phải thỏa trạng thái và ràng buộc.

<a id="rbac-020"></a>


### RBAC-020 — Quyền từ chối: /management/rooms

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng

- **Thao tác:** Mở /management/rooms bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-021"></a>


### RBAC-021 — Quyền cho phép: /management/room-types

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý

- **Thao tác:** Lần lượt mở /management/room-types từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. CRUD hạng, ảnh, tiện nghi hiện có.

<a id="rbac-022"></a>


### RBAC-022 — Quyền từ chối: /management/room-types

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng

- **Thao tác:** Mở /management/room-types bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-023"></a>


### RBAC-023 — Quyền cho phép: /add-on-services

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý

- **Thao tác:** Lần lượt mở /add-on-services từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Quản trị dịch vụ; danh mục active để chọn khi đặt là công khai.

<a id="rbac-024"></a>


### RBAC-024 — Quyền từ chối: /add-on-services

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng

- **Thao tác:** Mở /add-on-services bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-025"></a>


### RBAC-025 — Quyền cho phép: /price-rules

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý

- **Thao tác:** Lần lượt mở /price-rules từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Quy tắc giá và lịch lễ.

<a id="rbac-026"></a>


### RBAC-026 — Quyền từ chối: /price-rules

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng

- **Thao tác:** Mở /price-rules bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-027"></a>


### RBAC-027 — Quyền cho phép: /payment-monitoring/payments

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý

- **Thao tác:** Lần lượt mở /payment-monitoring/payments từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Danh sách giao dịch/tổng; không tự gán thanh toán thành công.

<a id="rbac-028"></a>


### RBAC-028 — Quyền từ chối: /payment-monitoring/payments

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng

- **Thao tác:** Mở /payment-monitoring/payments bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-029"></a>


### RBAC-029 — Quyền cho phép: /payment-monitoring/webhook-events

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin, Quản lý

- **Thao tác:** Lần lượt mở /payment-monitoring/webhook-events từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Manager đọc danh sách, không raw payload.

<a id="rbac-030"></a>


### RBAC-030 — Quyền từ chối: /payment-monitoring/webhook-events

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng

- **Thao tác:** Mở /payment-monitoring/webhook-events bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-031"></a>


### RBAC-031 — Quyền cho phép: /payment-monitoring/refunds

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Quản lý

- **Thao tác:** Lần lượt mở /payment-monitoring/refunds từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. ADMIN-only không có quyền duyệt hoàn theo cấu hình hiện tại.

<a id="rbac-032"></a>


### RBAC-032 — Quyền từ chối: /payment-monitoring/refunds

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng, Admin

- **Thao tác:** Mở /payment-monitoring/refunds bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm

<a id="rbac-033"></a>


### RBAC-033 — Quyền cho phép: Raw payload trong /payment-monitoring/webhook-events

Happy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Admin

- **Thao tác:** Lần lượt mở Raw payload trong /payment-monitoring/webhook-events từ menu, URL trực tiếp và F5; thử API đọc/thao tác tương ứng với fixture hợp lệ

- **Mong đợi:** Các role được cấp truy cập đúng; vẫn kiểm chủ sở hữu và điều kiện nghiệp vụ. Kiểm cả drawer và API raw; MANAGER bị từ chối.

<a id="rbac-034"></a>


### RBAC-034 — Quyền từ chối: Raw payload trong /payment-monitoring/webhook-events

Unhappy · P0 · Web + API fixture/DevTools · **Chưa chạy**

- **Dữ liệu/điều kiện:** Phiên riêng cho Ẩn danh, Khách roles=[], Lễ tân, Buồng phòng, Quản lý

- **Thao tác:** Mở Raw payload trong /payment-monitoring/webhook-events bằng URL trực tiếp; gọi API tương ứng bằng DevTools dù menu bị ẩn

- **Mong đợi:** Ẩn danh cần đăng nhập; phiên không có role bị403/không quyền; API từ chối độc lập UI; không đọc/ghi dữ liệu cấm
