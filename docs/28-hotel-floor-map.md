# 28. Sơ đồ khách sạn theo tầng

Triển khai MVP ngày **11/11/2026**, nhánh `feat/hotel-floor-map`.
Xem [thiết kế ban đầu và hình minh họa](27-hotel-floor-map-proposal.md).

## Sử dụng

Lần đầu bật tính năng, khởi động lại backend để Flyway áp dụng migration
`V202611111000__add_hotel_floor_plans.sql` nếu chưa được áp dụng. Migration chỉ thêm bảng và chỉ mục;
không reset danh mục, sửa phòng, booking hoặc trạng thái vận hành hiện có.
Frontend cần chạy mã mới hoặc build lại nếu đang phục vụ bản đã đóng gói.

| Trang | Người dùng | Công việc |
| --- | --- | --- |
| `/hotel-map` | Công khai | Chọn tầng, xem sơ đồ/danh sách, bấm phòng và mở chi tiết hạng để đặt |
| `/front-desk/room-map` | Lễ tân, quản lý | Xem trạng thái hiện tại, lọc theo hạng/trạng thái/số phòng; lễ tân nhập mã booking để sang bước nhận khách |
| `/front-desk/check-in` | Lễ tân | Tìm phòng khả dụng, chọn “Xem sơ đồ để chọn phòng”, chọn ô rồi xác nhận check-in |
| `/management/floor-plans` | Quản lý | Chọn mẫu, tự xếp đều, kéo thả phòng/khu vực chung rồi công bố bố cục |

Menu khách có “Sơ đồ khách sạn” (mobile: “Sơ đồ”); danh mục và chi tiết hạng có
liên kết tới sơ đồ. Menu nội bộ có “Sơ đồ phòng” và, với quản lý, “Cấu hình sơ đồ tầng”.
ADMIN vẫn chỉ quản trị tài khoản theo chính sách hiện có, không được vào các trang vận hành.

## Cấu hình lần đầu

1. Đăng nhập bằng tài khoản `MANAGER`, vào “Cấu hình sơ đồ tầng”.
2. Chọn tầng trong danh sách hoặc nhập số tầng từ -10 đến 200.
3. Chọn mẫu **“Hai dãy đối diện”**, **“Một dãy”** hoặc **“Dàn đầy tầng · hai dãy cân đối”**, bấm **“Tự xếp đều”**.
   Hệ thống định cỡ ô theo diện tích hạng phòng, xếp cách đều và thêm khu vực chung. Nút dùng được
   cả với tầng đã có bố cục. Mẫu chỉ là bản nháp để chỉnh theo mặt bằng thực tế.
   Tầng 1 không có phòng dùng mẫu **“Sảnh đón khách · quầy lễ tân”**.
4. Kéo phòng/khu vực tới vị trí mong muốn. Phòng có diện tích được giữ kích thước
   theo bố cục đã chọn theo mặc định; bật **“Chỉnh kích thước phòng thủ công”** trong Nâng cao
   để mở chỉnh rộng/cao, kéo góc hoặc Shift + mũi tên cho phòng. Việc chỉnh này
   chỉ thay hình vẽ, không sửa diện tích hạng phòng. Khu vực chung có thể kéo góc dưới phải để
   đổi kích thước. Khi kéo vào phòng khác cùng kích thước, hai phòng đổi chỗ;
   cũng có thể chọn “Đổi chỗ với phòng…” trong bảng công cụ bên cạnh.
5. Dùng các nút “Hành lang”, “Cầu thang”, “Thang máy”, “Khu vực chung” hoặc thêm
   phòng chưa bố trí. Ô mới được đặt vào chỗ trống; mặt bằng tự tăng chiều cao
   nếu cần và vẫn còn trong giới hạn. Dùng “Hoàn tác” / “Làm lại” để thử các cách
   bố trí, hỗ trợ tối đa 30 bước gần nhất.
6. Kiểm tra sơ đồ, rồi bấm **“Lưu và công bố”**. Thao tác thay toàn bộ bố cục
   của tầng trong một transaction. Bản đã công bố giữ nguyên nếu lưu thất bại.

Mặc định không cần nhập X/Y hoặc kích thước lưới. Bảng tọa độ và kích thước mặt
bằng nằm trong mục **“Nâng cao”**. X/Y bắt đầu từ 0, tính bằng ô lưới, không phải
mét; lưới có 8–80 hàng/cột. Với bàn phím, chọn ô rồi dùng mũi tên để di chuyển,
giữ Shift và bấm mũi tên để đổi kích thước những ô không khóa theo diện tích. Trên thiết bị cảm ứng, kéo trực tiếp
ô; vuốt vùng nền trống để cuộn sơ đồ rộng.

