# Quy trình kiểm thử xuyên suốt trên web

14 quy trình; mỗi quy trình cần fixture độc lập. Trạng thái: **Chưa chạy**. Đọc [hướng dẫn](web-uat-guide.md) và [checklist chi tiết](web-test-cases.md) để chạy đủ các biến thể.

## E2E-01 — Ẩn danh→ngân hàng→lễ tân→buồng phòng

**Ưu tiên:** P0. **Chuẩn bị:** Kỳ ở hợp lệ; đủ tiền ban đầu; phòng sẵn sàng; Clock tới ngày nhận/trả

1. Tìm ngày/khách và nhấn tên hạng kiểm đúng chi tiết. 2. Chọn phòng, nhập G, tạo pending. 3. Tạo QR, gửi callback sandbox đủ tiền. 4. Xác minh confirmed. 5. Lễ tân tra mã và check-in. 6. Đến hạn trả, checkout. 7. Buồng phòng bắt đầu/hoàn tất dọn. 8. Tìm và nhận khách mới trên phòng đó.

**Mong đợi và nhánh lỗi:** Một booking/một lần thu; pending→confirmed→checked-in→checked-out; available→occupied→dirty→cleaning→available. Nhánh lỗi: chưa đủ tiền không nhận; chưa dọn không giao.