**“Hiện lưới căn chỉnh”** bật/tắt các đường căn ở nền trình chỉnh sửa, với đường
đậm mỗi 5 ô. Nó không tự xếp lại phòng và không thay tọa độ hay dữ liệu đã lưu.
Kéo thả luôn bám từng bước ô, kể cả khi ẩn lưới. Lưới chỉ dành cho quản lý,
không xuất hiện trên sơ đồ khách hoặc lễ tân.

Mỗi phòng phải thuộc đúng tầng và chỉ xuất hiện một lần. Các phần tử phải nằm
trong lưới, có kích thước dương và không chồng nhau. Nút “Gỡ” chỉ gỡ khỏi sơ đồ,
không xóa phòng vật lý hoặc thay tồn phòng.

Kéo/thay kích thước vào chỗ chồng ô hoặc vượt mặt bằng sẽ bị từ chối và giữ vị
trí cũ. Lỗi nhập qua bảng Nâng cao được hiển thị ngay và chặn công bố cho tới khi
sửa hợp lệ. Các thao tác kéo, tự xếp và hoàn tác không gọi API ghi dữ liệu;
chỉ nút Lưu và công bố mới lưu bản nháp. Cấu hình đang công bố được giữ nguyên
khi mở trình chỉnh sửa; hệ thống không tự sắp lại một mặt bằng đã lưu.

Phòng chưa được đặt vào sơ đồ xuất hiện trong mục “Chưa bố trí”. Tầng chưa có
bố cục hiển thị danh sách đầy đủ, vẫn hỗ trợ chọn phòng khi check-in. Hệ thống
không tự coi số phòng là tọa độ hoặc suy đoán thang máy/hướng nhìn.

“Tải lại bản đã công bố” bỏ thay đổi đang nhập. Khi hai quản lý lưu từ cùng một
phiên bản, chỉ một người thành công; người còn lại nhận thông báo tải bản mới.

## Khung nhìn chung và kích thước phòng

Màn hình khách, lễ tân và trình cấu hình cùng dùng khung sơ đồ cao **600 px**.
Một đơn vị lưới luôn hiển thị bằng **14 px**, không phụ thuộc số phòng. Bố cục
chuẩn có **80 cột × 40 hàng**, tương ứng mặt bằng **1120 × 560 px**. Khi màn hình
hẹp hoặc mặt bằng lớn hơn khung, sơ đồ cuộn bên trong thay vì co nhỏ phòng.

Diện tích lấy từ trường `areaM2` của API chi tiết hạng phòng, không suy đoán từ
mô tả. Client nạp theo từng hạng cùng nhóm, ảnh, giường, sức chứa, giá cơ bản và
tiện nghi, dùng cache 60 giây; khi sửa hạng phòng hoặc
làm mới dữ liệu sơ đồ, cache được xóa. Phòng thiếu diện tích có viền nét đứt,
thông tin “Chưa có diện tích” trong chi tiết và kích thước tạm để vẫn thao tác được.

Mẫu tự xếp dùng mốc **4 ô lưới diện tích cho 1 m²**, làm tròn lên hình gần vuông.
Khi diện tích tăng, cả chiều rộng và chiều cao đều không giảm; tránh trường hợp
38 m² thành hình dài thấp hơn 36 m². Với các hạng hiện tại, sai lệch làm tròn
diện tích hình vẽ dưới 3%. Chiều dài, chiều rộng và vị trí là minh họa bố trí,
chưa phải số đo kiến trúc thực tế. Sơ đồ không hiển thị nhãn m² trên ô; diện tích
vẫn có trong chi tiết phòng. Mặc định khóa chỉnh kích thước phòng, có thể mở
chỉnh thủ công trong Nâng cao để khớp hình dạng mặt bằng. Kích thước là số ô lưới,
không phải mét và không thay diện tích trong danh mục hạng. Nếu đổi diện tích
hạng sau này, tạo lại mẫu và công bố để cập nhật các ô đã lưu.