**Ca tham chiếu:** [NAV-002](web-test-cases.md#nav-002); [BOOK-001](web-test-cases.md#book-001); [PAY-002](web-test-cases.md#pay-002); [CIN-001](web-test-cases.md#cin-001); [COUT-001](web-test-cases.md#cout-001); [HOUSE-008](web-test-cases.md#house-008)

## E2E-02 — Khách đăng nhập→dịch vụ→snapshot→lịch sử

**Ưu tiên:** P0. **Chuẩn bị:** A có tài khoản; phòng3m; dịch vụ200k x2

1. Đăng nhập A. 2. Tìm và đặt kèm dịch vụ. 3. Xem tổng3.4m và thanh toán test. 4. Mở lịch sử và chi tiết. 5. Manager đổi giá/ngừng dịch vụ. 6. Mở lại booking cũ; tạo booking mới.

**Mong đợi và nhánh lỗi:** Chỉ A xem booking; snapshot cũ không đổi; đặt mới dùng catalog hiện hành. Nhánh lỗi: B nhập mã A bị chặn.

**Ca tham chiếu:** [AUTH-007](web-test-cases.md#auth-007); [ADD-001](web-test-cases.md#add-001); [ADD-007](web-test-cases.md#add-007); [HIST-001](web-test-cases.md#hist-001); [HIST-004](web-test-cases.md#hist-004)

## E2E-03 — Hai khách→giữ phòng cuối→hết hạn

**Ưu tiên:** P0. **Chuẩn bị:** Còn1phòng; hai phiên A/B; Clock tại expiredAt

1. A/B mở cùng kết quả. 2. Cùng tạo pending. 3. Người thắng không trả tiền; thử sửa pending sát hạn. 4. Tới đúng hạn tìm lại trước scheduler. 5. B đặt lại. 6. Gửi tiền cho QR cũ của A.

**Mong đợi và nhánh lỗi:** Chỉ một hold tại một thời điểm; sửa không gia hạn; đúng expiredAt tồn được trả; tiền muộn không hồi sinh booking cũ, đi đối soát hoàn.

**Ca tham chiếu:** [INV-004](web-test-cases.md#inv-004); [INV-013](web-test-cases.md#inv-013); [BOOK-010](web-test-cases.md#book-010); [PAY-006](web-test-cases.md#pay-006); [PAY-013](web-test-cases.md#pay-013)

## E2E-04 — Thanh toán partial→full→callback lặp→dư

**Ưu tiên:** P0. **Chuẩn bị:** Payment test tổng1m; các transaction IDs riêng

1. Chuyển400k. 2. Xem chưa confirmed. 3. Chuyển600k. 4. Replay callback600k. 5. Dùng fixture riêng chuyển dư200k. 6. Mở monitor và refund.

**Mong đợi và nhánh lỗi:** Cộng tiền một lần mỗi giao dịch; đủ mới confirmed; dư thành refund pending. Nhánh lỗi: API key sai/account sai/outgoing không được xác nhận.

**Ca tham chiếu:** [PAY-003](web-test-cases.md#pay-003); [PAY-004](web-test-cases.md#pay-004); [PAY-005](web-test-cases.md#pay-005); [PAY-008](web-test-cases.md#pay-008); [PAY-009](web-test-cases.md#pay-009); [MON-007](web-test-cases.md#mon-007)

## E2E-05 — Hủy booking→duyệt hoàn→dashboard

**Ưu tiên:** P0. **Chuẩn bị:** Ba confirmed đã thanh toán; còn48h,24h,<24h; bank sandbox

1. Xem quote hủy từng mốc. 2. Cung cấp bank và hủy ca được phép. 3. Xem refund pending. 4. Manager đối soát chuyển sandbox rồi xác nhận chứng từ. 5. Xem cash dashboard trước/sau. 6. Thử replay duyệt và admin-only truy cập.

**Mong đợi và nhánh lỗi:** 48h hoàn100%,24h hoàn50%,<24h từ chối; pending chưa trừ cash thực; một lần hoàn. Nhánh partial-pending và nhiều payment cần đối soát riêng.

**Ca tham chiếu:** [CANCEL-002](web-test-cases.md#cancel-002); [CANCEL-003](web-test-cases.md#cancel-003); [CANCEL-004](web-test-cases.md#cancel-004); [CANCEL-005](web-test-cases.md#cancel-005); [REF-002](web-test-cases.md#ref-002); [REF-004](web-test-cases.md#ref-004); [DASH-005](web-test-cases.md#dash-005)

## E2E-06 — Nhận sớm→đến muộn qua ngày

**Ưu tiên:** P0. **Chuẩn bị:** Booking confirmed D và D-1; phòng AVAILABLE; Clock kiểm soát

1. D13:59 nhận không lý do rồi có lý do. 2. Fixture khác nhận đúng 14:00. 3. D05:59:59 nhận booking D-1. 4. Fixture khác thử đúng 06:00. 5. So ngày booking và timestamp.

**Mong đợi và nhánh lỗi:** Nhận sớm cần lý do; đúng 14 không cần; đến muộn trước 06 được, đúng 06 bị chặn; không đổi ngày/giá booking gốc.

**Ca tham chiếu:** [TIME-008](web-test-cases.md#time-008); [TIME-009](web-test-cases.md#time-009); [TIME-010](web-test-cases.md#time-010); [TIME-011](web-test-cases.md#time-011); [CIN-008](web-test-cases.md#cin-008)

## E2E-07 — Gia hạn→trả QR hoặc thu cash

**Ưu tiên:** P0. **Chuẩn bị:** Hai checked-in của A; giá đêm mới400k; còn đủ tồn

1. A gia hạn thêm2đêm. 2. Đối chiếu+800k, giữ phòng/snapshot cũ. 3. Ca1 trả QR khoản thêm rồi checkout. 4. Ca2 để nợ500k, thêm phí150k và thu cash650k lúc checkout. 5. So payment và dashboard.

**Mong đợi và nhánh lỗi:** Status đang ở giữ nguyên; không thu lại tiền cũ; cash và bank không cộng trùng. Nhánh lỗi: ngày không tiến/hết tồn/phòng đã gán người sau đều chặn.

**Ca tham chiếu:** [EXT-002](web-test-cases.md#ext-002); [EXT-004](web-test-cases.md#ext-004); [EXT-005](web-test-cases.md#ext-005); [EXT-006](web-test-cases.md#ext-006); [PAY-016](web-test-cases.md#pay-016); [COUT-002](web-test-cases.md#cout-002); [COUT-010](web-test-cases.md#cout-010)

## E2E-08 — Trả sớm→bù nợ→refund hoặc thu thêm

**Ưu tiên:** P0. **Chuẩn bị:** Ba fixture credit>nợ, =nợ, <nợ; ngày thực tế D

1. Mở trả sớm và đọc quote từng đêm. 2. CaU2m,nợ300k,phí200k nhập bank để refund1.1m. 3. Ca credit800k=nợ800k không thu/hoàn. 4. Ca credit800k,nợ1.2m thu400k. 5. Kiểm phòng DIRTY và trạng thái tiền. 6. Thử ngày hôm qua và submit lặp.

**Mong đợi và nhánh lỗi:** 20% phí/80%credit cho đêm chưa dùng; add-on không nằm trong U; chỉ xử lý ngày hôm nay; một checkout và đúng khoản thu/hoàn.

**Ca tham chiếu:** [EARLY-001](web-test-cases.md#early-001); [EARLY-002](web-test-cases.md#early-002); [EARLY-003](web-test-cases.md#early-003); [EARLY-004](web-test-cases.md#early-004); [EARLY-005](web-test-cases.md#early-005); [EARLY-009](web-test-cases.md#early-009)

## E2E-09 — Khách quá hạn→tồn tương lai→trả muộn→dọn

**Ưu tiên:** P0. **Chuẩn bị:** Checked-in chưa trả sau 12:00; có booking kế tiếp

1. Tìm tồn trước khi checkout. 2. Để qua deadline/nửa đêm. 3. Thử checkout thiếu ghi chú. 4. Ghi lý do/phí nếu có, thu đúng dư nợ. 5. Dọn phòng và tìm lại. 6. Chỉ nhận khách tiếp khi AVAILABLE.

**Mong đợi và nhánh lỗi:** Không tự giải phóng tồn vì ngày đặt đã kết thúc; cần ghi chú; không tự cộng đêm; checkout ra DIRTY, chưa giao ngay.

**Ca tham chiếu:** [TIME-013](web-test-cases.md#time-013); [TIME-014](web-test-cases.md#time-014); [INV-010](web-test-cases.md#inv-010); [COUT-006](web-test-cases.md#cout-006); [HOUSE-008](web-test-cases.md#house-008)

## E2E-10 — Quản lý bảo trì→chi phí→dashboard

**Ưu tiên:** P0. **Chuẩn bị:** Phòng AVAILABLE; managerM1/M2; giá trị cost fixture

1. M1 tạo ticket, kiểm MAINTENANCE và tồn giảm. 2. Bắt đầu xử lý. 3. Ghi cost1m. 4. M1/M2 cùng mở revision và sửa khác nhau. 5. Xử lý409; lưu tổng1.5m. 6. Hoàn tất ticket; kiểm AVAILABLE và dashboard/history.

**Mong đợi và nhánh lỗi:** Một ticket mở; đúng thứ tự trạng thái; revision chống ghi đè; dashboard1.5m không2.5m; lịch sử giữ1m. Admin-only bị chặn route bảo trì.

**Ca tham chiếu:** [MAINT-001](web-test-cases.md#maint-001); [MAINT-004](web-test-cases.md#maint-004); [MAINT-007](web-test-cases.md#maint-007); [MAINT-011](web-test-cases.md#maint-011); [MAINT-012](web-test-cases.md#maint-012); [DASH-006](web-test-cases.md#dash-006)

## E2E-11 — Admin/manager quản trị catalog→giá khách

**Ưu tiên:** P1. **Chuẩn bị:** Hạng/phòng/dịch vụ UAT; ảnh hợp lệ và giả; rule riêng

1. Tạo hạng, upload ảnh, chọn tiện nghi có sẵn. 2. Tạo phòng AVAILABLE thuộc hạng. 3. Thêm dịch vụ và rule/weekend/lịch lễ. 4. Khách tìm và cộng giá từng đêm. 5. Tạo booking. 6. Đổi giá và ngừng bán. 7. Kiểm snapshot cũ; thử xóa tài nguyên có FK.

**Mong đợi và nhánh lỗi:** Giá mới phản ánh catalog/rule; snapshot cũ giữ nguyên; ảnh giả/giá âm/hạng trùng bị chặn; không xóa lịch sử booking.

**Ca tham chiếu:** [RTYPE-001](web-test-cases.md#rtype-001); [RTYPE-007](web-test-cases.md#rtype-007); [ROOM-001](web-test-cases.md#room-001); [ADD-005](web-test-cases.md#add-005); [PRICE-004](web-test-cases.md#price-004); [HOL-004](web-test-cases.md#hol-004); [RTYPE-011](web-test-cases.md#rtype-011)

## E2E-12 — Tài khoản→reset→phân quyền→logout

**Ưu tiên:** P0. **Chuẩn bị:** A/B; mail sandbox; R/H/M/Admin mỗi role riêng

1. Đăng ký A và login. 2. Sửa hồ sơ. 3. Reset mật khẩu qua mail; dùng lại token cũ. 4. Chạy mọi dòng ma trận quyền bằng menu/URL/API. 5. Thử A đọc booking B. 6. Logout và Back/F5; login B cùng browser.

**Mong đợi và nhánh lỗi:** Token reset dùng một lần; quyền backend độc lập UI; không rò cache/PII giữa tài khoản. Không nhầm admin-only được check-in/maintenance/refund.

**Ca tham chiếu:** [AUTH-001](web-test-cases.md#auth-001); [RESET-001](web-test-cases.md#reset-001); [RESET-005](web-test-cases.md#reset-005); [PROFILE-002](web-test-cases.md#profile-002); [SEC-001](web-test-cases.md#sec-001); RBAC-* (chạy toàn bộ nhóm quyền); [UX-010](web-test-cases.md#ux-010)

## E2E-13 — AI→action thật→lỗi dịch vụ

**Ưu tiên:** P1. **Chuẩn bị:** A đăng nhập; RAG/provider test; booking A/B

1. Hỏi policy giờ và hủy; xem nguồn. 2. Nhấn gợi ý đặt với ngày/hạng. 3. Đi tiếp luồng booking có backend recheck. 4. Thử action hủy/payment của A và ID B. 5. Giả lập SSE/provider lỗi rồi retry.

**Mong đợi và nhánh lỗi:** AI không tự thu/hoàn/đặt; action đúng route và vẫn kiểm quyền; lỗi không phát sinh giao dịch; không đọc dữ liệu B.

**Ca tham chiếu:** [AI-002](web-test-cases.md#ai-002); [AI-005](web-test-cases.md#ai-005); [AI-006](web-test-cases.md#ai-006); [AI-007](web-test-cases.md#ai-007); [AI-009](web-test-cases.md#ai-009)

## E2E-14 — Hai phiên và lỗi phản hồi sau mutation

**Ưu tiên:** P0. **Chuẩn bị:** Fixture cuối phòng/cùng phòng nhận/cùng checkout/cùng refund

1. Lần lượt chạy tranh hold, nhận cùng phòng, checkout cùng booking, duyệt cùng refund. 2. Mỗi luồng giả lập web mất phản hồi sau commit. 3. Refresh/tra lại trước retry. 4. So phòng, booking, payment, refund và dashboard.

**Mong đợi và nhánh lỗi:** Mỗi tài nguyên chỉ một kết quả hợp lệ; không double charge/refund/assign; web khôi phục theo backend. Mọi lỗi500 phải có trạng thái cuối đối soát được.

**Ca tham chiếu:** [INV-013](web-test-cases.md#inv-013); [CIN-009](web-test-cases.md#cin-009); [CIN-010](web-test-cases.md#cin-010); [COUT-009](web-test-cases.md#cout-009); [REF-005](web-test-cases.md#ref-005); [CIN-012](web-test-cases.md#cin-012); [SEC-010](web-test-cases.md#sec-010)