Quy tắc trên áp dụng cho mẫu một/hai dãy theo diện tích. Mẫu **“Dàn đầy tầng”**
dành cho số phòng chẵn từ 2 đến 10: chia đều phòng về hai phía hành lang, kéo dài ô
ra bề ngang mặt bằng. Mẫu này minh họa bố cục phủ đầy khung, không dùng diện
tích hình vẽ để so sánh m² giữa các hạng/tầng. Diện tích thật trong danh mục và
chi tiết phòng được giữ nguyên. Mẫu không tự tạo phòng để đủ số lượng; quản lý
cần có số phòng hợp lệ trong danh mục trước khi chọn. Tầng có 2/4/6/8/10 phòng
mặc định chọn mẫu này trong trình cấu hình, nhưng chỉ thay bố cục khi bấm Tự
xếp đều và Lưu và công bố.

Đã cấu hình và công bố toàn bộ database local ngày 11/11/2026:

| Tầng | Số phòng | Diện tích mỗi phòng | Kích thước ô lưới | Phiên bản |
| --- | --- | --- | --- | --- |
| 1 | 0, tầng sảnh | — | Khu vực chung | 1 |
| 2 | 6 | 22 m² | 21/22/21 × 12 theo mỗi dãy, dàn đầy | 8 |
| 3 | 10 | 30 m² | 12 × 12, dàn đầy | 4 |
| 4 | 10 | 36 m² | 12 × 12 | 2 |
| 5 | 4 | 38 m² | 33 × 12, dàn đầy | 4 |
| 6 | 4 | 48 m² | 33 × 12, dàn đầy | 4 |
| 7 | 2 | 60 m² | 68 × 12, dàn đầy | 4 |

Cả 7 tầng cùng mặt bằng 80 × 40. Cầu thang ở `(2,18)`, kích thước 6 × 4;
hành lang ở `(8,18)`, kích thước 64 × 4; thang máy ở `(72,18)`, kích thước
6 × 4. Tầng 2 có ba phòng mỗi dãy ở X=6, 29, 53, rộng lần lượt 21, 22, 21;
làm tròn theo ô lưới để giữ hai lề và mọi khoảng hở bằng nhau. Tầng 3 giống
tầng 4: năm phòng mỗi dãy ở X=6, 20, 34, 48, 62, rộng 12. Tầng 5/6 có hai ô ở mỗi phía hành
lang: X=6 và 41, rộng 33. Tầng 7 có một ô ở mỗi phía: X=6, rộng 68. Các ô dàn
đầy cao 12, dãy trên Y=4 và dãy dưới Y=24. Các tầng 2–7 có cùng lề trái/phải 6 ô,
lề trên/dưới 4 ô, khoảng cách ngang giữa phòng 2 ô và khoảng cách tới hành lang
2 ô như tầng 4. Giữ nguyên số phòng trên từng tầng.
Mẫu có thể thêm dãy nếu nhiều phòng hơn, không giảm tỷ lệ để ép vừa khung;
nếu vượt giới hạn mặt bằng thì báo lỗi để quản lý bố trí lại.

Bản sao cấu hình trước nằm tại `.local-backups/all-floor-layouts-1794396088954/`;
bản sao và ảnh của lần bổ sung sảnh, điều chỉnh hình dáng mới nhất nằm tại
`.local-backups/lobby-uniform-layouts-1794398453720/` (được bỏ qua bởi Git).
Lượt dàn đầy mới nhất đã bổ sung phòng **504**, cùng hạng Triple Mixed và
trạng thái AVAILABLE, nâng tổng số phòng từ 35 lên **36**. Chỉ công bố lại tầng
5–7; toàn bộ 35 phòng cũ, thông số hạng và bố cục tầng 1–4 giữ nguyên. Sao lưu
trước/sau và ảnh từng tầng nằm tại `.local-backups/fill-floor-layouts-1794401149589/`.
Script bổ sung phòng có thể chạy lại an toàn: [add-triple-room-504.sql](../scripts/add-triple-room-504.sql).

Lượt căn khoảng cách tiếp theo chỉ cập nhật bố cục tầng 5–7 (phiên bản 4);
36 phòng, hạng và tầng 1–4 giữ nguyên. Bản sao trước/sau và ảnh đối chiếu với
tầng 4 nằm tại `.local-backups/floor-spacing-1794401573570/`.

Lượt tiếp theo chỉ dàn lại tầng 2 và 3 (phiên bản 8/4), giữ nguyên 36 phòng,
thông số hạng và bố cục các tầng khác. Bản sao trước/sau và ảnh đối chiếu nằm
tại `.local-backups/floor23-spacing-1794412071724/`.
Mẫu dàn đầy hỗ trợ số phòng chẵn từ 2 đến 10; chia ô nguyên với độ lệch rộng tối
đa 1 ô khi cần làm tròn. **61 test liên quan**, build, ESLint, Prettier và kiểm
tra Chrome desktop/mobile đạt; đối chiếu database xác nhận chỉ bố cục tầng 2–3
được công bố lại, 36 phòng và các tầng khác giữ nguyên.

Tầng 1 là khu vực chung, không tạo phòng lưu trú hoặc tồn phòng giả. Mẫu gồm
quầy lễ tân để nhận/trả phòng và hỗ trợ đặt phòng, sảnh đón khách, sảnh chờ,
gửi hành lý, hỗ trợ khách, WC chung, lối vào chính và các khu vực lưu thông.
Trang khách/lễ tân hiển thị sơ đồ và danh sách khu vực, không báo lỗi “chưa có
phòng”, không có trạng thái phòng hoặc lựa chọn gán phòng ở tầng này. Mẫu có
thể kéo sửa cho đúng vị trí thực tế của khách sạn.

## Đường bao và khu vực chung

Sơ đồ khách, lễ tân, chọn phòng khi check-in và trình cấu hình dùng chung lớp
minh họa mặt bằng: đường bao ngoài, tường phòng và biểu tượng
hành lang/cầu thang/thang máy/khu vực chung. Khung và tỷ lệ phòng đã cấu hình
được giữ nguyên, gồm cả tầng sảnh. Không hiện nhãn m² trên ô.

Đã bỏ ký hiệu cửa phòng minh họa, đoạn nối từ cửa ra hành lang và chú thích cửa
theo yêu cầu. Khi kéo hoặc đổi kích thước ô trong trình chỉnh sửa, tường phòng
cập nhật theo bản nháp. Bố cục và kích thước phòng đã lưu được giữ nguyên.

Phần mặt bằng ngoài các ô đã cấu hình dùng nền trơn. Đã bỏ các vùng
“Khu vực chưa mô tả”, nền gạch chéo và mục chú thích tương ứng theo yêu cầu.
Quản lý vẫn có thể thêm khu vực chung đã biết bằng công cụ hiện có.

Các ký hiệu là phần trình bày, không tạo phòng, không thay tồn phòng và không
ghi thêm phần tử vào database. Các lớp nền/ký hiệu không chặn thao tác
bấm phòng, kéo thả hoặc cuộn nền. Lưới căn chỉnh vẫn bật/tắt độc lập. Bên dưới
sơ đồ có chú thích đường bao.

## Bảng màu Coastal Hotel

Trang khách, sơ đồ lễ tân, chọn phòng khi check-in và trình cấu hình dùng chung:

| Vai trò | Màu |
| --- | --- |
| Thương hiệu, số phòng và biểu tượng | Navy `#17384D` |
| Viền phòng được chọn / focus | Xanh ngọc `#0F766E` |
| Nền sơ đồ | Trắng kem `#F7F8F5` |
| Hành lang, khu vực lưu thông | Xám xanh `#E6EDF1` |
| Điểm nhấn, Deluxe/Suite, quầy lễ tân | Champagne `#D5AE73` |
| Chữ | Xám đậm `#334155` |

Màu nền phòng cố định theo `category.code` từ API chi tiết hạng, không theo thứ
tự phòng hoặc vị trí tầng: Standard xanh lam `#DCEAF5`, Superior xanh bạc hà
`#DEF1EB`, Deluxe kem đào `#F9E8D2`, Triple hồng `#F8E3DA`, Family tím
`#ECE4F5`, Suite vàng nhạt `#F6E7B9`. Hạng chưa có nhóm/nhóm tùy chỉnh dùng màu
trung tính, không đoán từ tên hoặc UUID. Queen/Twin cùng nhóm dùng cùng màu.

Trạng thái vận hành hiển thị bằng nhãn/chấm riêng, không đổi nền hạng phòng:
sẵn sàng xanh lá, đang có khách đỏ, chờ dọn vàng, đang dọn xanh lam, bảo trì tím,
ngừng phục vụ xám. Chọn phòng dùng viền xanh ngọc dày hơn, giữ nền pastel.
Sơ đồ công khai không lộ trạng thái vận hành; nhãn còn phòng/sắp hết theo ngày
chỉ có ý nghĩa khi đã chọn kỳ lưu trú và có dữ liệu tồn tương ứng, nên không
tự suy ra từ trạng thái sẵn sàng của phòng vật lý.

Ô có góc bo nhẹ, ký hiệu giường, số và tên hạng rõ ràng. Tầng sảnh giữ hình đồ
đạc riêng, phối lại màu theo bảng chung. Khi bấm phòng, bảng chi tiết dùng dữ
liệu hạng để hiện ảnh, giường, sức chứa, diện tích, giá cơ bản/đêm và tiện nghi;
giá theo kỳ lưu trú vẫn được kiểm tra khi tìm/đặt phòng. Không đổi bố cục đã
công bố, số lượng phòng, mã Java hoặc database trong lần chỉnh màu này.

Kiểm tra bản Coastal ngày 11/11/2026: **410 test frontend / 68 file đạt**;
TypeScript/Vite build, ESLint và Prettier đạt. Chrome xác nhận đủ 7 tầng / 36
phòng với 6 màu hạng cố định, nền/hành lang/biểu tượng đúng mã màu, viền chọn
xanh ngọc không thay nền và 6 nhãn trạng thái độc lập. Trang khách dùng API local
thật; màn lễ tân và cấu hình dùng API giả lập để kiểm tra đủ trạng thái, chọn,
chỉnh sửa và hoàn tác. Bảng chi tiết có ảnh/giường/sức chứa/giá/tiện nghi; mobile
giữ tỷ lệ và cuộn trong khung. Đối chiếu API trước/sau xác nhận bố cục giữ nguyên.

## Nghiệp vụ nhận phòng

- Khách vẫn đặt theo **hạng phòng**, không đặt chắc chắn một số phòng cụ thể.
  Sơ đồ công khai không cung cấp trạng thái ở/dọn/bảo trì, lịch booking, UUID phòng
  vật lý, thông tin khách hoặc ghi chú nội bộ. Ảnh và cấu hình giường là của hạng.
- Sơ đồ lễ tân thể hiện **trạng thái hiện tại**. `AVAILABLE` không đồng nghĩa còn
  bán được cho mọi kỳ lưu trú; tồn theo ngày vẫn do bộ tính hiện có quyết định.
- Tập phòng được nhận lấy từ API `available-rooms` của booking: đúng hạng, đang
  sẵn sàng và không có lịch gán xung đột. Ô không đủ điều kiện vẫn hiển thị nhưng
  không chọn được. Khi API hiện tại không trả lý do cụ thể, nhãn dùng “Khác hạng
  hoặc trùng lịch”, không suy đoán một nguyên nhân chắc chắn.
- Chọn ô chỉ cập nhật form. **Xác nhận check-in** mới gọi API cũ để khóa booking
  và phòng, kiểm tra lại rồi gán phòng. Giữ nguyên xác nhận booking, lý do nhận
  sớm, cửa sổ đến muộn, giờ thực tế và lịch sử.
- Sơ đồ chọn phòng chỉ mở các tầng có phòng thuộc kết quả `available-rooms`
  của booking đang tìm, mặc định chọn tầng đầu tiên trong tập này. Nhãn tab
  ghi số phòng có thể nhận, thông báo phía trên ghi rõ mã booking và hạng của
  các phòng khả dụng. Các phòng khác trong cùng mặt bằng vẫn hiển thị mờ để
  giữ ngữ cảnh vị trí, kèm lý do và không chọn được. Danh sách này quyết định
  quyền chọn phòng, không suy ra chỉ từ màu hoặc trạng thái AVAILABLE.
- Đổi booking xóa lựa chọn/tầng cũ. Chọn phòng từ danh sách radio chuyển sơ đồ
  đến tầng chứa phòng đó. Nếu không còn phòng thì không mở các tầng không
  liên quan; nếu phòng khả dụng chưa có trong sơ đồ thì vẫn chọn bằng danh sách.
- Có thể chọn qua sơ đồ hoặc danh sách radio hiện có. Khi đổi booking, phòng được
  chọn bị xóa. Khi phòng không còn trong kết quả mới hoặc lệnh nhận thất bại,
  bỏ lựa chọn và làm mới dữ liệu.
- Mất mạng hoặc truy vấn đang lỗi sẽ chặn xác nhận từ sơ đồ. Khi thành công, màn
  hình hiển thị số phòng đã gán; thao tác trùng phòng vẫn do backend từ chối `409`.
- Trả phòng vẫn chuyển `DIRTY`, dọn theo `DIRTY → CLEANING → AVAILABLE`. Sơ đồ
  không có nút đổi trạng thái để bỏ qua quy trình vận hành.

Sơ đồ và phòng khả dụng đọc lại mỗi 15 giây khi tab đang hoạt động, khi focus
hoặc reconnect. Các thao tác nhận/trả, dọn, bảo trì và quản lý phòng làm mới cache
liên quan trên client. Máy khác nhận thay đổi ở lần polling tiếp theo. Trong chế
độ xem trạng thái, bảng thông tin phòng đang mở cũng cập nhật theo kết quả mới.

## API

Tiền tố `/api/v1`; response bọc bởi `ApiResponse` như các API hiện có.

| Phương thức và đường dẫn | Quyền | Dữ liệu |
| --- | --- | --- |
| `GET /hotel-map` | Công khai | `{ floors }`, phòng công khai dùng `roomNumber` làm key, liên kết hạng và `bookable` theo trạng thái kinh doanh của hạng |
| `GET /front-desk/room-map?floor=3` | `RECEPTIONIST`, `MANAGER` | `{ floors, serverNow }`; bỏ `floor` để lấy mọi tầng; phòng có UUID, hạng, trạng thái hiện tại |
| `GET /management/floor-plans/{floorNumber}` | `MANAGER` | Một tầng, version, bố cục và toàn bộ phòng, kể cả chưa bố trí; tầng mới có version 0 |
| `PUT /management/floor-plans/{floorNumber}` | `MANAGER` | Lưu và công bố, trả tầng mới với version tăng 1 |

Ví dụ body PUT (UUID là phòng đã có trên đúng tầng):

```json
{
  "name": "Tầng 3",
  "gridColumns": 24,
  "gridRows": 16,
  "expectedVersion": 0,
  "elements": [
    { "kind": "ROOM", "roomId": "<UUID phòng>", "label": "", "x": 0, "y": 0, "width": 4, "height": 3 },
    { "kind": "CORRIDOR", "roomId": null, "label": "Hành lang", "x": 0, "y": 4, "width": 24, "height": 2 }
  ]
}
```

Loại phần tử: `ROOM`, `CORRIDOR`, `ELEVATOR`, `STAIRS`, `COMMON_AREA`.
Tối đa 400 phần tử/tầng, nhãn/tên tối đa 80 ký tự. DTO công khai và nội bộ
riêng biệt; hai truy vấn GET nội bộ đặt `Cache-Control: no-store`.

| Mã lỗi | HTTP | Cách xử lý |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Sửa tọa độ/kích thước, phòng trùng, sai tầng hoặc dữ liệu không hợp lệ |
| `FLOOR_PLAN_VERSION_CONFLICT` | 409 | Tải lại bố cục mới trước khi lưu |
| `ROOM_LAYOUT_CONFLICT` | 409 | Gỡ phòng khỏi sơ đồ trước khi đổi tầng hoặc xóa phòng vật lý |

Đổi số phòng vẫn được phản ánh tự động vì bố cục nội bộ tham chiếu bằng UUID.
Các điều kiện xóa/sửa khác của quản lý phòng vẫn áp dụng. Khởi tạo demo local
không thay danh mục nếu đã có bố cục do quản lý cấu hình. Script reset chủ động
đã thêm hai bảng sơ đồ vào danh sách xóa và yêu cầu migration 202611111000;
không chạy script reset để bật tính năng.

## Phạm vi và kiểm tra

Lỗi chọn tầng khi nhận phòng, kiểm tra ngày 11/11/2026: booking
`BK20261111001251` có hạng `DELUXE_TWIN` và các phòng 406–410 ở tầng 4;
database và bộ lọc backend đúng, nhưng sơ đồ trước đây mở toàn bộ tầng rồi
mặc định tầng 2. Đã sửa tập tầng và tab đang mở theo phòng khả dụng của booking,
thêm thông tin ngữ cảnh và làm mờ ô bị chặn. **74 test frontend liên quan và
11 test backend CheckInServiceImplTest đạt**, gồm mở đúng tầng Deluxe, đổi sang
Family, bỏ lựa chọn cũ, trường hợp hết phòng, đồng bộ radio/tab và chặn sai hạng.
Chrome dùng dữ liệu phòng đã đối chiếu database với API giả lập để kiểm tra
desktop/mobile, không gửi check-in thật. Không sửa booking hoặc database local.

Bản này chưa có phân phòng trước, đổi phòng đang ở, giữ số phòng cho
khách hoặc cam kết phòng liền kề. Đơn nhiều phòng tiếp tục nhận từng booking con.

Các kiểm tra mới bao phủ lưu/đọc PostgreSQL thật, phiên bản đồng thời, ràng buộc
bố cục, chống lộ dữ liệu, phân quyền, gỡ phòng, cập nhật chi tiết sau polling và
chọn ô rồi xác nhận nhận khách. Kiểm tra trình duyệt dùng API giả lập để xem giao
diện desktop/mobile và luồng thao tác; không thay thế kiểm thử API với database.

Kết quả ngày 11/11/2026:

- Backend `clean verify`: 921 ca, **920 đạt, 1 bỏ qua**, không lỗi. Ca bỏ qua là
  `ReviewBrowserIntegrationTest` có sẵn, phụ thuộc cấu hình chạy trình duyệt riêng.
  Các test sơ đồ và test PostgreSQL đã chạy, không bị bỏ qua.
- Frontend: **349 test đạt**; build TypeScript/Vite, ESLint và Prettier đạt. Sau khi
  chỉnh fixture TypeScript của test check-in, 9 test liên quan được chạy lại và đạt.
- Chrome: trang khách desktop/mobile, công bố bố cục, trạng thái lễ tân, khóa phòng
  không hợp lệ, chọn phòng chưa gọi API nhận, chặn khi mất mạng và nhận sau xác nhận
  đều đạt với API giả lập. Kiểm tra lại viền focus SVG sau khi sửa hiển thị.
- Rà soát cuối: trang cấu hình chờ danh sách tầng trước khi lấy bố cục mặc định;
  không gọi thừa tầng 1. Các trang mới có giao diện chờ tải khi mở trực tiếp.
  Chạy lại 9 test liên quan, build/lint/format và Chrome không có lỗi console.
- Không reset hoặc chạy migration lên database local của người dùng trong lượt này.
  Migration đã được kiểm tra trên PostgreSQL container riêng; backend chính cần
  khởi động lại để áp dụng schema và mã Java mới.

Cập nhật màn hình cấu hình trực quan cùng ngày:

- Thay bảng tọa độ mặc định bằng chọn mẫu, tự xếp đều, kéo thả, đổi kích thước,
  đổi chỗ và hoàn tác/làm lại. Bảng tọa độ vẫn dùng được trong mục Nâng cao.
- **367 test frontend đạt**, sau các chỉnh cuối chạy lại **27 test liên quan đạt**;
  TypeScript/Vite build, ESLint và Prettier đạt.
- Chrome kiểm tra chuột và cảm ứng: kéo/đổi chỗ, thay kích thước, trả ô về vị trí
  cũ khi thả vào chỗ chồng, cuộn nền trên mobile, đổi mẫu trên sơ đồ có sẵn, hoàn
  tác, lưu rõ ràng và lỗi phiên bản. Kiểm tra ghi dữ liệu dùng API giả lập.
- Lượt cập nhật này chỉ thay frontend; không thêm migration hoặc ghi lại bố cục
  tầng 2 đang công bố. Frontend dev cập nhật qua HMR/F5; không cần khởi động lại
  backend chỉ để dùng giao diện mới.

Cập nhật tỷ lệ diện tích và cấu hình tất cả tầng cùng ngày:

- **382 test frontend đạt**; TypeScript/Vite build, ESLint và Prettier đạt.
- Chrome kiểm tra cả 6 tầng / 35 phòng qua trang khách với API local thật:
  tỷ lệ diện tích chính xác, cùng khung 600 px, mặt bằng 1120 × 560 px và cùng
  vị trí/kích thước khu vực chung. Desktop và mobile cuộn trong khung, không
  co phòng hoặc làm tràn chiều ngang trang.
- Trang lễ tân và trình cấu hình được kiểm tra bằng API giả lập, bao gồm khóa
  kích thước theo m² và thao tác chỉnh sửa không tự ghi dữ liệu.
- Đã công bố bố cục local cho tầng 2–7 bằng một transaction có kiểm tra phiên
  bản, đủ phòng, tỷ lệ diện tích, giới hạn và chồng ô. Kiểm tra lại API sau lưu
  đạt. Lượt cập nhật này không thay mã Java hay migration; không cần khởi động
  lại backend đang chạy.

Cập nhật sảnh tầng 1 và khả năng chỉnh sửa cùng ngày:

- Bỏ nhãn m² trên ô sơ đồ khách/lễ tân và trình chỉnh sửa; giữ diện tích trong
  chi tiết phòng và nguồn dữ liệu định cỡ.
- Đổi thuật toán sang hình gần vuông, tăng diện tích không giảm chiều nào.
  Phòng 38 m² có ô 13 × 12, lớn hơn ô 12 × 12 của phòng 36 m²; các tầng còn
  lại giữ cùng khung/tỷ lệ và cùng khu vực lưu thông.
- Thêm mẫu sảnh và công bố tầng 1 với 10 khu vực. Không tạo phòng lưu trú;
  tầng chỉ có khu vực chung được loại khỏi các tab chọn phòng khi check-in.
- Lưới căn chỉnh dùng đường rõ với vạch đậm mỗi 5 ô, bật/tắt không thay tọa độ.
  Chỉnh kích thước phòng thủ công là tùy chọn rõ ràng trong Nâng cao, có hỗ trợ
  kéo góc, phím tắt, hoàn tác và lưu kích thước đã chỉnh.
- **388 test frontend đạt**; build, lint và kiểm tra định dạng đạt. Sau chỉnh
  bộ lọc tầng trong luồng check-in, chạy lại các test sơ đồ và check-in liên quan.
- Chrome đạt với 7 tầng / 35 phòng trên API local thật, bao gồm sảnh, ô 38/36,
  ẩn nhãn diện tích, đồng bộ khung/khu vực chung và cuộn trên mobile. Trang
  quản lý/lễ tân dùng API giả lập để kiểm tra bật/tắt lưới có khác biệt hình ảnh,
  mở chỉnh kích thước thủ công, hoàn tác và không ghi dữ liệu khi chưa công bố.
- Bố cục local được cập nhật trong transaction có sao lưu, kiểm tra phiên bản,
  đủ phòng, hình dáng, giới hạn/chồng ô và đối chiếu dữ liệu phòng/hạng giữ nguyên.

Cập nhật hoàn thiện hình thức mặt bằng cùng ngày:

- Giữ 7 tầng / 35 phòng và toàn bộ hình học bố cục đang công bố; bổ sung lớp
  minh họa đường bao, tường/cửa, biểu tượng khu vực chung và nền chưa mô tả.
- **62 test liên quan đạt**, gồm tính hướng cửa, chặn lối nối qua ô khác, nhãn
  không chồng vùng đã cấu hình, kích thước/tồn phòng giữ nguyên, thao tác chọn
  phòng và chỉnh sửa/check-in. TypeScript/Vite build, ESLint và Prettier đạt.
- Kiểm tra Chrome với API local thật cho các tầng; các thao tác quản lý và lễ
  tân dùng API giả lập. Kiểm tra thêm bấm vào vị trí cửa vẫn mở chi tiết phòng,
  kéo nền trang trí vẫn cuộn, lưới, sửa kích thước/hoàn tác và khung trên mobile.
- Không thay mã Java, migration hoặc database local trong lượt cập nhật này.

Cập nhật dàn đầy tầng ít phòng cùng ngày:

- Bổ sung phòng 504 vào Triple Mixed ở tầng 5, tổng 36 phòng. Tầng 5 và 6 chia
  thành 4 ô bằng nhau; tầng 7 có 2 ô kéo dài hết bề ngang. Cả 7 tầng cùng khung,
  hành lang/cầu thang/thang máy và các ký hiệu được giữ đồng bộ. Diện tích thật
  của hạng không đổi; trang khách ghi rõ sơ đồ minh họa bố trí.
- Lưu phòng mới và bố cục tầng 5–7 trong một transaction có kiểm tra phiên bản,
  đủ phòng, giới hạn và chồng ô; đối chiếu 35 phòng cũ và hạng giữ nguyên.
  Chạy lại script bổ sung 504 không đổi dữ liệu hoặc phiên bản bố cục.
- **70 test frontend liên quan đạt**; TypeScript/Vite build và ESLint đạt.
  Chrome xác minh 7 tầng / 36 phòng, chia đều 4/2 ô theo hàng, ẩn nhãn m² và
  phần chưa mô tả, giữ khung/khu vực chung, chỉnh thủ công/hoàn tác và mobile.
- API tìm phòng thực tế ngày 20–21/11/2026 trả Triple Mixed có 4 phòng còn bán
  và 4 phòng sẵn sàng. Không tạo booking hoặc thanh toán trong kiểm tra.

```sh
# Trong hotel-booking/, cần Docker cho các test PostgreSQL
./mvnw -o clean verify

# Trong client/
npm run test:run
npm run build
npm run lint
npm run format:check
```
